// Plain browser JavaScript, no build step. The selected demo identity is sent on every request
// as X-Demo-Attendee; the backend enforces all rules.
const identitySelect = document.getElementById('identity');
const statusBox = document.getElementById('status');
const sessionsBody = document.querySelector('#sessions tbody');
const bookingsBody = document.querySelector('#bookings tbody');
const availableOnly = document.getElementById('available-only');
availableOnly.addEventListener('change', () => refresh());

identitySelect.value = localStorage.getItem('demoIdentity') || 'alice';
identitySelect.addEventListener('change', () => {
  localStorage.setItem('demoIdentity', identitySelect.value);
  refresh();
});

function setStatus(kind, text) {
  statusBox.className = kind;
  statusBox.textContent = text;
}

async function api(method, path, body, extraHeaders = {}) {
  const headers = { 'X-Demo-Attendee': identitySelect.value, ...extraHeaders };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  const json = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = json && json.message ? `${json.message} (${json.code})` : `HTTP ${response.status}`;
    throw new Error(message);
  }
  return json;
}

function cell(text, cls) {
  const td = document.createElement('td');
  td.textContent = text;
  if (cls) td.className = cls;
  return td;
}

async function loadSessions() {
  const page = await api('GET', `/api/sessions?page=0&size=20&availableOnly=${availableOnly.checked}`);
  sessionsBody.replaceChildren();
  for (const s of page.items) {
    const tr = document.createElement('tr');
    tr.dataset.sessionId = s.id;
    if (s.availablePlaces <= 0) tr.className = 'full';
    tr.append(cell(s.title), cell(s.room), cell(s.capacity, 'num'), cell(s.activeBookings, 'num'), cell(s.availablePlaces, 'num'));
    const action = document.createElement('td');
    const button = document.createElement('button');
    button.textContent = 'Book';
    button.disabled = s.availablePlaces <= 0;
    button.addEventListener('click', () => book(s));
    action.append(button);
    tr.append(action);
    sessionsBody.append(tr);
  }
}

async function loadBookings() {
  const bookings = await api('GET', '/api/bookings');
  bookingsBody.replaceChildren();
  if (bookings.length === 0) {
    const tr = document.createElement('tr');
    tr.append(cell('No active bookings.', 'empty'));
    tr.firstChild.colSpan = 4;
    bookingsBody.append(tr);
    return;
  }
  for (const b of bookings) {
    const tr = document.createElement('tr');
    tr.dataset.bookingId = b.id;
    tr.append(cell(`#${b.id}`), cell(b.sessionTitle), cell(new Date(b.createdAt).toLocaleTimeString()));
    const action = document.createElement('td');
    const button = document.createElement('button');
    button.className = 'secondary';
    button.textContent = 'Cancel';
    button.addEventListener('click', () => cancel(b));
    action.append(button);
    tr.append(action);
    bookingsBody.append(tr);
  }
}

async function refresh(successText) {
  setStatus('loading', 'Loading…');
  try {
    await Promise.all([loadSessions(), loadBookings()]);
    if (successText) setStatus('success', successText); else setStatus('idle', '');
  } catch (e) {
    setStatus('error', `Could not load data: ${e.message}`);
  }
}

async function book(session) {
  setStatus('loading', `Booking '${session.title}'…`);
  try {
    // A fresh Idempotency-Key per click; a retry of the same click would reuse it.
    const booking = await api('POST', '/api/bookings', { sessionId: session.id }, { 'Idempotency-Key': crypto.randomUUID() });
    await refresh(`Booked '${booking.sessionTitle}' (booking #${booking.id}).`);
  } catch (e) {
    setStatus('error', `Booking failed: ${e.message}`);
    loadSessions().catch(() => {});
  }
}

async function cancel(booking) {
  setStatus('loading', `Cancelling booking #${booking.id}…`);
  try {
    await api('DELETE', `/api/bookings/${booking.id}`);
    await refresh(`Cancelled booking #${booking.id}.`);
  } catch (e) {
    setStatus('error', `Cancellation failed: ${e.message}`);
  }
}

refresh();
