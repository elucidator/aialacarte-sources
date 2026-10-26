const $ = id => document.getElementById(id);
let page = 0;
const size = 20;
let view = 'sessions';
let busy = false;
let refreshVersion = 0;
// Retain the same key after a network error so a retry cannot create a second booking.
const requestKeys = new Map();
function status(message, kind = '') { $('status').textContent = message; $('status').className = kind; }
async function api(path, options = {}, attendee = $('identity').value) {
  const response = await fetch(path, {...options, headers: {'Content-Type': 'application/json', 'X-Demo-Attendee': attendee, ...options.headers}});
  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) { const error = new Error(data.message || 'Request failed.'); error.http = response.status; throw error; }
  return data;
}
function cell(row, text) { const td = document.createElement('td'); td.textContent = text; row.append(td); return td; }
function button(label, action, disabled = false) {
  const element = document.createElement('button'); element.textContent = label; element.disabled = disabled; element.onclick = action; return element;
}
async function refresh(message = '') {
  const version = ++refreshVersion;
  const attendee = $('identity').value;
  status('Loading…');
  try {
    const [sessions, bookings] = await Promise.all([api(`/api/sessions?page=${page}&size=${size}`, {}, attendee), api('/api/bookings', {}, attendee)]);
    if (version !== refreshVersion) return;
    $('sessions').replaceChildren();
    const owned = new Set(bookings.map(b => b.sessionId));
    for (const session of sessions.items) {
      const row = document.createElement('tr');
      [session.title, session.room, session.capacity, session.activeBookings, session.availablePlaces].forEach(value => cell(row, value));
      cell(row, '').append(button(owned.has(session.id) ? 'Booked' : session.availablePlaces === 0 ? 'Full' : 'Book', () => book(session.id), busy || owned.has(session.id) || session.availablePlaces === 0));
      $('sessions').append(row);
    }
    $('previous').disabled = busy || page === 0;
    $('next').disabled = busy || (page + 1) * size >= sessions.totalElements;
    $('page-label').textContent = `Page ${page + 1} · ${sessions.totalElements} sessions`;
    $('bookings').replaceChildren();
    if (!bookings.length) $('bookings').textContent = 'You have no active bookings.';
    for (const booking of bookings) {
      const row = document.createElement('div'); row.className = 'booking';
      const title = document.createElement('span'); title.textContent = booking.title;
      row.append(title, button('Cancel', () => cancel(booking.id, booking.sessionId), busy)); $('bookings').append(row);
    }
    status(message || `Ready for ${attendee === 'alice' ? 'Alice' : 'Bob'}.`, message ? 'success' : '');
  } catch (error) { if (version === refreshVersion) status(error.message, 'error'); }
}
function setBusy(value) {
  busy = value; $('identity').disabled = value;
  if (value) document.querySelectorAll('main button').forEach(element => { element.disabled = true; });
  else { $('sessions-tab').disabled = false; $('bookings-tab').disabled = false; }
}
async function book(sessionId) {
  if (busy) return;
  const attendee = $('identity').value, scope = `${attendee}:${sessionId}`;
  if (!requestKeys.has(scope)) requestKeys.set(scope, crypto.randomUUID());
  setBusy(true); status('Booking your place…');
  try {
    const booking = await api('/api/bookings', {method: 'POST', headers: {'Idempotency-Key': requestKeys.get(scope)}, body: JSON.stringify({sessionId})}, attendee);
    requestKeys.delete(scope); setBusy(false); await refresh(booking.status === 'ACTIVE' ? 'Your place is booked.' : 'That booking was cancelled. Select Book to make a new request.');
  } catch (error) {
    if (error.http) requestKeys.delete(scope);
    setBusy(false); await refresh(); status(error.message, 'error');
  }
}
async function cancel(id, sessionId) {
  if (busy) return;
  setBusy(true); status('Cancelling…');
  try { await api(`/api/bookings/${id}`, {method: 'DELETE'}); requestKeys.delete(`${$('identity').value}:${sessionId}`); setBusy(false); await refresh('Booking cancelled. The place is available again.'); }
  catch (error) { setBusy(false); await refresh(); status(error.message, 'error'); }
}
function show(next) {
  view = next; $('sessions-view').hidden = view !== 'sessions'; $('bookings-view').hidden = view !== 'bookings';
  $('sessions-tab').setAttribute('aria-pressed', view === 'sessions'); $('bookings-tab').setAttribute('aria-pressed', view === 'bookings'); refresh();
}
$('identity').onchange = () => { page = 0; refresh(); };
$('sessions-tab').onclick = () => show('sessions'); $('bookings-tab').onclick = () => show('bookings');
$('previous').onclick = () => { page--; refresh(); }; $('next').onclick = () => { page++; refresh(); };
refresh();
