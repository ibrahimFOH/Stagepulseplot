import { EventEmitter } from 'node:events';
import { decodeOscMessage, encodeOscMessage, DEFAULT_OSC_PORT } from './osc.js';
import dgram from 'node:dgram';

export class M32Emulator extends EventEmitter {
  constructor({ host = '127.0.0.1', port = DEFAULT_OSC_PORT, localPort = 10025 } = {}) {
    super();
    this.host = host;
    this.port = port;
    this.localPort = localPort;
    this.socket = null;
    this.state = new Map();
  }

  async start() {
    if (this.socket) return;
    this.socket = dgram.createSocket('udp4');
    this.socket.on('message', (message, rinfo) => this.#handle(message, rinfo));
    await new Promise((resolve, reject) => {
      const onError = (err) => { this.socket?.off('listening', onListening); reject(err); };
      const onListening = () => { this.socket?.off('error', onError); resolve(); };
      this.socket.once('error', onError);
      this.socket.once('listening', onListening);
      this.socket.bind(this.port, this.host);
    });
    this.emit('started');
  }

  #handle(message, rinfo) {
    let packet;
    try { packet = decodeOscMessage(message); } catch (error) {
      this.emit('error', error);
      return;
    }
    this.emit('message', packet);
    if (packet.address === '/xremote') return;
    if (packet.address === '/info') return this.#reply(rinfo, '/info', ['StagePulseMix M32 Emulator']);
    if (packet.address === '/status') return this.#reply(rinfo, '/status', ['OK']);
    if (packet.address === '/node') return this.#nodeReply(rinfo, packet.args[0]);
    if (packet.args.length) {
      this.state.set(packet.address, packet.args.length === 1 ? packet.args[0] : packet.args.slice());
      this.#reply(rinfo, packet.address, packet.args);
    }
  }

  #nodeReply(rinfo, path) {
    const value = this.state.get(path);
    const args = value === undefined ? [] : Array.isArray(value) ? value : [value];
    this.#reply(rinfo, path, args);
  }

  #reply(rinfo, address, args) {
    const packet = encodeOscMessage(address, args);
    this.socket.send(packet, rinfo.port, rinfo.address);
  }

  set(address, ...args) {
    this.state.set(address, args.length === 1 ? args[0] : args);
    this.emit('state', { address, args });
  }

  close() {
    if (!this.socket) return;
    this.socket.close();
    this.socket = null;
    this.emit('stopped');
  }
}
