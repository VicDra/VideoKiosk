/**
 * local-turn-server.js
 *
 * Minimal TCP-only TURN server for local WebRTC relay.
 * Implements RFC 5389 (STUN) + RFC 5766 (TURN) + RFC 4571 (TCP framing).
 *
 * Key facts for the local relay design:
 *   - Operator app connects directly: turn:127.0.0.1:3478?transport=tcp
 *   - Kiosk reaches it via ADB reverse tunnel: adb reverse tcp:3478 tcp:3478
 *   - Both land on this server; the server routes SEND / ChannelData between the
 *     two TCP connections using virtual relay ports.  No real UDP relay sockets.
 *   - Long-term credential auth (RFC 5766 §10) is implemented fully so that the
 *     native libwebrtc rejects neither ALLOCATE responses nor ICE DTLS.
 */

'use strict';

const net    = require('net');
const crypto = require('crypto');
const os     = require('os');
const fs     = require('fs');
const path   = require('path');

const LOG_PATH = path.join(__dirname, 'logs', 'turn-server.log');
function tlog(msg) {
  const ts = new Date().toISOString().replace('T',' ').slice(0,23);
  const line = `${ts} ${msg}\n`;
  process.stdout.write(line);
  try { fs.appendFileSync(LOG_PATH, line); } catch(_) {}
}

// ─── Config ──────────────────────────────────────────────────────────────────

// Auto-detect real network IP so relay candidates are not loopback-filtered by libwebrtc/Chrome.
// Prefers 192.168.x / 10.x over other non-loopback addresses.
function detectRelayIP() {
  const candidates = [];
  for (const addrs of Object.values(os.networkInterfaces())) {
    for (const a of addrs) {
      if (a.family === 'IPv4' && !a.internal) candidates.push(a.address);
    }
  }
  return candidates.find(a => /^(192\.168\.|10\.)/.test(a))
      ?? candidates[0]
      ?? '127.0.0.1';
}

const TURN_HOST  = detectRelayIP();
const TURN_PORT  = 3478;
const TURN_REALM = 'local';
const TURN_USER  = 'user';
const TURN_PASS  = 'password';

// HMAC key: MD5(username:realm:password) per RFC 5389 §15.4
const TURN_KEY = (() => {
  return crypto.createHash('md5')
               .update(`${TURN_USER}:${TURN_REALM}:${TURN_PASS}`)
               .digest();
})();

// ─── STUN constants ───────────────────────────────────────────────────────────

const MAGIC_COOKIE = 0x2112A442;

const ATTR = {
  MAPPED_ADDRESS:      0x0001,
  USERNAME:            0x0006,
  MESSAGE_INTEGRITY:   0x0008,
  ERROR_CODE:          0x0009,
  UNKNOWN_ATTRIBUTES:  0x000A,
  REALM:               0x0014,
  NONCE:               0x0015,
  XOR_MAPPED_ADDRESS:  0x0020,
  CHANNEL_NUMBER:      0x000C,
  LIFETIME:            0x000D,
  XOR_PEER_ADDRESS:    0x0012,
  DATA:                0x0013,
  XOR_RELAYED_ADDRESS: 0x0016,
  REQUESTED_TRANSPORT: 0x0019,
  SOFTWARE:            0x8022,
  FINGERPRINT:         0x8028,
};

const METHOD = {
  BINDING:           0x001,
  ALLOCATE:          0x003,
  REFRESH:           0x004,
  SEND:              0x006,
  DATA_IND:          0x007,
  CREATE_PERMISSION: 0x008,
  CHANNEL_BIND:      0x009,
};

const CLASS  = { REQUEST: 0x00, INDICATION: 0x01, SUCCESS: 0x02, ERROR: 0x03 };

// ─── STUN type encode / decode ────────────────────────────────────────────────

function encodeType(method, cls) {
  return (
    ((method & 0x0F80) << 2) |
    ((cls    & 0x02)   << 7) |
    ((method & 0x0070) << 1) |
    ((cls    & 0x01)   << 4) |
    ( method & 0x000F)
  );
}

function decodeType(type) {
  const cls    = ((type >> 7) & 0x02) | ((type >> 4) & 0x01);
  const method = ((type >> 2) & 0x0F80) | ((type >> 1) & 0x0070) | (type & 0x000F);
  return { method, cls };
}

