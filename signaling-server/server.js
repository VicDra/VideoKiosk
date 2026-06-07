'use strict';

require('dotenv').config();
const { WebSocketServer } = require('ws');
const { URL } = require('url');
const path = require('path');
const winston = require('winston');

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
      maxsize: 5 * 1024 * 1024,   // 5 MB per file
      maxFiles: 3,
      tailable: true
    })
  ]
});

// ---------------------------------------------------------------------------
// Server bootstrap
// ---------------------------------------------------------------------------

const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });

// Map of clientId -> WebSocket for kiosk clients
const clients = new Map();

// The single connected operator WebSocket (or null)
let operator = null;

// Queue of clientIds waiting for the operator
const callQueue = [];

logger.info(`Signaling server started on port ${PORT}`);
logger.info(`Log file: ${LOG_FILE}`);

wss.on('connection', (ws, req) => {
  const urlObj = new URL(req.url, `ws://localhost:${PORT}`);
  const role = urlObj.searchParams.get('role');
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
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      logger.error(`Invalid JSON from role=${role} id=${clientId}: ${data.toString()}`);
      return;
    }
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
    const existing = clients.get(clientId);
    existing.close(1001, 'Replaced by new connection with same id');
  }
  clients.set(clientId, ws);
  ws._role = 'client';
  ws._clientId = clientId;
  logger.info(`Client connected: id=${clientId} total=${clients.size}`);
}

// ---------------------------------------------------------------------------
// Disconnect handler
// ---------------------------------------------------------------------------

function handleDisconnect(ws, role, clientId) {
  if (role === 'operator') {
    // Only clear operator if it's the same WebSocket that just closed.
    // If it was already replaced by a new operator connection, ignore the stale close event.
    if (operator === ws) {
      operator = null;
      logger.warn('Operator disconnected — clients will be queued');
    } else {
      logger.debug('Stale operator close event — already replaced, ignoring');
    }
  } else if (role === 'client' && clientId) {
    // Only remove client if this ws is still the active one for this clientId.
    // When a duplicate connection replaces an old one, the old ws fires a close event
    // AFTER the new ws has already been registered — without this guard, the new entry
    // would be deleted from the map and subsequent sends would fail.
    if (clients.get(clientId) === ws) {
      clients.delete(clientId);
      logger.info(`Client disconnected: id=${clientId} remaining=${clients.size}`);

      const queueIdx = callQueue.indexOf(clientId);
      if (queueIdx !== -1) {
        callQueue.splice(queueIdx, 1);
        logger.debug(`Removed id=${clientId} from queue (was pos ${queueIdx + 1})`);
        sendQueuePositions();
      }
    } else {
      logger.debug(`Stale close event for replaced client id=${clientId} — ignoring`);
    }
  }
}

// ---------------------------------------------------------------------------
// Message router
// ---------------------------------------------------------------------------

function routeMessage(ws, msg) {
  const { type } = msg;

  switch (type) {
    case 'call':          handleCall(msg);              break;
    case 'accept':        handleAccept(msg);            break;
    case 'reject':        handleReject(msg);            break;
    case 'end_call':       handleEndCall(msg);           break;
    case 'offer':
    case 'answer':
    case 'ice_candidate': handleWebRTCSignal(ws, msg);  break;
    default:
      logger.warn(`Unknown message type: ${type}`);
  }
}

// ---------------------------------------------------------------------------
// Signaling logic
// ---------------------------------------------------------------------------

function handleCall(msg) {
  const { clientId } = msg;
  if (!clientId) {
    logger.error('call message missing clientId field');
    return;
  }

  logger.info(`Call request from id=${clientId}`);

  if (operator && operator.readyState === 1) {
    sendToOperator({ type: 'incoming_call', clientId });
    logger.info(`Forwarded incoming_call to operator for id=${clientId}`);
  } else {
    if (!callQueue.includes(clientId)) {
      callQueue.push(clientId);
    }
    const position = callQueue.indexOf(clientId) + 1;
    sendToClient(clientId, { type: 'queued', position });
    logger.info(`Client id=${clientId} queued at position ${position} (operator offline)`);
  }
}

function handleAccept(msg) {
  const { clientId } = msg;
  logger.info(`Operator accepted call from id=${clientId}`);

  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) callQueue.splice(idx, 1);

  sendToClient(clientId, { type: 'accept', clientId });
}

function handleReject(msg) {
  const { clientId } = msg;
  logger.info(`Operator rejected call from id=${clientId}`);

  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) callQueue.splice(idx, 1);

  sendToClient(clientId, { type: 'reject', clientId });
  sendQueuePositions();
}

function handleEndCall(msg) {
  const { clientId } = msg;
  logger.info(`Call ended by client id=${clientId}`);
  // Notify operator so it can tear down WebRTC
  sendToOperator({ type: 'end_call', clientId });
  // Remove from queue if still waiting
  const idx = callQueue.indexOf(clientId);
  if (idx !== -1) {
    callQueue.splice(idx, 1);
    sendQueuePositions();
  }
}

function handleWebRTCSignal(ws, msg) {
  const { type, target, clientId } = msg;
  logger.debug(`WebRTC signal type=${type} target=${target} clientId=${clientId}`);

  if (target === 'operator') {
    sendToOperator(msg);
  } else if (target === 'client' && clientId) {
    sendToClient(clientId, msg);
  } else {
    logger.error(`WebRTC signal missing valid target: ${JSON.stringify(msg)}`);
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function sendToOperator(payload) {
  if (operator && operator.readyState === 1) {
    operator.send(JSON.stringify(payload));
  } else {
    logger.warn(`Cannot send type=${payload.type} — operator not connected`);
  }
}

function sendToClient(clientId, payload) {
  const ws = clients.get(clientId);
  if (ws && ws.readyState === 1) {
    ws.send(JSON.stringify(payload));
  } else {
    logger.warn(`Cannot send type=${payload.type} to id=${clientId} — not connected`);
  }
}

function sendQueuePositions() {
  callQueue.forEach((clientId, index) => {
    sendToClient(clientId, { type: 'queued', position: index + 1 });
  });
  logger.debug(`Queue updated: ${callQueue.length} clients waiting`);
}

function processQueue() {
  if (callQueue.length > 0 && operator && operator.readyState === 1) {
    const nextClientId = callQueue[0];
    sendToOperator({ type: 'incoming_call', clientId: nextClientId });
    logger.info(`Drained queue: forwarded id=${nextClientId} to operator`);
  }
}
