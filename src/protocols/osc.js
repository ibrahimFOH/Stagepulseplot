// StagePulseMix OSC transport helpers.
// Kept dependency-free so the core protocol code can run in Node and tests.

import dgram from 'node:dgram';

export const DEFAULT_OSC_PORT = 10023;
export const DEFAULT_LOCAL_PORT = 10024;

export function pad4(n) {
  return (n + 3) & ~3;
}

export function encodeOscString(value = '') {
  const bytes = Buffer.from(String(value), 'utf8');
  const out = Buffer.alloc(pad4(bytes.length + 1));
  bytes.copy(out);
  return out;
}

export function encodeOscMessage(address, args = []) {
  if (!address || !address.startsWith('/')) throw new Error('OSC address must start with /');
  const typeTags = ',' + args.map((arg) => {
    if (typeof arg === 'string') return 's';
    if (typeof arg === 'boolean') return 'T';
    if (Number.isInteger(arg)) return 'i';
    if (typeof arg === 'number') return 'f';
    throw new Error(`Unsupported OSC argument type: ${typeof arg}`);
  }).join('');

  const chunks = [encodeOscString(address), encodeOscString(typeTags)];
  for (const arg of args) {
    if (typeof arg === 'string') chunks.push(encodeOscString(arg));
    else if (typeof arg === 'boolean') {}
    else if (Number.isInteger(arg)) {
      const b = Buffer.alloc(4); b.writeInt32BE(arg); chunks.push(b);
    } else {
      const b = Buffer.alloc(4); b.writeFloatBE(arg); chunks.push(b);
    }
  }
  return Buffer.concat(chunks);
}

function readOscString(buf, offset) {
  let end = offset;
  while (end < buf.length && buf[end] !== 0) end += 1;
  if (end >= buf.length) throw new Error('Malformed OSC string');
  const value = buf.subarray(offset, end).toString('utf8');
  return { value, next: pad4(end + 1) };
}

export function decodeOscMessage(buf) {
  const packet = Buffer.isBuffer(buf) ? buf : Buffer.from(buf);
  const a = readOscString(packet, 0);
  const t = readOscString(packet, a.next);
  if (!t.value.startsWith(',')) throw new Error('Malformed OSC type tag string');
  let offset = t.next;
  const args = [];

  for (const tag of t.value.slice(1)) {
    if (tag === 's') {
      const s = readOscString(packet, offset); args.push(s.value); offset = s.next;
    } else if (tag === 'i') {
      if (offset + 4 > packet.length) throw new Error('Malformed OSC int');
      args.push(packet.readInt32BE(offset)); offset += 4;
    } else if (tag === 'f') {
      if (offset + 4 > packet.length) throw new Error('Malformed OSC float');
      args.push(packet.readFloatBE(offset)); offset += 4;
    } else if (tag === 'T') args.push(true);
    else if (tag === 'F') args.push(false);
    else throw new Error(`Unsupported OSC tag: ${tag}`);
  }
  return { address: a.value, args };
}

export class UdpOscTransport {
  constructor({ host, port = DEFAULT_OSC_PORT, localPort = DEFAULT_LOCAL_PORT } = {}) {
    this.host = host;
    this.port = port;
    this.localPort = localPort;
    this.socket = null;
    this.onMessage = null;
  }

  async connect() {
    if (this.socket) return;
    this.socket = dgram.createSocket('udp4');
    this.socket.on('message', (msg, rinfo) => {
      this.onMessage?.(msg, rinfo);
    });
    await new Promise((resolve, reject) => {
      const onError = (err) => { this.socket?.off('listening', onListening); reject(err); };
      const onListening = () => { this.socket?.off('error', onError); resolve(); };
      this.socket.once('error', onError);
      this.socket.once('listening', onListening);
      this.socket.bind(this.localPort);
    });
  }

  send(address, args = []) {
    if (!this.socket) throw new Error('OSC transport is not connected');
    const packet = encodeOscMessage(address, args);
    return new Promise((resolve, reject) => {
      this.socket.send(packet, this.port, this.host, (err) => err ? reject(err) : resolve());
    });
  }

  close() {
    if (!this.socket) return;
    this.socket.close();
    this.socket = null;
  }
}