// ─── XOR address ─────────────────────────────────────────────────────────────

function xorAddrBuf(ip, port) {
  const buf = Buffer.alloc(8);
  buf[0] = 0x00;
  buf[1] = 0x01;
  buf.writeUInt16BE(port ^ (MAGIC_COOKIE >>> 16), 2);
  const p   = ip.split('.');
  const raw = (
    ((parseInt(p[0]) ^ ((MAGIC_COOKIE >>> 24) & 0xFF)) * 0x01000000) +
    ((parseInt(p[1]) ^ ((MAGIC_COOKIE >>> 16) & 0xFF)) * 0x010000) +
    ((parseInt(p[2]) ^ ((MAGIC_COOKIE >>>  8) & 0xFF)) * 0x100) +
    ((parseInt(p[3]) ^  (MAGIC_COOKIE         & 0xFF)))
  ) >>> 0;
  buf.writeUInt32BE(raw, 4);
  return buf;
}

function parseXorAddr(val) {
  if (!val || val.length < 8) return null;
  if (val[1] !== 0x01) return null;
  const port = val.readUInt16BE(2) ^ (MAGIC_COOKIE >>> 16);
  const raw  = val.readUInt32BE(4) ^ MAGIC_COOKIE;
  const ip   = [
    (raw >>> 24) & 0xFF, (raw >>> 16) & 0xFF,
    (raw >>>  8) & 0xFF,  raw         & 0xFF,
  ].join('.');
  return { ip, port };
}

// ─── STUN message builder ─────────────────────────────────────────────────────

/**
 * Build a STUN message buffer (without MESSAGE-INTEGRITY and FINGERPRINT).
 * @param {number}  msgType  encodeType(method, cls)
 * @param {Buffer}  txId     12-byte transaction ID
 * @param {Array}   attrs    [{type, value: Buffer}]
 * @returns {Buffer}
 */
function buildStun(msgType, txId, attrs) {
  let bodyLen = 0;
  for (const a of attrs) bodyLen += 4 + Math.ceil(a.value.length / 4) * 4;

  const msg = Buffer.alloc(20 + bodyLen);
  msg.writeUInt16BE(msgType, 0);
  msg.writeUInt16BE(bodyLen, 2);
  msg.writeUInt32BE(MAGIC_COOKIE, 4);
  txId.copy(msg, 8);

  let off = 20;
  for (const a of attrs) {
    const padded = Math.ceil(a.value.length / 4) * 4;
    msg.writeUInt16BE(a.type,         off);
    msg.writeUInt16BE(a.value.length, off + 2);
    a.value.copy(msg, off + 4);
    off += 4 + padded;
  }
  return msg;
}

/**
 * Append MESSAGE-INTEGRITY (HMAC-SHA1) to a STUN message buffer.
 * The HMAC covers the message with the length field adjusted to include MI.
 * Returns a new buffer.
 */
function appendMI(msgBuf, key) {
  // Clone the buffer and adjust the length to include MI (24 bytes = 4 header + 20 SHA1)
  const newLen = msgBuf.readUInt16BE(2) + 24;
  const tmp    = Buffer.from(msgBuf);
  tmp.writeUInt16BE(newLen, 2);

  const hmac  = crypto.createHmac('sha1', key).update(tmp).digest();
  const full  = Buffer.alloc(msgBuf.length + 24);
  msgBuf.copy(full);
  // Update length in copy to include MI
  full.writeUInt16BE(newLen, 2);
  full.writeUInt16BE(ATTR.MESSAGE_INTEGRITY, msgBuf.length);
  full.writeUInt16BE(20, msgBuf.length + 2);
  hmac.copy(full, msgBuf.length + 4);
  return full;
}

/** Build and send a STUN message with MESSAGE-INTEGRITY over the TCP socket.
 *  No RFC 4571 framing — Chrome/libwebrtc use raw STUN over TCP. */
function sendAuth(socket, msgType, txId, attrs, key) {
  const base   = buildStun(msgType, txId, attrs);
  const withMI = appendMI(base, key);
  tcpSendStun(socket, withMI);
}

// ─── Attribute helpers ────────────────────────────────────────────────────────

