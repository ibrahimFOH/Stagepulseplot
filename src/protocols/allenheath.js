// StagePulseMix Allen & Heath driver placeholder.
// Deliberately isolated so Midas/X32 can be validated before adding vendor specifics.

export class AllenHeathDriver {
  constructor(options = {}) {
    this.options = { ...options };
    this.connected = false;
  }

  async connect() {
    throw new Error('Allen & Heath support is not enabled in the Midas/X32 release candidate');
  }

  close() {
    this.connected = false;
  }
}

export default AllenHeathDriver;
