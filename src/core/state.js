export function createDefaultState() {
  return {
    connected: false,
    model: 'M32',
    info: {},
    channels: Array.from({ length: 40 }, (_, i) => ({ id: i + 1, fader: 0, mute: false, pan: 0, name: `CH ${i + 1}` })),
    buses: Array.from({ length: 16 }, (_, i) => ({ id: i + 1, fader: 0, mute: false, linked: false })),
    matrices: Array.from({ length: 6 }, (_, i) => ({ id: i + 1, fader: 0, mute: false, linked: false })),
    dcas: Array.from({ length: 8 }, (_, i) => ({ id: i + 1, fader: 0, mute: false, assignmentMask: 0 })),
    muteGroups: Array.from({ length: 6 }, (_, i) => ({ id: i + 1, on: false, assignmentMask: 0 })),
    main: { stereo: { fader: 0, mute: false }, mono: { fader: 0, mute: false }, sub: { on: false } },
    fx: Array.from({ length: 8 }, (_, i) => ({ id: i + 1, type: null, bypass: false, source: null, params: {} })),
    routing: { raw: {}, inputs: {}, outputs: {}, aes50: { a: {}, b: {} }, card: {}, usb: {}, aux: {} },
    monitor: { source: null, volume: 0, dim: false, mono: false, solo: false },
    talkback: { a: {}, b: {} },
    recorder: { state: 'stopped', position: 0, source: null, channels: 2 },
    scenes: { current: null, items: [] },
    snippets: { current: null, items: [] },
    cues: { current: null, items: [] },
    surface: { bank: 0, selectedChannel: null, sendsOnFader: null },
    meters: {},
    rawFeedback: [],
    lastRxAt: null,
  };
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
