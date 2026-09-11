import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MixerState } from '../core/state.js';
import { M32Driver } from '../protocols/m32.js';
import { AllenHeathDriver } from '../protocols/allenheath.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.resolve(__dirname, '../../public');
const state = new MixerState(40);
let driver = null;
const clients = new Set();

const json = (res, code, body) => {
  const b = Buffer.from(JSON.stringify(body));
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': b.length,
  });
  res.end(b);
};

const snapshot = () => state.toJSON();
const broadcast = () => {
  const payload = `data: ${JSON.stringify(snapshot())}\n\n`;
  for (const res of clients) res.write(payload);
};

function attachDriverEvents(d) {
  d.on?.('connected', () => { state.setConnection({ status: 'connected', vendor: d.vendor || 'midas', model: d.model || 'M32' }); broadcast(); });
  d.on?.('disconnected', () => { state.setConnection({ status: 'disconnected' }); broadcast(); });
  d.on?.('feedback', (packet) => { state.data.rawFeedback.push({ timestamp: Date.now(), ...packet }); if (state.data.rawFeedback.length > 5000) state.data.rawFeedback.shift(); broadcast(); });
  d.on?.('protocolError', (error) => { state.setConnection({ status: 'protocol-error', error: error.message }); broadcast(); });
}

const commands = {
  setChannelFader: (d, c) => d.setChannelFader(c.channel ?? c.id, c.value),
  setChannelMute: (d, c) => d.setChannelMute(c.channel ?? c.id, c.on ?? c.value),
  setChannelPan: (d, c) => d.setChannelPan(c.channel ?? c.id, c.value),
  setChannelGain: (d, c) => d.setChannelGain?.(c.channel ?? c.id, c.value),
  setChannelPhantom: (d, c) => d.setChannelPhantom?.(c.channel ?? c.id, c.on ?? c.value),
  setChannelPolarity: (d, c) => d.setChannelPolarity?.(c.channel ?? c.id, c.on ?? c.value),
  setChannelEqBand: (d, c) => d.setChannelEqBand?.(c.channel ?? c.id, c.band, c),
  setBusSend: (d, c) => d.setBusSend?.(c.channel, c.bus, c),
  setBusFader: (d, c) => d.setBusFader?.(c.bus, c.value),
  setBusMute: (d, c) => d.setBusMute?.(c.bus, c.on ?? c.value),
  setMatrixFader: (d, c) => d.setMatrixFader?.(c.matrix, c.value),
  setDcaFader: (d, c) => d.setDcaFader?.(c.dca, c.value),
  setDcaMute: (d, c) => d.setDcaMute?.(c.dca, c.on ?? c.value),
  setMainFader: (d, c) => d.setMainFader?.(c.value),
  setMainMute: (d, c) => d.setMainMute?.(c.on ?? c.value),
  setMonoFader: (d, c) => d.setMonoFader?.(c.value),
  setParam: (d, c) => d.setParam(c.address, ...(Array.isArray(c.args) ? c.args : [])),
};

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://localhost');

  if (url.pathname === '/health') return json(res, 200, { ok: true, version: '1.0.0', connected: state.data.connected, model: state.data.model });
  if (url.pathname === '/api/state') return json(res, 200, snapshot());

  if (url.pathname === '/api/events') {
    res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
    clients.add(res);
    res.write(`data: ${JSON.stringify(snapshot())}\n\n`);
    req.on('close', () => clients.delete(res));
    return;
  }

  if (url.pathname === '/api/connect' && req.method === 'POST') {
    try {
      if (driver) driver.close();
      const c = JSON.parse(await readBody(req));
      const vendor = c.vendor || 'midas';
      if (vendor === 'midas') {
        state.resetMidas(c.channelCount || 40);
        driver = new M32Driver({ host: c.host, port: Number(c.port) || 10023, localPort: Number(c.localPort) || 10024, model: c.model || 'M32' });
      } else {
        driver = new AllenHeathDriver(c);
      }
      attachDriverEvents(driver);
      await driver.connect();
      return json(res, 200, { ok: true, vendor, model: driver.model || c.model || null });
    } catch (error) {
      state.setConnection({ status: 'error', error: error.message });
      broadcast();
      return json(res, 400, { ok: false, error: error.message });
    }
  }

  if (url.pathname === '/api/disconnect' && req.method === 'POST') {
    driver?.close?.();
    driver = null;
    state.setConnection({ status: 'disconnected' });
    broadcast();
    return json(res, 200, { ok: true });
  }

  if (url.pathname === '/api/command' && req.method === 'POST') {
    try {
      if (!driver) throw new Error('Mixer bağlantısı yok');
      const c = JSON.parse(await readBody(req));
      const fn = commands[c.action];
      if (!fn) throw new Error(`Desteklenmeyen komut: ${c.action}`);
      await fn(driver, c.args || {});
      broadcast();
      return json(res, 200, { ok: true });
    } catch (error) {
      return json(res, 400, { ok: false, error: error.message });
    }
  }

  const file = url.pathname === '/' ? path.join(publicDir, 'index.html') : path.join(publicDir, url.pathname.replace(/^\//, ''));
  if (!file.startsWith(publicDir)) return json(res, 403, { error: 'forbidden' });
  fs.readFile(file, (error, data) => {
    if (error) return json(res, 404, { error: 'not found' });
    const ext = path.extname(file);
    const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
    res.writeHead(200, { 'content-type': types[ext] || 'application/octet-stream' });
    res.end(data);
  });
}

http.createServer(handle).listen(Number(process.env.PORT) || 8787, process.env.HOST || '127.0.0.1', () => console.log('StagePulseMix: http://127.0.0.1:8787'));
