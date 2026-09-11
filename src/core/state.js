export function createDefaultState() {
  return {
    connected: false,
    model: 'M32',
    info: {},
    channels: Array.from({ length: 40 }, (_, i) => ({
      id: i + 1,
      fader: 0,
      mute: false,
      solo: false,
      pan: 0,
      name: `CH ${i + 1}`,
      meter: 0,
      gain: 0,
      phantom: false,
      polarity: false,
      hpf: { enabled: false, frequency: 80, slope: 12 },
      delay: { enabled: false, ms: 0 },
      eq: Array.from({ length: 4 }, () => ({ on: false, type: 'PEQ', frequency: 1000, gain: 0, q: 1 })),
      gate: { enabled: false, threshold: -60, range: 60, attack: 0.3, hold: 10, release: 100 },
      gateFilter: { enabled: false, frequency: 1000, q: 1 },
      dynamics: { enabled: false, threshold: 0, ratio: 1, attack: 0.3, hold: 10, release: 100, makeup: 0 },
      insert: { enabled: false, source: null, position: 'post', bypass: false },
      busSends: Array.from({ length: 16 }, (_, bus) => ({ id: bus + 1, level: 0, on: false, pan: 0, prepost: 'post' })),
    })),
    buses: Array.from({ length: 16 }, (_, i) => ({
      id: i + 1,
      fader: 0,
      mute: false,
      linked: false,
      pan: 0,
      eq: Array.from({ length: 6 }, () => ({ on: false, type: 'PEQ', frequency: 1000, gain: 0, q: 1 })),
      dynamics: { enabled: false, threshold: 0, ratio: 1, attack: 0.3, release: 100, makeup: 0 },
      insert: { enabled: false, source: null, bypass: false },
    })),
    matrices: Array.from({ length: 6 }, (_, i) => ({
      id: i + 1,
      fader: 0,
      mute: false,
      linked: false,
      pan: 0,
      eq: Array.from({ length: 6 }, () => ({ on: false, type: 'PEQ', frequency: 1000, gain: 0, q: 1 })),
      dynamics: { enabled: false, threshold: 0, ratio: 1, attack: 0.3, release: 100, makeup: 0 },
    })),
    dcas: Array.from({ length: 8 }, (_, i) => ({ id: i + 1, fader: 0, mute: false, assignmentMask: 0 })),
    muteGroups: Array.from({ length: 6 }, (_, i) => ({ id: i + 1, on: false, assignmentMask: 0 })),
    solo: { mode: 'momentary', activeChannel: null, safeMask: 0 },
    main: {
      stereo: { fader: 0, mute: false, pan: 0 },
      mono: { fader: 0, mute: false },
      sub: { on: false, fader: 0 },
    },
    fx: Array.from({ length: 8 }, (_, i) => ({ id: i + 1, type: null, bypass: false, source: null, params: {} })),
    geq: {
      buses: Array.from({ length: 16 }, (_, i) => ({ id: i + 1, enabled: false, bands: Array(31).fill(0) })),
      main: { enabled: false, bands: Array(31).fill(0) },
    },
    routing: { raw: {}, inputs: {}, outputs: {}, aes50: { a: {}, b: {} }, card: {}, usb: {}, aux: {} },
    monitor: { source: null, volume: 0, dim: false, mono: false, solo: false, delay: 0 },
    talkback: { a: {}, b: {} },
    recorder: { state: 'stopped', position: 0, source: null, channels: 2 },
    card: { state: 'idle', position: 0, channels: 2 },
    usb: { source: null, channels: 2 },
    scenes: { current: null, items: [] },
    snippets: { current: null, items: [] },
    cues: { current: null, items: [] },
    libraries: { channel: [], gate: [], dynamics: [], eq: [], fx: [], snippets: [] },
    surface: { bank: 0, selectedChannel: null, sendsOnFader: null },
    meters: {},
    rawFeedback: [],
    lastRxAt: null,
  };
}

export class MixerState {
  constructor(channelCount = 40) {
    this.data = createDefaultState();
    if (channelCount !== 40) {
      this.data.channels = Array.from({ length: channelCount }, (_, i) => ({ id: i + 1, fader: 0, mute: false, solo: false, pan: 0, name: `CH ${i + 1}`, meter: 0 }));
    }
  }

  resetMidas(channelCount = 40) {
    this.data = createDefaultState();
    if (channelCount !== 40) this.data.channels.length = channelCount;
  }

  setConnection(info = {}) {
    this.data.connected = info.status !== 'error' && info.status !== 'disconnected';
    this.data.info = { ...this.data.info, ...info };
  }

  get channels() { return this.data.channels; }
  set channels(value) { this.data.channels = value; }
  get buses() { return this.data.buses; }
  get dcas() { return this.data.dcas; }
  get rawFeedback() { return this.data.rawFeedback; }

  toJSON() { return structuredClone(this.data); }

  [Symbol.toPrimitive]() { return this.toJSON(); }
}

export function setByPath(root, path, value) {
  const parts = Array.isArray(path) ? path : String(path).split('.').filter(Boolean);
  if (!parts.length) return root;
  let node = root;
  for (let i = 0; i < parts.length - 1; i += 1) {
    const key = parts[i];
    if (node[key] == null || typeof node[key] !== 'object') node[key] = {};
    node = node[key];
  }
  node[parts[parts.length - 1]] = value;
  return root;
}

export function cloneState(state) {
  return structuredClone(state);
}
