import dgram from 'node:dgram';
import { EventEmitter } from 'node:events';
import { decodeOscMessage, encodeOscMessage, DEFAULT_OSC_PORT, DEFAULT_LOCAL_PORT } from './osc.js';

export const M32_CAPABILITIES = Object.freeze({
  inputChannels: 32,
  inputs: 40,
  auxInputs: 8,
  mixBuses: 16,
  mixOutputs: 25,
  matrices: 6,
  dcas: 8,
  fxReturns: 8,
  inputEqBands: 4,
  busEqBands: 6,
  matrixEqBands: 6,
  geqBands: 31,
  aes50Ports: 2,
  aes50ChannelsPerPort: 48,
  ultranetChannels: 16,
});

const clamp = (v, min, max) => Math.min(max, Math.max(min, v));
const channelPath = (n, suffix) => `/ch/${String(n).padStart(2, '0')}/${suffix}`;
const busPath = (n, suffix) => `/bus/${String(n).padStart(2, '0')}/${suffix}`;
const matrixPath = (n, suffix) => `/mtx/${String(n).padStart(2, '0')}/${suffix}`;

export class M32Driver extends EventEmitter {
  constructor({ host, port = DEFAULT_OSC_PORT, localPort = DEFAULT_LOCAL_PORT, refreshMs = 9000, autoRefresh = true, meterIntervalMs = 100 } = {}) {
    super();
    if (!host) throw new Error('M32 host is required');
    this.host = host;
    this.port = port;
    this.localPort = localPort;
    this.refreshMs = refreshMs;
    this.autoRefresh = autoRefresh;
    this.meterIntervalMs = meterIntervalMs;
    this.socket = null;
    this.refreshTimer = null;
    this.meterTimer = null;
    this.connected = false;
    this.rawFeedback = [];
    this.lastRxAt = null;
  }

