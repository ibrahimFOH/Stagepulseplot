// Vendor-neutral mixer driver contract for StagePulseMix.
// Vendor drivers implement this surface so the server/UI stay mixer-agnostic.

export class MixerDriver {
  constructor(options = {}) {
    this.options = { ...options };
    this.vendor = 'unknown';
    this.model = options.model || null;
    this.connected = false;
  }

  async connect() { throw new Error('connect() is not implemented'); }
  close() { this.connected = false; }

  async setParam(address, ...args) { throw new Error(`setParam() is not implemented: ${address}`); }

  async setChannelFader(channel, value) { return this.setParam(`/ch/${String(channel).padStart(2,'0')}/fdr`, value); }
  async setChannelMute(channel, on) { return this.setParam(`/ch/${String(channel).padStart(2,'0')}/mix/on`, !!on); }
  async setChannelPan(channel, value) { return this.setParam(`/ch/${String(channel).padStart(2,'0')}/pan`, value); }

  capabilities() { return {}; }
}

export function isMixerDriver(value) {
  return value && typeof value.connect === 'function' && typeof value.close === 'function' && typeof value.setParam === 'function';
}

export default MixerDriver;
