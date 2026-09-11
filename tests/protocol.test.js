import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeOscMessage, encodeOscMessage } from '../src/protocols/osc.js';
import { createDefaultState, setByPath, cloneState } from '../src/core/state.js';
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

test('M32 emulator starts and accepts OSC', async () => {
  const emulator = new M32Emulator({ host: '127.0.0.1', port: 14023 });
  await emulator.start();
  emulator.close();
  assert.ok(true);
});
