import test from 'node:test';
import assert from 'node:assert/strict';
import dgram from 'node:dgram';
import { decodeOscMessage, encodeOscMessage } from '../src/protocols/osc.js';
import { createDefaultState, setByPath, cloneState } from '../src/core/state.js';
import { M32Driver } from '../src/protocols/m32.js';
import { M32Emulator } from '../src/protocols/m32-emulator.js';

test('OSC round trip', () => {
  const packet = encodeOscMessage('/ch/01/fdr', [-12.5]);
  assert.deepEqual(decodeOscMessage(packet), { address: '/ch/01/fdr', args: [-12.5] });
});

test('OSC string and integer arguments', () => {
  const packet = encodeOscMessage('/label', ['StagePulse', 12]);
  assert.deepEqual(decodeOscMessage(packet), { address: '/label', args: ['StagePulse', 12] });
});

test('central state has M32 blocks', () => {
  const state = createDefaultState();
  assert.equal(state.channels.length, 40);
  assert.equal(state.buses.length, 16);
  assert.equal(state.matrices.length, 6);
  assert.equal(state.dcas.length, 8);
  assert.equal(state.muteGroups.length, 6);
  assert.equal(state.fx.length, 8);
});

test('setByPath creates nested values', () => {
  const state = createDefaultState();
  setByPath(state, 'routing.aes50.a.1', 'local:1');
  assert.equal(state.routing.aes50.a['1'], 'local:1');
});

test('cloneState is independent', () => {
  const state = createDefaultState();
  const clone = cloneState(state);
  clone.channels[0].fader = -12;
  assert.equal(state.channels[0].fader, 0);
});

test('M32 driver exposes expected live surface', () => {
  const driver = new M32Driver({ host: '127.0.0.1', port: 14023, localPort: 14024, autoRefresh: false });
  assert.equal(driver.host, '127.0.0.1');
  assert.equal(driver.port, 14023);
  driver.close();
});

test('M32 emulator starts and echoes OSC state', async () => {
  const emulator = new M32Emulator({ host: '127.0.0.1', port: 14023 });
  await emulator.start();
  emulator.set('/ch/01/fdr', 0.75);
  const client = dgram.createSocket('udp4');
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { client.close(); reject(new Error('emulator response timeout')); }, 1000);
    client.on('message', (message) => {
      try {
        const packet = decodeOscMessage(message);
        assert.equal(packet.address, '/ch/01/fdr');
        assert.equal(packet.args[0], 0.75);
        clearTimeout(timer);
        client.close();
        resolve();
      } catch (error) {
        clearTimeout(timer);
        client.close();
        reject(error);
      }
    });
    client.bind(14025, '127.0.0.1', () => {
      const ping = encodeOscMessage('/node', ['/ch/01/fdr']);
      client.send(ping, 14023, '127.0.0.1');
    });
  });
  emulator.close();
});
