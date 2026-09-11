import http from 'node:http';
import { URL } from 'node:url';
import { M32Driver } from '../protocols/m32.js';
import { createDefaultState, setByPath } from '../core/state.js';

const HOST = process.env.HOST || '127.0.0.1';
const PORT = Number(process.env.PORT || 3000);

const state = createDefaultState();
let driver = null;
const clients = new Set();

function broadcast(event) {
  const data = `data: ${JSON.stringify(event)}\n\n`;
  for (const res of clients) res.write(data);
}

function applyFeedback(packet) {
  state.rawFeedback.push(packet);
  if (state.rawFeedback.length > 5000) state.rawFeedback.shift();
  state.lastRxAt = Date.now();
  setByPath(state, ['feedback', packet.address.replace(/^\//, '').replaceAll('/', '.')], packet.args);
  broadcast({ type: 'feedback', ...packet });
}

function attachDriver(instance) {
  instance.on('feedback', applyFeedback);
  instance.on('connected', () => { state.connected = true; broadcast({ type: 'connection', connected: true }); });
  instance.on('disconnected', () => { state.connected = false; broadcast({ type: 'connection', connected: false }); });
  instance.on('protocolError', (error) => broadcast({ type: 'error', error: error.message }));
  instance.on('error', (error) => broadcast({ type: 'error', error: error.message }));
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(payload);
}

function serveStatic(req, res) {
  const path = new URL(req.url, `http://${req.headers.host || 'localhost'}`).pathname;
  const files = {
    '/': ['text/html; charset=utf-8', new URL('../../public/index.html', import.meta.url)],
    '/index.html': ['text/html; charset=utf-8', new URL('../../public/index.html', import.meta.url)],
    '/app.js': ['text/javascript; charset=utf-8', new URL('../../public/app.js', import.meta.url)],
    '/style.css': ['text/css; charset=utf-8', new URL('../../public/style.css', import.meta.url)],
  };
  const entry = files[path];
  if (!entry) return json(res, 404, { error: 'Not found' });
  import('node:fs/promises').then(({ readFile }) => readFile(entry[1])).then((data) => {
    res.writeHead(200, { 'content-type': entry[0], 'cache-control': 'no-store' });
    res.end(data);
  }).catch(() => json(res, 500, { error: 'Read failure' }));
}

function body(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => { raw += chunk; });
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}); } catch (error) { reject(error); }
    });
    req.on('error', reject);
  });
}

async function handle(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  if (req.method === 'GET' && url.pathname === '/health') {
    return json(res, 200, { ok: true, connected: state.connected, model: state.model });
  }

  if (req.method === 'GET' && url.pathname === '/api/state') {
    return json(res, 200, state);
  }

  if (req.method === 'GET' && url.pathname === '/api/events') {
    res.writeHead(200, {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-cache, no-store, must-revalidate',
      connection: 'keep-alive',
      'access-control-allow-origin': '*',
    });
    res.write(`data: ${JSON.stringify({ type: 'state', state })}\n\n`);
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }

  if (req.method === 'POST' && url.pathname === '/api/connect') {
    try {
      const input = await body(req);
      if (!input.host) return json(res, 400, { error: 'host is required' });
      if (driver) driver.close();
      driver = new M32Driver({ host: input.host, port: input.port });
      attachDriver(driver);
      await driver.connect();
      state.connected = true;
      state.info.host = input.host;
      return json(res, 200, { ok: true, message: `Connected to ${input.host}` });
    } catch (error) {
      state.connected = false;
      return json(res, 500, { error: error.message });
    }
  }

  if (req.method === 'POST' && url.pathname === '/api/disconnect') {
    driver?.close();
    driver = null;
    state.connected = false;
    return json(res, 200, { ok: true });
  }

  if (req.method === 'POST' && url.pathname === '/api/command') {
    try {
      const input = await body(req);
      if (!driver) return json(res, 409, { error: 'M32 is not connected' });
      const args = input.args || {};
      const commands = {
        setChannelFader: () => driver.setChannelFader(args.id, args.value),
        setChannelMute: () => driver.setChannelMute(args.id, args.on),
        setChannelPan: () => driver.setChannelPan(args.id, args.value),
        setChannelGain: () => driver.setChannelGain(args.id, args.value),
        setChannelPhantom: () => driver.setChannelPhantom(args.id, args.on),
        setChannelPolarity: () => driver.setChannelPolarity(args.id, args.on),
        setChannelHpf: () => driver.setChannelHpf(args.id, args.frequency),
        setChannelEqBand: () => driver.setChannelEqBand(args.id, args.band, args.options),
        setBusSend: () => driver.setBusSend(args.channel, args.bus, args.options),
        setBusFader: () => driver.setBusFader(args.id, args.value),
        setBusMute: () => driver.setBusMute(args.id, args.on),
        setMatrixFader: () => driver.setMatrixFader(args.id, args.value),
        setDcaFader: () => driver.setDcaFader(args.id, args.value),
        setDcaMute: () => driver.setDcaMute(args.id, args.on),
        setMainFader: () => driver.setMainFader(args.value),
        setMainMute: () => driver.setMainMute(args.on),
        setMonoFader: () => driver.setMonoFader(args.value),
        setParam: () => driver.setParam(args.address, ...(args.values || [])),
        refresh: () => driver.refresh(),
      };
      const fn = commands[input.command];
      if (!fn) return json(res, 400, { error: `Unknown command: ${input.command}` });
      await fn();
      return json(res, 200, { ok: true });
    } catch (error) {
      return json(res, 500, { error: error.message });
    }
  }

  if (req.method === 'GET') return serveStatic(req, res);
  return json(res, 405, { error: 'Method not allowed' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((error) => json(res, 500, { error: error.message }));
});

server.listen(PORT, HOST, () => {
  console.log(`StagePulseMix listening on http://${HOST}:${PORT}`);
});

const shutdown = () => {
  driver?.close();
  server.close(() => process.exit(0));
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