  async connect() {
    if (this.socket) return;
    this.socket = dgram.createSocket('udp4');
    this.socket.on('message', (message, rinfo) => this.#onPacket(message, rinfo));
    await new Promise((resolve, reject) => {
      const onError = (error) => { this.socket?.off('listening', onListening); reject(error); };
      const onListening = () => { this.socket?.off('error', onError); resolve(); };
      this.socket.once('error', onError);
      this.socket.once('listening', onListening);
      this.socket.bind(this.localPort);
    });
    this.connected = true;
    this.#beginTimers();
    await this.refresh();
    this.emit('connected');
  }

  async refresh() {
    this.#requireSocket();
    await this.send('/xremote', []);
    for (const address of ['/info', '/status', '/config']) await this.send(address, []);
    await this.send('/-stat/chfaderbank', []);
    await this.send('/-stat/grpfaderbank', []);
    await this.send('/-stat/sendsonfader', []);
    await this.subscribeMeters(true);
  }

  async subscribeMeters(enable = true) {
    for (const group of ['/meters/6', '/meters/7', '/meters/12']) await this.send('/meters', [group, enable ? 1 : 0]);
  }

  async setChannelFader(channel, value) { return this.set(channelPath(this.#ch(channel), 'fdr'), clamp(value, -90, 10)); }
  async setChannelMute(channel, on) { return this.set(channelPath(this.#ch(channel), 'mix/on'), !!on); }
  async setChannelPan(channel, value) { return this.set(channelPath(this.#ch(channel), 'pan'), clamp(value, -1, 1)); }
  async setChannelGain(channel, value) { return this.set(channelPath(this.#ch(channel), 'preamp/gain'), clamp(value, -12, 60)); }
  async setChannelPhantom(channel, on) { return this.set(channelPath(this.#ch(channel), 'preamp/phantom'), !!on); }
  async setChannelPolarity(channel, on) { return this.set(channelPath(this.#ch(channel), 'preamp/invert'), !!on); }
  async setChannelHpf(channel, frequency) { return this.set(channelPath(this.#ch(channel), 'eq/lo/f'), frequency); }
  async setChannelEqBand(channel, band, { on, type, frequency, gain, q } = {}) {
    this.#band(band, 4);
    const base = channelPath(this.#ch(channel), `eq/${band}`);
    const jobs = [];
    if (on !== undefined) jobs.push(this.set(`${base}/on`, !!on));
    if (type !== undefined) jobs.push(this.set(`${base}/type`, type));
    if (frequency !== undefined) jobs.push(this.set(`${base}/f`, frequency));
    if (gain !== undefined) jobs.push(this.set(`${base}/g`, gain));
    if (q !== undefined) jobs.push(this.set(`${base}/q`, q));
    return Promise.all(jobs);
  }

  async setBusSend(channel, bus, { level, on, pan, prepost } = {}) {
    this.#ch(channel); this.#bus(bus);
    const base = channelPath(channel, `mix/${String(bus).padStart(2, '02')}`);
    const jobs = [];
    if (level !== undefined) jobs.push(this.set(`${base}/level`, level));
    if (on !== undefined) jobs.push(this.set(`${base}/on`, !!on));
    if (pan !== undefined) jobs.push(this.set(`${base}/pan`, clamp(pan, -1, 1)));
    if (prepost !== undefined) jobs.push(this.set(`${base}/prepost`, prepost));
    return Promise.all(jobs);
  }

  async setBusFader(bus, value) { return this.set(busPath(this.#bus(bus), 'fdr'), value); }
  async setBusMute(bus, on) { return this.set(busPath(this.#bus(bus), 'on'), !!on); }
  async setMatrixFader(matrix, value) { return this.set(matrixPath(this.#matrix(matrix), 'fdr'), value); }
  async setDcaFader(dca, value) { this.#dca(dca); return this.set(`/dca/${String(dca).padStart(2, '0')}/fdr`, value); }
  async setDcaMute(dca, on) { this.#dca(dca); return this.set(`/dca/${String(dca).padStart(2, '0')}/on`, !!on); }
  async setMainFader(value) { return this.set('/main/st/m', value); }
  async setMainMute(on) { return this.set('/main/st/on', !!on); }
  async setMonoFader(value) { return this.set('/main/m/level', value); }
  async setParam(address, ...args) { return this.set(address, ...args); }

  async set(address, ...args) {
    this.#requireSocket();
    if (!address.startsWith('/')) throw new Error('OSC address must start with /');
    const packet = encodeOscMessage(address, args);
    return new Promise((resolve, reject) => {
      this.socket.send(packet, this.port, this.host, (error) => error ? reject(error) : resolve());
    });
  }

  #onPacket(message, rinfo) {
    let packet;
    try { packet = decodeOscMessage(message); } catch (error) {
      this.emit('protocolError', error);
      return;
    }
    this.lastRxAt = Date.now();
    this.rawFeedback.push({ timestamp: this.lastRxAt, address: packet.address, args: packet.args, peer: `${rinfo.address}:${rinfo.port}` });
    if (this.rawFeedback.length > 5000) this.rawFeedback.shift();
    this.emit('feedback', packet);
  }

  #beginTimers() {
    if (this.autoRefresh) this.refreshTimer = setInterval(() => this.refresh().catch((e) => this.emit('error', e)), this.refreshMs);
    this.meterTimer = setInterval(() => this.subscribeMeters(true).catch(() => {}), Math.max(this.meterIntervalMs, 1000));
  }

  #requireSocket() { if (!this.socket) throw new Error('M32 is not connected'); }
  #ch(n) { return clamp(Number(n), 1, 32); }
  #bus(n) { return clamp(Number(n), 1, 16); }
  #matrix(n) { return clamp(Number(n), 1, 6); }
  #dca(n) { return clamp(Number(n), 1, 8); }
  #band(n, max) { if (Number(n) < 1 || Number(n) > max) throw new Error(`band must be 1..${max}`); }

  close() {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    if (this.meterTimer) clearInterval(this.meterTimer);
    this.refreshTimer = null; this.meterTimer = null;
    if (this.socket) this.socket.close();
    this.socket = null;
    this.connected = false;
    this.emit('disconnected');
  }
}

export default M32Driver;