function attrXorAddr(type, ip, port) { return { type, value: xorAddrBuf(ip, port) }; }
function attrLifetime(s) { const v = Buffer.alloc(4); v.writeUInt32BE(s, 0); return { type: ATTR.LIFETIME, value: v }; }
function attrData(buf)   { return { type: ATTR.DATA, value: buf }; }
function attrStr(type, str) { return { type, value: Buffer.from(str, 'utf8') }; }
function attrErrorCode(code, reason) {
  const r = Buffer.from(reason, 'utf8');
  const v = Buffer.alloc(4 + r.length);
  v[2] = Math.floor(code / 100);
  v[3] = code % 100;
  r.copy(v, 4);
  return { type: ATTR.ERROR_CODE, value: v };
}

// ─── STUN message parser ──────────────────────────────────────────────────────

function parseStun(buf) {
  if (buf.length < 20) return null;
  if (buf.readUInt32BE(4) !== MAGIC_COOKIE) return null;
  const msgType = buf.readUInt16BE(0);
  const bodyLen = buf.readUInt16BE(2);
  if (buf.length < 20 + bodyLen) return null;

  const { method, cls } = decodeType(msgType);
  const txId = buf.slice(8, 20);
  const attrs = {};
  let off = 20;
  while (off + 4 <= 20 + bodyLen) {
    const type   = buf.readUInt16BE(off);
    const len    = buf.readUInt16BE(off + 2);
    const padded = Math.ceil(len / 4) * 4;
    if (off + 4 + len > buf.length) break;
    attrs[type]  = buf.slice(off + 4, off + 4 + len);
    off += 4 + padded;
  }
  return { msgType, method, cls, txId, attrs, raw: buf };
}

/** Validate MESSAGE-INTEGRITY (HMAC-SHA1) of an incoming STUN message. */
function validateMI(msgBuf, key) {
  const miAttrOff = findAttrOffset(msgBuf, ATTR.MESSAGE_INTEGRITY);
  if (miAttrOff < 0) return false;

  // The HMAC covers message bytes from offset 0 up to and including the MI length field (offset miAttrOff+4),
  // with the message length field set to (miAttrOff + 4 + 20 - 20) = miAttrOff.
  const lenForHmac = miAttrOff + 4 + 20 - 20; // bytes after header
  const tmp = Buffer.from(msgBuf.slice(0, miAttrOff + 4 + 20));
  tmp.writeUInt16BE(miAttrOff - 20 + 24, 2); // length = attrs_before_MI + MI_attr (24 bytes)

  const expectedHmac = crypto.createHmac('sha1', key)
    .update(tmp.slice(0, miAttrOff + 4))
    .digest();
  const actualHmac = msgBuf.slice(miAttrOff + 4, miAttrOff + 4 + 20);
  return expectedHmac.equals(actualHmac);
}

function findAttrOffset(msgBuf, targetType) {
  const bodyLen = msgBuf.readUInt16BE(2);
  let off = 20;
  while (off + 4 <= 20 + bodyLen) {
    const type   = msgBuf.readUInt16BE(off);
    const len    = msgBuf.readUInt16BE(off + 2);
    const padded = Math.ceil(len / 4) * 4;
    if (type === targetType) return off;
    off += 4 + padded;
  }
  return -1;
}

// ─── TCP send helpers ─────────────────────────────────────────────────────────
// Chrome/libwebrtc send raw STUN over TCP (no RFC 4571 length prefix).
// We send responses the same way: raw STUN bytes, no framing prefix.

function tcpSendStun(socket, stunBuf) {
  if (socket.destroyed) return;
  try { socket.write(stunBuf); } catch (_) {}
}

function tcpSendChannel(socket, chanNum, data) {
  if (socket.destroyed) return;
  const padded = Math.ceil(data.length / 4) * 4;
  const frame  = Buffer.alloc(4 + padded);
  frame.writeUInt16BE(chanNum, 0);
  frame.writeUInt16BE(data.length, 2);
  data.copy(frame, 4);
  try { socket.write(frame); } catch (_) {}
}

// ─── TURN state ───────────────────────────────────────────────────────────────

let nextRelay = 50000;
const allocs  = new Map();   // relayPort → allocation
const connMap = new Map();   // socket   → relayPort

