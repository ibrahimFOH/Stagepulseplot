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
    const d = this.requireDriver();
    return d.connect(options);
  }

  close() {
    this.driver?.close?.();
  }

  command(action, args = {}) {
    const d = this.requireDriver();
    const id = args.channel ?? args.id;
    const value = args.value;
    const boolValue = args.on ?? value;

    const map = {
      setChannelFader: () => d.setChannelFader(id, value),
      setChannelMute: () => d.setChannelMute(id, boolValue),
      setChannelPan: () => d.setChannelPan(id, value),
      setChannelGain: () => d.setChannelGain?.(id, value),
      setChannelPhantom: () => d.setChannelPhantom?.(id, boolValue),
      setChannelPolarity: () => d.setChannelPolarity?.(id, boolValue),
      setChannelHpf: () => d.setChannelHpf?.(id, args.frequency ?? value),
      setChannelEqBand: () => d.setChannelEqBand?.(id, args.band, args),
      setBusSend: () => d.setBusSend?.(args.channel, args.bus, args),
      setBusFader: () => d.setBusFader?.(args.bus, value),
      setBusMute: () => d.setBusMute?.(args.bus, boolValue),
      setMatrixFader: () => d.setMatrixFader?.(args.matrix, value),
      setDcaFader: () => d.setDcaFader?.(args.dca, value),
      setDcaMute: () => d.setDcaMute?.(args.dca, boolValue),
      setMainFader: () => d.setMainFader?.(value),
      setMainMute: () => d.setMainMute?.(boolValue),
      setMonoFader: () => d.setMonoFader?.(value),
      setParam: () => d.setParam(args.address, ...(Array.isArray(args.args) ? args.args : [])),
    };

    const fn = map[action];
    if (!fn) throw new Error(`Desteklenmeyen komut: ${action}`);
    const result = fn();
    if (result === undefined) throw new Error(`Komut ${action} mevcut driver tarafından desteklenmiyor`);
    return result;
  }
}

export default MixerAdapter;
