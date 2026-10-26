const statusBox = document.getElementById('status');
const byId = (id) => document.getElementById(id);

function setStatus(kind, text) { statusBox.className = kind; statusBox.textContent = text; }

async function call(method, path, body) {
  const response = await fetch(path, {
    method, headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 404) throw new Error('disabled');
  const json = await response.json();
  if (!response.ok) throw new Error(json.message || `HTTP ${response.status}`);
  return json;
}

function fill(tbody, rows, render, emptyText) {
  tbody.replaceChildren();
  if (rows.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 4; td.className = 'empty'; td.textContent = emptyText;
    tr.append(td); tbody.append(tr);
    return;
  }
  for (const row of rows) {
    const tr = document.createElement('tr');
    for (const [text, cls] of render(row)) {
      const td = document.createElement('td');
      if (cls === 'badge') { const span = document.createElement('span'); span.className = `badge ${text}`; span.textContent = text; td.append(span); }
      else { td.textContent = text; if (cls) td.className = cls; }
      tr.append(td);
    }
    tbody.append(tr);
  }
}

const time = (iso) => iso ? new Date(iso).toLocaleTimeString() : '';

async function load(message) {
  try {
    const data = await call('GET', '/api/demo/activity');
    byId('panel').hidden = false; byId('disabled').hidden = true;
    byId('gateway-mode').textContent = data.loseAcknowledgements ? 'accepts, then LOSES the acknowledgement (timeout)' : 'accepts and acknowledges normally';
    byId('toggle-gateway').textContent = data.loseAcknowledgements ? 'Gateway: restore acknowledgements' : 'Gateway: simulate lost acknowledgement';
    fill(byId('confirmations'), data.confirmations, (c) => [[`#${c.booking_id}`], [c.delivery_key], [c.status, 'badge'], [c.attempts, 'num']], 'No confirmations yet.');
    fill(byId('attempts'), data.attempts, (a) => [[`#${a.booking_id}`], [a.attempt_no, 'num'], [a.delivery_key], [a.outcome, 'badge']], 'No delivery attempts yet.');
    fill(byId('gateway'), data.gatewayMessages, (m) => [[m.delivery_key], [m.recipient], [time(m.accepted_at)]], 'Gateway has accepted nothing yet.');
    fill(byId('activity'), data.activity, (a) => [[time(a.happened_at)], [a.kind], [a.message]], 'No activity yet.');
    if (message) setStatus('success', message); else setStatus('idle', '');
  } catch (e) {
    if (e.message === 'disabled') { byId('disabled').hidden = false; byId('panel').hidden = true; setStatus('idle', ''); }
    else setStatus('error', e.message);
  }
}

async function run(label, action) {
  setStatus('loading', `${label}…`);
  try { const result = await action(); await load(`${label}: ${JSON.stringify(result)}`); }
  catch (e) { setStatus('error', `${label} failed: ${e.message}`); }
}

byId('reset').addEventListener('click', () => run('Reset seed data', () => call('POST', '/api/demo/reset')));
byId('dispatch').addEventListener('click', () => run('Dispatch', () => call('POST', '/api/demo/dispatch')));
byId('reload').addEventListener('click', () => load());
byId('toggle-gateway').addEventListener('click', async () => {
  const current = (await call('GET', '/api/demo/status')).loseAcknowledgements;
  run('Gateway mode change', () => call('POST', '/api/demo/gateway', { loseAcknowledgements: !current }));
});

load();