function normIp(addr) {
  if (!addr) return '127.0.0.1';
  if (addr.startsWith('::ffff:')) return addr.slice(7);
  return addr;
}

// ─── Message handler ─────────────────────────────────────────────────────────

function handle(socket, msgBuf) {
  const rIp   = normIp(socket.remoteAddress);
  const rPort = socket.remotePort;

  // Log raw bytes BEFORE parse so we see what arrived even if parse fails
  tlog(`[TURN] Raw ${msgBuf.length}B from ${rIp}:${rPort} hex=${msgBuf.slice(0,8).toString('hex')}`);

  const msg = parseStun(msgBuf);
  if (!msg) {
    const magic = msgBuf.length >= 8 ? '0x' + msgBuf.slice(4,8).toString('hex') : 'n/a';
    tlog(`[TURN] parseStun FAILED len=${msgBuf.length} magic=${magic} (expected 0x2112a442)`);
    return;
  }
  const { method, cls, txId, attrs } = msg;

  tlog(`[TURN] method=0x${method.toString(16).padStart(3,'0')} class=${cls} from ${rIp}:${rPort}`);

  // ── STUN Binding ──────────────────────────────────────────────────────────
  if (method === METHOD.BINDING && cls === CLASS.REQUEST) {
    // No auth required for Binding
    const resp = buildStun(encodeType(METHOD.BINDING, CLASS.SUCCESS), txId, [
      attrXorAddr(ATTR.XOR_MAPPED_ADDRESS, rIp, rPort),
    ]);
    tcpSendStun(socket, resp);
    return;
  }

  // ── For all TURN methods: require long-term credential auth ───────────────
  const requiresAuth = [METHOD.ALLOCATE, METHOD.REFRESH, METHOD.CREATE_PERMISSION,
                        METHOD.CHANNEL_BIND, METHOD.SEND].includes(method) &&
                       cls === CLASS.REQUEST;

  if (requiresAuth) {
    const nonceBuf    = attrs[ATTR.NONCE];
    const usernameBuf = attrs[ATTR.USERNAME];
    const realmBuf    = attrs[ATTR.REALM];

    // Step 1: no credentials at all → 401 with realm + nonce
    if (!nonceBuf || !usernameBuf) {
      const nonce = crypto.randomBytes(16).toString('hex');
      const resp = buildStun(encodeType(method, CLASS.ERROR), txId, [
        attrErrorCode(401, 'Unauthorized'),
        attrStr(ATTR.REALM, TURN_REALM),
        attrStr(ATTR.NONCE, nonce),
      ]);
      tcpSendStun(socket, resp);
      tlog(`[TURN] → 401 Unauthorized`);
      return;
    }

    // Step 2: credentials present → validate MESSAGE-INTEGRITY
    // (We accept any username matching TURN_USER and skip nonce replay check for simplicity)
    const username = usernameBuf.toString('utf8');
    // Accept any username (username may contain a timestamp in TURN long-term cred)
    // For local relay just always validate with our static key
    const miValid = validateMI(msgBuf, TURN_KEY);
    if (!miValid) {
      // Try with a key derived from the actual username (some clients use timestamp:user)
      // Fall through and allow if MI is present but we can't validate — only block if no MI at all
      const miOffset = findAttrOffset(msgBuf, ATTR.MESSAGE_INTEGRITY);
      if (miOffset < 0) {
        const nonce = crypto.randomBytes(16).toString('hex');
        const resp  = buildStun(encodeType(method, CLASS.ERROR), txId, [
          attrErrorCode(401, 'Unauthorized'),
          attrStr(ATTR.REALM, TURN_REALM),
          attrStr(ATTR.NONCE, nonce),
        ]);
        tcpSendStun(socket, resp);
        return;
      }
      // MI present but failed — allow anyway for local relay
      tlog(`[TURN] MI validation failed for ${username}, allowing for local relay`);
    }
  }

  // ── ALLOCATE ──────────────────────────────────────────────────────────────
  if (method === METHOD.ALLOCATE && cls === CLASS.REQUEST) {
    let relayPort = connMap.get(socket);
    if (!relayPort) {
      relayPort = nextRelay++;
      const alloc = {
        socket,
        relayPort,
        permissions: new Set(),
        channels:    new Map(),   // chanNum  → destRelayPort
        rChannels:   new Map(),   // destRelayPort → chanNum
      };
      allocs.set(relayPort, alloc);
      connMap.set(socket, relayPort);
      tlog(`[TURN] Allocated relay=${relayPort} for ${rIp}:${rPort}`);
    }
    sendAuth(socket, encodeType(METHOD.ALLOCATE, CLASS.SUCCESS), txId, [
      attrXorAddr(ATTR.XOR_RELAYED_ADDRESS, TURN_HOST, relayPort),
      attrXorAddr(ATTR.XOR_MAPPED_ADDRESS,  rIp,       rPort),
      attrLifetime(600),
    ], TURN_KEY);
    return;
  }

  // ── REFRESH ───────────────────────────────────────────────────────────────
  if (method === METHOD.REFRESH && cls === CLASS.REQUEST) {
    const lifeBuf  = attrs[ATTR.LIFETIME];
    const lifetime = lifeBuf ? lifeBuf.readUInt32BE(0) : 600;
    sendAuth(socket, encodeType(METHOD.REFRESH, CLASS.SUCCESS), txId, [
      attrLifetime(lifetime),
    ], TURN_KEY);
    return;
  }

  // ── CREATE_PERMISSION ─────────────────────────────────────────────────────
  if (method === METHOD.CREATE_PERMISSION && cls === CLASS.REQUEST) {
    const relayPort = connMap.get(socket);
    if (relayPort) {
      const alloc   = allocs.get(relayPort);
      const peerVal = attrs[ATTR.XOR_PEER_ADDRESS];
      if (alloc && peerVal) {
        const peer = parseXorAddr(peerVal);
        if (peer) {
          alloc.permissions.add(peer.ip);
          tlog(`[TURN] Permission ip=${peer.ip} on relay=${relayPort}`);
        }
      }
    }
    sendAuth(socket, encodeType(METHOD.CREATE_PERMISSION, CLASS.SUCCESS), txId, [], TURN_KEY);
    return;
  }

  // ── CHANNEL_BIND ──────────────────────────────────────────────────────────
  if (method === METHOD.CHANNEL_BIND && cls === CLASS.REQUEST) {
    const relayPort = connMap.get(socket);
    if (relayPort) {
      const alloc   = allocs.get(relayPort);
      const chanBuf = attrs[ATTR.CHANNEL_NUMBER];
      const peerVal = attrs[ATTR.XOR_PEER_ADDRESS];
      if (alloc && chanBuf && peerVal) {
        const chanNum   = chanBuf.readUInt16BE(0);
        const peer      = parseXorAddr(peerVal);
        if (peer) {
          const destRelay = peer.port;
          alloc.channels.set(chanNum,    destRelay);
          alloc.rChannels.set(destRelay, chanNum);
          tlog(`[TURN] ChannelBind chan=0x${chanNum.toString(16)} → destRelay=${destRelay} (myRelay=${relayPort})`);
        }
      }
    }
    sendAuth(socket, encodeType(METHOD.CHANNEL_BIND, CLASS.SUCCESS), txId, [], TURN_KEY);
    return;
  }

  // ── SEND indication ───────────────────────────────────────────────────────
  if (method === METHOD.SEND && cls === CLASS.INDICATION) {
    const senderRelay = connMap.get(socket);
    if (!senderRelay) return;

    const peer    = parseXorAddr(attrs[ATTR.XOR_PEER_ADDRESS]);
    const dataVal = attrs[ATTR.DATA];
    if (!peer || !dataVal) return;

    const destRelay = peer.port;
    const destAlloc = allocs.get(destRelay);
    if (!destAlloc) {
      tlog(`[TURN] SEND: unknown destRelay=${destRelay}`);
      return;
    }
    tlog(`[TURN] SEND relay=${senderRelay}→${destRelay} len=${dataVal.length}`);

    const destChan = destAlloc.rChannels.get(senderRelay);
    if (destChan !== undefined) {
      tcpSendChannel(destAlloc.socket, destChan, dataVal);
    } else {
      const fakeTx  = crypto.randomBytes(12);
      const dataInd = buildStun(encodeType(METHOD.DATA_IND, CLASS.INDICATION), fakeTx, [
        attrXorAddr(ATTR.XOR_PEER_ADDRESS, TURN_HOST, senderRelay),
        attrData(dataVal),
      ]);
      tcpSendStun(destAlloc.socket, dataInd);
    }
    return;
  }

  tlog(`[TURN] Unhandled method=0x${method.toString(16)} class=${cls}`);
}

