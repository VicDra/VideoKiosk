'use strict';

require('dotenv').config();
const { WebSocketServer } = require('ws');
const { URL }             = require('url');
const http                = require('http');
const fs                  = require('fs');
const path                = require('path');
const winston             = require('winston');

// ---------------------------------------------------------------------------
// Logger
// ---------------------------------------------------------------------------

const LOG_FILE = path.join(__dirname, '..', 'logs', 'signaling-server.log');

const logger = winston.createLogger({
  level: 'debug',
  format: winston.format.combine(
    winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss.SSS' }),
    winston.format.printf(({ timestamp, level, message }) =>
      `${timestamp} [${level.toUpperCase().padEnd(5)}] ${message}`)
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({
      filename: LOG_FILE,
      maxsize: 5 * 1024 * 1024,
      maxFiles: 3,
      tailable: true
    })
  ]
});

// ---------------------------------------------------------------------------
// HTTP server — serves the web operator UI
// ---------------------------------------------------------------------------

const PORT    = process.env.PORT || 8080;
const PUBLIC  = path.join(__dirname, 'public');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'application/javascript; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.ico':  'image/x-icon',
};

const httpServer = http.createServer((req, res) => {
  // Strip query string
  const urlPath = req.url.split('?')[0];

  // ── /status — machine-readable connection state (used by CDP test) ──────
  if (urlPath === '/status') {
    const payload = JSON.stringify({
      operator: operator && operator.readyState === 1 ? 'connected' : 'disconnected',
      clients:  Array.from(clients.keys()),
      activeCall: activeCallClientId || null,
    });
    res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(payload);
    return;
  }

  // Redirect bare / to operator UI
  let filePath;
  if (urlPath === '/' || urlPath === '') {
    filePath = path.join(PUBLIC, 'operator.html');
  } else {
    filePath = path.join(PUBLIC, urlPath.replace(/^\//, ''));
  }

  // Prevent directory traversal
  if (!filePath.startsWith(PUBLIC)) {
    res.writeHead(403); res.end('Forbidden'); return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not found');
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, {
      'Content-Type':  MIME[ext] || 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    res.end(data);
  });
});

// ---------------------------------------------------------------------------
// WebSocket server — attached to the same HTTP port
// ---------------------------------------------------------------------------

const wss = new WebSocketServer({ server: httpServer });

// Map of clientId -> WebSocket for kiosk clients
const clients  = new Map();
let   operator = null;
const callQueue = [];
// Clients for which incoming_call was already forwarded to operator (pending answer)
const pendingCalls = new Set();
// Client currently in an active (accepted) call with the operator
let activeCallClientId = null;

httpServer.listen(PORT, () => {
  logger.info(`Signaling server started on port ${PORT}`);
  logger.info(`Operator web UI: http://localhost:${PORT}/`);
  logger.info(`Log file: ${LOG_FILE}`);
});

wss.on('connection', (ws, req) => {
  const urlObj   = new URL(req.url, `ws://localhost:${PORT}`);
  const role     = urlObj.searchParams.get('role');
  const clientId = urlObj.searchParams.get('id');

  logger.debug(`New connection: role=${role} id=${clientId || 'n/a'} ip=${req.socket.remoteAddress}`);

  if (role === 'operator') {
    handleOperatorConnect(ws);
  } else if (role === 'client' && clientId) {
    handleClientConnect(ws, clientId);
  } else {
    logger.warn(`Unknown role or missing id — closing connection (role=${role} id=${clientId})`);
    ws.close(1008, 'Missing role or id');
    return;
  }

  ws.on('message', (data) => {
    let msg;
    try { msg = JSON.parse(data.toString()); }
    catch (e) { logger.error(`Invalid JSON from role=${role} id=${clientId}: ${data.toString()}`); return; }
    logger.debug(`Message from role=${role} id=${clientId || 'operator'}: type=${msg.type}`);
    routeMessage(ws, msg);
  });

  ws.on('close', (code, reason) => {
    logger.info(`Disconnected: role=${role} id=${clientId || 'operator'} code=${code} reason=${reason}`);
    handleDisconnect(ws, role, clientId);
  });

  ws.on('error', (err) => {
    logger.error(`WebSocket error role=${role} id=${clientId}: ${err.message}`);
  });
});

wss.on('error', (err) => {
  logger.error(`Server error: ${err.message}`);
});

// ---------------------------------------------------------------------------
// Connection handlers
// ---------------------------------------------------------------------------

function handleOperatorConnect(ws) {
  if (operator && operator.readyState < 2) {
    logger.warn('Operator already connected — replacing existing connection');
    operator.close(1001, 'Replaced by new operator connection');
  }
  operator = ws;
  ws._role = 'operator';
  logger.info('Operator connected');
  processQueue();
}

function handleClientConnect(ws, clientId) {
  if (clients.has(clientId)) {
    logger.warn(`Duplicate client id=${clientId} — closing existing connection`);
    clients.get(clientId).close(1001, 'Replaced by new connection with same id');
  }
  clients.set(clientId, ws);
  ws._role    = 'client';
  ws._clientId = clientId;
  logger.info(`Client connected: id=${clientId} total=${clients.size}`);
}

// ---------------------------------------------------------------------------
// Disconnect handler
// ---------------------------------------------------------------------------

function handleDisconnect(ws, role, clientId) {
  if (role === 'operator') {
    if (operator === ws) {
      operator = null;
      logger.warn('Operator disconnected — clients will be queued');
      // If an active call was in progress, notify the kiosk so it returns to idle
      if (activeCallClientId) {
        logger.info(`Operator left during active call with id=${activeCallClientId} — sending end_call`);
        sendToClient(activeCallClientId, { type: 'end_call', clientId: activeCallClientId });
        activeCallClientId = null;
      }
      // Move any pending (forwarded-but-unanswered) calls back to the wait queue
      pendingCalls.forEach(cid => {
        if (!callQueue.includes(cid)) {
          callQueue.push(cid);
          sendToClient(cid, { type: 'queued', position: callQueue.indexOf(cid) + 1 });
          logger.info(`Moved pending call id=${cid} back to queue (operator left)`);
        }
      });
      pendingCalls.clear();
    }
  } else if (role === 'client' && clientId) {
    if (clients.get(clientId) === ws) {
      clients.delete(clientId);
      pendingCalls.delete(clientId);
      if (activeCallClientId === clientId) activeCallClientId = null;
      logger.info(`Client disconnected: id=${clientId} remaining=${clients.size}`);
      // Notify operator so call UI resets even if kiosk didn't send end_call
      sendToOperator({ type: 'end_call', clientId });
      const queueIdx = callQueue.indexOf(clientId);
      if (queueIdx !== -1) {
        callQueue.splice(queueIdx, 1);
        sendQueuePositions();
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Message router
// ---------------------------------------------------------------------------

function routeMessage(ws, msg) {
  switch (msg.type) {
    case 'call':          handleCall(msg);             break;
    case 'accept':        handleAccept(msg);           break;
    case 'reject':        handleReject(msg);           break;
    case 'end_call':      handleEndCall(msg);          break;
    case 'offer':
    case 'answer':
    case 'ice_candidate': handleWebRTCSignal(ws, msg); break;
    default:
      logger.warn(`Unknown message type: ${msg.type}`);
  }
}

// ---------------------------------------------------------------------------
// Signaling logic
// ---------------------------------------------------------------------------

function handleCall(msg) {
  const { clientId } = msg;
  if (!clientId) { logger.error('call message missing clientId'); return; }

  // Deduplicate: ignore if already pending (operator notified) or already in queue
  if (pendingCalls.has(clientId) || callQueue.includes(clientId)) {
    logger.debug(`Duplicate call from id=${clientId} — already pending/queued, ignoring`);
    return;
  }

  logger.info(`Call request from id=${clientId}`);

  if (operator && operator.readyState === 1) {
    pendingCalls.add(clientId);
    sendToOperator({ type: 'incoming_call', clientId });
    logger.info(`Forwarded incoming_call to operator for id=${clientId}`);
  } else {
    callQueue.push(clientId);
    const position = callQueue.indexOf(clientId) + 1;
    sendToClient(clientId, { type: 'queued', position });
    logger.info(`Client id=${clientId} queued at position ${position} (operator offline)`);
  }
}

function handleAccept(msg) {
  const { clientId } = msg;
  logger.info(`Operator accepted call from id=${clientId}`);
  pendingCalls.delete(clientId);
  activeCallClientId = clientId;
  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) callQueue.splice(idx, 1);
  sendToClient(clientId, { type: 'accept', clientId });
}

function handleReject(msg) {
  const { clientId } = msg;
  logger.info(`Operator rejected call from id=${clientId}`);
  pendingCalls.delete(clientId);
  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) callQueue.splice(idx, 1);
  sendToClient(clientId, { type: 'reject', clientId });
  sendQueuePositions();
}

function handleEndCall(msg) {
  const { clientId, target } = msg;
  logger.info(`end_call: clientId=${clientId} target=${target || 'unknown'}`);

  if (target === 'client') {
    // Operator ended the call → notify kiosk so it resets to Idle
    sendToClient(clientId, { type: 'end_call', clientId });
  } else {
    // Kiosk ended the call → notify operator
    sendToOperator({ type: 'end_call', clientId });
  }

  pendingCalls.delete(clientId);
  if (activeCallClientId === clientId) activeCallClientId = null;
  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) { callQueue.splice(idx, 1); sendQueuePositions(); }
}

function handleWebRTCSignal(ws, msg) {
  const { type, target, clientId } = msg;
  if (type === 'ice_candidate' && msg.ice && msg.ice.candidate) {
    // Log full candidate string so we can see candidate types (host/srflx/relay)
    logger.info(`ICE [${target === 'operator' ? 'kiosk→op' : 'op→kiosk'}] ${msg.ice.candidate}`);
  } else {
    logger.debug(`WebRTC signal type=${type} target=${target} clientId=${clientId}`);
  }
  if (target === 'operator')          sendToOperator(msg);
  else if (target === 'client' && clientId) sendToClient(clientId, msg);
  else logger.error(`WebRTC signal missing valid target: ${JSON.stringify(msg)}`);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function sendToOperator(payload) {
  if (operator && operator.readyState === 1) operator.send(JSON.stringify(payload));
  else logger.warn(`Cannot send type=${payload.type} — operator not connected`);
}

function sendToClient(clientId, payload) {
  const ws = clients.get(clientId);
  if (ws && ws.readyState === 1) ws.send(JSON.stringify(payload));
  else logger.warn(`Cannot send type=${payload.type} to id=${clientId} — not connected`);
}

function sendQueuePositions() {
  callQueue.forEach((clientId, index) => {
    sendToClient(clientId, { type: 'queued', position: index + 1 });
  });
  logger.debug(`Queue updated: ${callQueue.length} clients waiting`);
}

function processQueue() {
  if (callQueue.length > 0 && operator && operator.readyState === 1) {
    const clientId = callQueue[0];
    pendingCalls.add(clientId);
    sendToOperator({ type: 'incoming_call', clientId });
    logger.info(`Drained queue: forwarded id=${clientId} to operator`);
  }
}
