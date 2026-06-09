/**
 * cdp-test.js
 * Launches Chrome with remote-debugging, opens operator page,
 * waits for incoming call, clicks Accept, waits 20s, then reads
 * the ICE debug panel and dumps it to stdout.
 */
'use strict';

const http             = require('http');
const { spawn, execSync } = require('child_process');
const WebSocket        = require('ws');
const fs               = require('fs');
const path             = require('path');

const ADB = 'C:\\sdk\\platform-tools\\adb.exe';

/** Tap kiosk screen via ADB — retries if needed */
function adbTap(x, y) {
  try {
    execSync(`"${ADB}" shell input tap ${x} ${y}`, { timeout: 5000 });
    log(`[ADB] tap ${x},${y} OK`);
  } catch (e) {
    log(`[ADB] tap ${x},${y} FAILED: ${e.message}`);
  }
}

const LOG = path.join(__dirname, 'logs', 'cdp-test.log');
fs.writeFileSync(LOG, '');   // truncate

function log(msg) {
  const ts  = new Date().toLocaleTimeString('ru-RU',{hour12:false}) + '.' +
              String(new Date().getMilliseconds()).padStart(3,'0');
  const line = `[${ts}] ${msg}\n`;
  process.stdout.write(line);
  fs.appendFileSync(LOG, line);
}
// Redirect console.log
const _log = console.log.bind(console);
console.log   = (...a) => log(a.map(String).join(' '));
console.error = (...a) => log('[ERR] ' + a.map(String).join(' '));

const CHROME      = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const DEBUG_PORT  = 9223;
const OP_URL      = 'http://localhost:8080/';
const WAIT_MS     = 25000;  // wait after accepting before reading debug panel
const PROFILE_DIR = 'C:\\tmp\\chrome-cdp-debug2';

