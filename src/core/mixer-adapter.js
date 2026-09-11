import { isMixerDriver } from './mixer-driver.js';

export class MixerAdapter {
  constructor(driver = null) {
    this.driver = null;
    if (driver) this.setDriver(driver);
  }

  setDriver(driver) {
    if (!isMixerDriver(driver)) throw new TypeError('Invalid mixer driver');
    this.driver = driver;
    return driver;
  }

  clearDriver() {
    this.driver?.close?.();
    this.driver = null;
  }

  requireDriver() {
    if (!this.driver) throw new Error('Mixer bağlantısı yok');
    return this.driver;
  }

  connect(options) {
    return this.requireDriver().connect(options);
  }

  close() {
    this.driver?.close?.();
  }

  command(action, args = {}) {
    const d = this.requireDriver();
    const map = {
      setChannelFader: () => d.setChannelFader(args.channel ?? args.id, args.value),
      setChannelMute: () => d.setChannelMute(args.channel ?? args.id, args.on ?? args.value),
      setChannelPan: () => d.setChannelPan(args.channel ?? args.id, args.value),
      setChannelGain: () => d.setChannelGain?.(args.channel ?? args.id, args.value),
      setChannelPhantom: () => d.setChannelPhantom?.(args.channel ?? args.id, args.on ?? args.value),
      setChannelPolarity: () => d.setChannelPolarity?.(args.channel ?? args.id, args.on ?? args.value),
      setChannelEqBand: () => d.setChannelEqBand?.(args.channel ?? args.id, args.band, args),
      setBusSend: () => d.setBusSend?.(args.channel, args.bus, args),
      setBusFader: () => d.setBusFader?.(args.bus, args.value),
      setBusMute: () => d.setBusMute?.(args.bus, args.on ?? args.value),
      setMatrixFader: () => d.setMatrixFader?.(args.matrix, args.value),
      setDcaFader: () => d.setDcaFader?.(args.dca, args.value),
      setDcaMute: () => d.setDcaMute?.(args.dca, args.on ?? args.value),
      setMainFader: () => d.setMainFader?.(args.value),
      setMainMute: () => d.setMainMute?.(args.on ?? args.value),
      setMonoFader: () => d.setMonoFader?.(args.value),
      setParam: () => d.setParam(args.address, ...(Array.isArray(args.args) ? args.args : [])),
    };
    if (!map[action]) throw new Error(`Desteklenmeyen komut: ${action}`);
    const result = map[action]();
    if (result === undefined) throw new Error(`Komut ${action} mevcut driver tarafından desteklenmiyor`);
    return result;
  }
}

export default MixerAdapter;
