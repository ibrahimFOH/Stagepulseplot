const $ = (s) => document.querySelector(s);
const state = {
  connected: false,
  channels: Array.from({ length: 32 }, (_, i) => ({ id: i + 1, fader: 0, mute: false, solo: false, pan: 0, name: `CH ${i + 1}` })),
  buses: Array.from({ length: 16 }, (_, i) => ({ id: i + 1, fader: 0, mute: false })),
  dcas: Array.from({ length: 8 }, (_, i) => ({ id: i + 1, fader: 0, mute: false })),
  main: { stereo: { fader: 0, mute: false } },
  routing: {},
  scenes: {}
};

let eventSource;

function setConnection(ok, text = ok ? 'Bağlı' : 'Bağlantı yok') {
  state.connected = ok;
  $('#connection').textContent = text;
  $('#connect').disabled = ok;
  $('#disconnect').disabled = !ok;
}

async function api(path, options = {}) {
  const res = await fetch(path, { headers: { 'content-type': 'application/json' }, ...options });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
  return body;
}

async function connect() {
  const host = $('#host').value.trim();
  if (!host) throw new Error('X32 IP adresi gerekli');
  const body = await api('/api/connect', { method: 'POST', body: JSON.stringify({ host }) });
  setConnection(true, body.message || 'Bağlı');
  startEvents();
}

async function disconnect() {
  await api('/api/disconnect', { method: 'POST', body: '{}' });
  eventSource?.close();
  setConnection(false);
}

function startEvents() {
  eventSource?.close();
  eventSource = new EventSource('/api/events');
  eventSource.onmessage = ({ data }) => {
    try { applyFeedback(JSON.parse(data)); } catch {}
  };
  eventSource.onerror = () => setConnection(false, 'Bağlantı koptu');
}

function applyFeedback(packet) {
  if (!packet?.address) return;
  const m = packet.address.match(/^\/ch\/(\d{2})\/([^/]+)$/);
  if (m) {
    const i = Number(m[1]) - 1;
    if (state.channels[i]) {
      if (m[2] === 'fdr') state.channels[i].fader = Number(packet.args?.[0] ?? 0);
      if (m[2] === 'pan') state.channels[i].pan = Number(packet.args?.[0] ?? 0);
    }
  }
  renderAll();
}

async function command(command, args = {}) {
  return api('/api/command', { method: 'POST', body: JSON.stringify({ command, args }) });
}

function makeStrip(item, type) {
  const node = $('#strip-template').content.firstElementChild.cloneNode(true);
  node.querySelector('.strip-title').textContent = item.name || `${type} ${item.id}`;
  const fader = node.querySelector('.fader');
  const mute = node.querySelector('.mute');
  const solo = node.querySelector('.solo');
  const pan = node.querySelector('.pan');
  fader.value = item.fader ?? 0;
  pan.value = item.pan ?? 0;
  mute.classList.toggle('on', !!item.mute);
  solo.classList.toggle('on', !!item.solo);
  fader.addEventListener('change', async () => {
    await command(type === 'input' ? 'setChannelFader' : type === 'bus' ? 'setBusFader' : 'setDcaFader', { id: item.id, value: Number(fader.value) });
  });
  mute.addEventListener('click', async () => {
    item.mute = !item.mute;
    await command(type === 'input' ? 'setChannelMute' : type === 'bus' ? 'setBusMute' : 'setDcaMute', { id: item.id, on: item.mute });
    renderAll();
  });
  solo.addEventListener('click', async () => {
    item.solo = !item.solo;
    await command('setSolo', { id: item.id, on: item.solo });
    renderAll();
  });
  if (type === 'input') pan.addEventListener('change', async () => command('setChannelPan', { id: item.id, value: Number(pan.value) }));
  return node;
}

function renderStrips(target, items, type) {
  const el = $(target);
  el.replaceChildren(...items.map((item) => makeStrip(item, type)));
}

function renderAll() {
  renderStrips('#input-strips', state.channels, 'input');
  renderStrips('#bus-strips', state.buses, 'bus');
  renderStrips('#dca-strips', state.dcas, 'dca');
  $('#routing-state').textContent = JSON.stringify(state.routing, null, 2);
  $('#scene-state').textContent = JSON.stringify(state.scenes, null, 2);
}

for (const button of document.querySelectorAll('[data-view]')) {
  button.addEventListener('click', () => {
    document.querySelectorAll('.view').forEach((v) => v.classList.remove('active-view'));
    document.querySelector(`#view-${button.dataset.view}`).classList.add('active-view');
    document.querySelectorAll('[data-view]').forEach((b) => b.classList.remove('active'));
    button.classList.add('active');
  });
}

$('#connect').addEventListener('click', () => connect().catch((e) => setConnection(false, e.message)));
$('#disconnect').addEventListener('click', () => disconnect().catch((e) => setConnection(false, e.message)));

renderAll();
