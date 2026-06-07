'use strict';

require('dotenv').config();
const { WebSocketServer } = require('ws');
const { URL } = require('url');

const PORT = process.env.PORT || 8080;

const wss = new WebSocketServer({ port: PORT });

// Map of clientId -> WebSocket for kiosk clients
const clients = new Map();

// The single connected operator WebSocket (or null)
let operator = null;

// Queue of clientIds waiting for the operator
const callQueue = [];

console.log(`[server] Signaling server started on port ${PORT}`);

wss.on('connection', (ws, req) => {
  const urlObj = new URL(req.url, `ws://localhost:${PORT}`);
  const role = urlObj.searchParams.get('role');
  const clientId = urlObj.searchParams.get('id');

  if (role === 'operator') {
    handleOperatorConnect(ws);
  } else if (role === 'client' && clientId) {
    handleClientConnect(ws, clientId);
  } else {
    console.warn('[server] Unknown role or missing id — closing connection');
    ws.close(1008, 'Missing role or id');
    return;
  }

  ws.on('message', (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      console.error('[server] Invalid JSON:', data.toString());
      return;
    }
    routeMessage(ws, msg);
  });

  ws.on('close', () => {
    handleDisconnect(ws, role, clientId);
  });

  ws.on('error', (err) => {
    console.error(`[server] WebSocket error (role=${role}, id=${clientId}):`, err.message);
  });
});

// ---------------------------------------------------------------------------
// Connection handlers
// ---------------------------------------------------------------------------

function handleOperatorConnect(ws) {
  if (operator && operator.readyState < 2) {
    console.warn('[server] Operator already connected — replacing');
    operator.close(1001, 'Replaced by new operator connection');
  }
  operator = ws;
  ws._role = 'operator';
  console.log('[server] Operator connected');

  // Drain the queue: notify operator about first waiting client
  processQueue();
}

function handleClientConnect(ws, clientId) {
  if (clients.has(clientId)) {
    const existing = clients.get(clientId);
    existing.close(1001, 'Replaced by new connection with same id');
  }
  clients.set(clientId, ws);
  ws._role = 'client';
  ws._clientId = clientId;
  console.log(`[server] Client connected: ${clientId}`);
}

// ---------------------------------------------------------------------------
// Disconnect handler
// ---------------------------------------------------------------------------

function handleDisconnect(ws, role, clientId) {
  if (role === 'operator') {
    console.log('[server] Operator disconnected');
    operator = null;
    // TODO: notify all queued/active clients that operator is unavailable
  } else if (role === 'client' && clientId) {
    console.log(`[server] Client disconnected: ${clientId}`);
    clients.delete(clientId);

    // Remove from queue if present
    const queueIdx = callQueue.indexOf(clientId);
    if (queueIdx !== -1) {
      callQueue.splice(queueIdx, 1);
      // Update queue positions for remaining clients
      sendQueuePositions();
    }
  }
}

// ---------------------------------------------------------------------------
// Message router
// ---------------------------------------------------------------------------

function routeMessage(ws, msg) {
  const { type } = msg;

  switch (type) {
    case 'call':
      handleCall(msg);
      break;

    case 'accept':
      handleAccept(msg);
      break;

    case 'reject':
      handleReject(msg);
      break;

    case 'offer':
    case 'answer':
    case 'ice_candidate':
      handleWebRTCSignal(ws, msg);
      break;

    default:
      console.warn(`[server] Unknown message type: ${type}`);
  }
}

// ---------------------------------------------------------------------------
// Signaling logic
// ---------------------------------------------------------------------------

/**
 * Client requests a call.
 * If operator is free (not in a call), forward incoming_call immediately.
 * Otherwise, add to queue and send back queued position.
 */
function handleCall(msg) {
  const { clientId } = msg;
  if (!clientId) {
    console.warn('[server] call message missing clientId');
    return;
  }

  console.log(`[server] Call request from: ${clientId}`);

  if (operator && operator.readyState === 1 /* OPEN */) {
    // TODO: track whether operator is currently in a call and queue if busy
    sendToOperator({ type: 'incoming_call', clientId });
  } else {
    // No operator connected or operator is busy — queue the client
    if (!callQueue.includes(clientId)) {
      callQueue.push(clientId);
    }
    const position = callQueue.indexOf(clientId) + 1;
    sendToClient(clientId, { type: 'queued', position });
    console.log(`[server] Client ${clientId} queued at position ${position}`);
  }
}

/**
 * Operator accepts a call for the given clientId.
 */
function handleAccept(msg) {
  const { clientId } = msg;
  console.log(`[server] Operator accepted call from: ${clientId}`);

  // Remove from queue if present
  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) callQueue.splice(idx, 1);

  sendToClient(clientId, { type: 'accept', clientId });
  // TODO: mark operator as busy
}

/**
 * Operator rejects a call for the given clientId.
 */
function handleReject(msg) {
  const { clientId } = msg;
  console.log(`[server] Operator rejected call from: ${clientId}`);

  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) callQueue.splice(idx, 1);

  sendToClient(clientId, { type: 'reject', clientId });
  sendQueuePositions();
}

/**
 * Route WebRTC signaling messages (offer/answer/ice_candidate) by target field.
 * target === 'operator' → forward to operator
 * target === 'client'   → forward to the client identified by msg.clientId
 */
function handleWebRTCSignal(ws, msg) {
  const { target, clientId } = msg;

  if (target === 'operator') {
    sendToOperator(msg);
  } else if (target === 'client' && clientId) {
    sendToClient(clientId, msg);
  } else {
    console.warn('[server] WebRTC signal missing valid target:', msg);
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function sendToOperator(payload) {
  if (operator && operator.readyState === 1) {
    operator.send(JSON.stringify(payload));
  } else {
    console.warn('[server] Cannot send to operator — not connected');
  }
}

function sendToClient(clientId, payload) {
  const ws = clients.get(clientId);
  if (ws && ws.readyState === 1) {
    ws.send(JSON.stringify(payload));
  } else {
    console.warn(`[server] Cannot send to client ${clientId} — not connected`);
  }
}

function sendQueuePositions() {
  callQueue.forEach((clientId, index) => {
    sendToClient(clientId, { type: 'queued', position: index + 1 });
  });
}

function processQueue() {
  if (callQueue.length > 0 && operator && operator.readyState === 1) {
    const nextClientId = callQueue[0];
    sendToOperator({ type: 'incoming_call', clientId: nextClientId });
  }
}