// ── Launch Chrome ──────────────────────────────────────────────────────────
function launchChrome() {
  return new Promise((resolve) => {
    // Kill any process holding the debug port (title-based taskkill misses
    // Chrome windows whose title changed, e.g. "Оператор — ЕИРЦ ЛО")
    try {
      const ns = execSync('netstat -ano', { encoding: 'utf8', timeout: 5000 });
      const portRe = new RegExp(`[:\\s]${DEBUG_PORT}\\s+.*LISTENING\\s+(\\d+)`, 'i');
      const m = ns.match(portRe);
      if (m) {
        const pid = m[1];
        try { execSync(`taskkill /F /PID ${pid}`, { timeout: 3000 }); } catch (_) {}
        log(`[CDP] Killed PID=${pid} (held debug port ${DEBUG_PORT})`);
        // Give OS time to release the port
        execSync('timeout /T 1 /NOBREAK >nul 2>&1', { timeout: 3000, shell: true });
      }
    } catch (_) {}
    // Clear previous profile so Chrome doesn't restore old operator.html tabs
    // (restored tabs create a second operator WebSocket that fights with this one)
    try {
      execSync(`rd /s /q "${PROFILE_DIR}" 2>nul`, { timeout: 3000 });
      log('[CDP] Profile directory cleared');
    } catch (_) {
      log('[CDP] Profile dir clear skipped (not found)');
    }

    const args = [
      `--remote-debugging-port=${DEBUG_PORT}`,
      `--user-data-dir=${PROFILE_DIR}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--no-restore-last-session',      // prevent session restore that spawns extra tabs
      '--disable-background-timer-throttling',
      '--disable-backgrounding-occluded-windows',
      '--disable-renderer-backgrounding',
      '--disable-session-crashed-bubble',
      '--disable-infobars',
      // Send real LAN IP instead of mDNS *.local — Android libwebrtc can't resolve .local
      '--disable-features=WebRtcHideLocalIpsWithMdns',
      // Allow autoplay without user gesture (CDP session has no human interaction)
      '--autoplay-policy=no-user-gesture-required',
      // Allow getUserMedia without real hardware (fake camera for automated testing)
      '--use-fake-ui-for-media-stream',
      '--use-fake-device-for-media-stream',
      OP_URL,
    ];
    const proc = spawn(CHROME, args, { detached: true, stdio: 'ignore' });
    proc.unref();
    console.log(`[CDP] Chrome launched PID=${proc.pid}`);
    // Give Chrome time to start and open the DevTools port
    setTimeout(resolve, 3000);
  });
}

// ── HTTP helper ────────────────────────────────────────────────────────────
function httpGet(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = '';
      res.on('data', d => data += d);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); }
        catch(e) { resolve(data); }
      });
    }).on('error', reject);
  });
}

/**
 * Poll GET /status until kiosk_1 appears in the connected-clients list.
 * Returns true if online within timeoutMs, false otherwise.
 * The kiosk app re-establishes its WebSocket after am start; this wait is
 * necessary to avoid tapping the call button before the WS is ready.
 */
async function waitForKioskOnline(kioskId, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  let logged = false;
  while (Date.now() < deadline) {
    try {
      const status = await httpGet('http://localhost:8080/status');
      if (Array.isArray(status.clients) && status.clients.includes(kioskId)) {
        log(`[CDP] Kiosk ${kioskId} is online (server /status confirmed)`);
        return true;
      }
      if (!logged) { log(`[CDP] Waiting for ${kioskId} to connect… (status=${JSON.stringify(status)})`); logged = true; }
    } catch (_) {}
    await sleep(400);
  }
  log(`[CDP] WARN: ${kioskId} did not appear in /status within ${timeoutMs}ms — tapping anyway`);
  return false;
}

// ── CDP session ────────────────────────────────────────────────────────────
class CDPSession {
  constructor(wsUrl) {
    this.ws  = new WebSocket(wsUrl);
    this.id  = 1;
    this.cbs = new Map();
    this.ws.on('message', (raw) => {
      const msg = JSON.parse(raw);
      if (msg.id && this.cbs.has(msg.id)) {
        const { resolve, reject } = this.cbs.get(msg.id);
        this.cbs.delete(msg.id);
        if (msg.error) reject(new Error(JSON.stringify(msg.error)));
        else resolve(msg.result);
      }
    });
    this.ws.on('error', e => console.error('[CDP] WS error', e.message));
  }

  ready() {
    return new Promise((resolve, reject) => {
      if (this.ws.readyState === WebSocket.OPEN) return resolve();
      this.ws.on('open', resolve);
      this.ws.on('error', reject);
    });
  }

  send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = this.id++;
      this.cbs.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async eval(expr) {
    const r = await this.send('Runtime.evaluate', {
      expression: expr,
      returnByValue: true,
      awaitPromise: false,
    });
    return r && r.result && r.result.value;
  }

  close() { this.ws.close(); }
}

// ── Main ───────────────────────────────────────────────────────────────────
async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function main() {
  console.log('[CDP] Launching Chrome...');
  await launchChrome();

  // Find our tab
  let tab;
  for (let attempt = 0; attempt < 10; attempt++) {
    try {
      const tabs = await httpGet(`http://localhost:${DEBUG_PORT}/json`);
      tab = Array.isArray(tabs) && tabs.find(t => t.type === 'page');
      if (tab) break;
    } catch(_) {}
    console.log(`[CDP] Waiting for Chrome... attempt ${attempt + 1}`);
    await sleep(1500);
  }
  if (!tab) { console.error('[CDP] Could not find Chrome tab'); process.exit(1); }
  console.log(`[CDP] Connected to tab: ${tab.url}`);

  const cdp = new CDPSession(tab.webSocketDebuggerUrl);
  await cdp.ready();
  await cdp.send('Runtime.enable');
  console.log('[CDP] Runtime enabled');

  // Navigate to operator page if not already there
  const curUrl = await cdp.eval('location.href');
  if (!curUrl || !curUrl.includes('localhost:8080')) {
    await cdp.send('Page.enable');
    await cdp.send('Page.navigate', { url: OP_URL });
    await sleep(3000);
    console.log('[CDP] Navigated to operator page');
  }

  // Wait for debug panel to show TURN config line
  console.log('[CDP] Waiting for page to initialise...');
  for (let i = 0; i < 10; i++) {
    const dbg = await cdp.eval(`document.getElementById('dbg')?.innerText || ''`);
    if (dbg && dbg.includes('UI loaded')) break;
    await sleep(500);
  }
  const initMsg = await cdp.eval(`document.getElementById('dbg')?.innerText || 'NO DBG PANEL'`);
  console.log('[CDP] Debug panel on init:\n' + initMsg);

  // Wake kiosk screen and bring app to foreground BEFORE tapping the call button.
  // The kiosk can go to background/sleep between test runs; a tap on a locked screen
  // does nothing — the call never reaches the server.
  console.log('[CDP] Waking kiosk and bringing to foreground before tap...');
  try {
    execSync(`"${ADB}" shell input keyevent KEYCODE_WAKEUP`, { timeout: 3000 });
    execSync(`"${ADB}" shell am start -W --activity-clear-top -n com.videokiosk.kiosk/.MainActivity`, { timeout: 8000 });
    log('[ADB] kiosk awake and foregrounded');
  } catch (e) {
    log(`[ADB] pre-tap foreground failed: ${e.message}`);
  }
  // Wait until kiosk_1 is connected to the signaling server before tapping.
  // After am start the app re-establishes its WebSocket — this takes 1–4s.
  // Tapping before the WS is ready results in the call message being silently dropped.
  await waitForKioskOnline('kiosk_1', 12000);

  // Tap kiosk call button — btn_call_operator center coords from UIAutomator dump
  // Screen: 2560x1600 landscape; button bounds [1340,690][2420,1090] → center (1880,890)
  console.log('[CDP] Tapping kiosk call button...');
  adbTap(1880, 890);
  await sleep(1500);

  // Wait for an incoming call card to appear
  console.log('[CDP] Waiting for incoming call...');
  let callId = null;
  for (let i = 0; i < 60; i++) {  // wait up to 30s
    callId = await cdp.eval(`document.querySelector('.call-item .call-id')?.textContent || ''`);
    if (callId) break;
    // Retry tap every 10s in case kiosk wasn't ready
    if (i === 20) {
      console.log('[CDP] No call yet — retrying wakeup + tap...');
      try {
        execSync(`"${ADB}" shell input keyevent KEYCODE_WAKEUP`, { timeout: 3000 });
        execSync(`"${ADB}" shell am start -W --activity-clear-top -n com.videokiosk.kiosk/.MainActivity`, { timeout: 8000 });
      } catch (_) {}
      await waitForKioskOnline('kiosk_1', 8000);
      adbTap(1880, 890);
    }
    await sleep(500);
  }
  if (!callId) {
    console.error('[CDP] No incoming call appeared in 30 seconds');
    const dbg = await cdp.eval(`document.getElementById('dbg')?.innerText || ''`);
    console.log('[CDP] Debug panel at timeout:\n' + dbg);
    cdp.close(); process.exit(1);
  }
  console.log(`[CDP] Incoming call from: ${callId}`);

  // Bring kiosk app to foreground before accepting so Android allows camera access
  // (Android 9+ blocks camera from background; kiosk may be in background while queued)
  console.log('[CDP] Bringing kiosk to foreground...');
  try {
    // Wake screen first (in case it slept)
    execSync(`"${ADB}" shell input keyevent KEYCODE_WAKEUP`, { timeout: 3000 });
    // -W waits until Activity is fully started/resumed; --activity-clear-top brings it to front
    execSync(`"${ADB}" shell am start -W --activity-clear-top -n com.videokiosk.kiosk/.MainActivity`, { timeout: 8000 });
    log('[ADB] kiosk foregrounded (waited for resume)');
  } catch (e) {
    log(`[ADB] foreground failed: ${e.message}`);
  }
  await sleep(1000);   // small extra buffer after -W confirms Activity resumed

  // Click Accept
  await cdp.eval(`document.getElementById('btnAccept').click()`);
  console.log('[CDP] Clicked Accept');

  // Poll debug panel every 2s for WAIT_MS
  console.log(`[CDP] Monitoring for ${WAIT_MS/1000}s...`);
  for (let t = 0; t < WAIT_MS; t += 2000) {
    await sleep(2000);
    const dbg   = await cdp.eval(`document.getElementById('dbg')?.innerText || ''`);
    const state = await cdp.eval(`document.getElementById('iceStatus')?.textContent || ''`);
    const vidW  = await cdp.eval(`document.getElementById('remoteVideo')?.videoWidth || 0`);
    const vidH  = await cdp.eval(`document.getElementById('remoteVideo')?.videoHeight || 0`);
    console.log(`[t+${t+2000}ms] iceStatus="${state}" video=${vidW}x${vidH}`);
    // Print new debug lines
    const lines = dbg.split('\n').filter(l => l.trim());
    lines.forEach(l => console.log('  DBG: ' + l));

    if (state && state.includes('🟢')) {
      console.log('[CDP] ICE CONNECTED! Video should be flowing.');
    }
  }

  // Final state
  const finalDbg = await cdp.eval(`document.getElementById('dbg')?.innerText || ''`);
  console.log('\n[CDP] === FINAL DEBUG PANEL ===\n' + finalDbg);

  const vidW = await cdp.eval(`document.getElementById('remoteVideo')?.videoWidth || 0`);
  const vidH = await cdp.eval(`document.getElementById('remoteVideo')?.videoHeight || 0`);
  console.log(`[CDP] Video dimensions: ${vidW}x${vidH}`);
  console.log(`[CDP] ${vidW > 0 ? '✅ VIDEO IS FLOWING' : '❌ NO VIDEO'}`);

  cdp.close();
  process.exit(0);
}

main().catch(e => { console.error('[CDP] Fatal:', e); process.exit(1); });
