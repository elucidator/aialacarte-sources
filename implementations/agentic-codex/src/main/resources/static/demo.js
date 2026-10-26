const $ = id => document.getElementById(id);
async function request(path, body) {
  const response = await fetch('/api/demo/' + path, body === undefined ? {} : {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
  if (response.status === 404) throw new Error('Local demo controls are disabled. Start with the local-demo profile.');
  const result = await response.json(); if (!response.ok) throw new Error(result.message || 'Demo request failed.'); return result;
}
function status(text, kind = '') { $('status').textContent = text; $('status').className = kind; }
async function refresh(message) {
  try {
    const data = await request('activity'); $('controls').hidden = false; $('mode').value = data.mode;
    $('counts').textContent = `${data.pending} pending · ${data.attempts.length} recent attempts · ${data.accepted.length} accepted confirmations`;
    for (const name of ['attempts','accepted','events']) $(name).textContent = data[name].length ? JSON.stringify(data[name], null, 2) : 'None yet.';
    status(message || 'Activity loaded.', message ? 'success' : '');
  } catch (error) { status(error.message, 'error'); }
}
async function action(path, body, message) {
  document.querySelectorAll('button,select').forEach(e => e.disabled = true); status('Working…');
  try { await request(path, body); await refresh(message); }
  catch (error) { status(error.message, 'error'); }
  finally { document.querySelectorAll('button,select').forEach(e => e.disabled = false); }
}
$('mode').onchange = () => action('mode', {mode:$('mode').value}, 'Gateway mode updated.');
$('dispatch').onclick = () => action('dispatch', {}, 'Dispatch finished. Pending confirmations can be retried.');
$('refresh').onclick = () => refresh();
$('reset').onclick = () => { if (confirm('Restore the three seed sessions and clear all demo bookings and delivery activity?')) action('reset', {}, 'Seed data restored.'); };
refresh();