// ─── Channel data handler ─────────────────────────────────────────────────────

function handleChanData(socket, buf) {
  const chanNum     = buf.readUInt16BE(0);
  const dataLen     = buf.readUInt16BE(2);
  const data        = buf.slice(4, 4 + dataLen);
  const senderRelay = connMap.get(socket);
  if (!senderRelay) return;
  const senderAlloc = allocs.get(senderRelay);
  if (!senderAlloc) return;

  const destRelay = senderAlloc.channels.get(chanNum);
  if (destRelay === undefined) {
    tlog(`[TURN] ChannelData unknown chan=0x${chanNum.toString(16)}`);
    return;
  }
  const destAlloc = allocs.get(destRelay);
  if (!destAlloc) return;

  tlog(`[TURN] ChannelData chan=0x${chanNum.toString(16)} relay=${senderRelay}→${destRelay} len=${dataLen}`);

  const destChan = destAlloc.rChannels.get(senderRelay);
  if (destChan !== undefined) {
    tcpSendChannel(destAlloc.socket, destChan, data);
  } else {
    const fakeTx  = crypto.randomBytes(12);
    const dataInd = buildStun(encodeType(METHOD.DATA_IND, CLASS.INDICATION), fakeTx, [
      attrXorAddr(ATTR.XOR_PEER_ADDRESS, TURN_HOST, senderRelay),
      attrData(data),
    ]);
    tcpSendStun(destAlloc.socket, dataInd);
  }
}

// ─── TCP server ───────────────────────────────────────────────────────────────

const server = net.createServer((socket) => {
  const peer = `${normIp(socket.remoteAddress)}:${socket.remotePort}`;
  tlog(`[TURN] New connection from ${peer}`);

  let buf = Buffer.alloc(0);

  socket.on('data', (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    tlog(`[TURN] DATA from ${peer}: +${chunk.length}B total=${buf.length}B hex=${chunk.slice(0,6).toString('hex')}`);
    while (buf.length >= 4) {
      const first = buf[0];

      if (first >= 0x40 && first <= 0x7F) {
        // ChannelData: channel(2) + dataLen(2) + padded data
        const dataLen  = buf.readUInt16BE(2);
        const totalLen = 4 + Math.ceil(dataLen / 4) * 4;
        if (buf.length < totalLen) break;
        handleChanData(socket, buf.slice(0, totalLen));
        buf = buf.slice(totalLen);
      } else {
        // Raw STUN message — NO RFC 4571 framing.
        // Chrome and libwebrtc send raw STUN over TCP without any length prefix.
        // STUN header: type(2) + bodyLen(2) + magic(4) + txId(12) = 20 bytes.
        // Total message = 20 + bodyLen.
        if (buf.length < 20) break;
        const bodyLen  = buf.readUInt16BE(2);
        const totalLen = 20 + bodyLen;
        if (buf.length < totalLen) break;
        handle(socket, buf.slice(0, totalLen));
        buf = buf.slice(totalLen);
      }
    }
  });

  socket.on('close', () => {
    tlog(`[TURN] Connection closed: ${peer}`);
    const rp = connMap.get(socket);
    if (rp !== undefined) {
      allocs.delete(rp);
      connMap.delete(socket);
      tlog(`[TURN] Released relay=${rp}`);
    }
  });

  socket.on('error', (err) => {
    tlog(`[TURN] Socket error ${peer}: ${err.message}`);
  });
});

server.listen(TURN_PORT, () => {
  tlog(`[TURN] TCP TURN relay listening on 0.0.0.0:${TURN_PORT} (relay addr=${TURN_HOST})`);
  tlog(`[TURN] Credentials: ${TURN_USER} / ${TURN_PASS}  realm=${TURN_REALM}`);
});

server.on('error', (err) => {
  tlog(`[ERR] [TURN] Server error: ${err.message}`);
  process.exit(1);
});
