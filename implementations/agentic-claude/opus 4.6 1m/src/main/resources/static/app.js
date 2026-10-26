const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

function attendee() { return $('#attendee-select').value; }

function showStatus(msg, type) {
    const bar = $('#status-bar');
    bar.textContent = msg;
    bar.className = type;
    bar.classList.remove('hidden');
    setTimeout(() => bar.classList.add('hidden'), 4000);
}

// Navigation
$$('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        $$('.nav-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        $$('.view').forEach(v => v.classList.remove('active'));
        $(`#${btn.dataset.view}-view`).classList.add('active');
        if (btn.dataset.view === 'sessions') loadSessions();
        if (btn.dataset.view === 'bookings') loadBookings();
        if (btn.dataset.view === 'demo') loadTimeoutStatus();
    });
});

$('#attendee-select').addEventListener('change', () => {
    const activeView = $('.nav-btn.active').dataset.view;
    if (activeView === 'sessions') loadSessions();
    if (activeView === 'bookings') loadBookings();
});

// Sessions
async function loadSessions() {
    const loading = $('#sessions-loading');
    const table = $('#sessions-table');
    loading.classList.remove('hidden');
    table.classList.add('hidden');
    try {
        const resp = await fetch('/api/sessions?page=0&size=20');
        if (!resp.ok) throw new Error('Failed to load sessions');
        const data = await resp.json();
        const body = $('#sessions-body');
        body.innerHTML = '';
        data.items.forEach(s => {
            const full = s.availablePlaces <= 0;
            const tr = document.createElement('tr');
            if (full) tr.classList.add('full');
            tr.innerHTML = `
                <td>${esc(s.title)}</td>
                <td>${esc(s.room)}</td>
                <td>${s.capacity}</td>
                <td>${s.activeBookings}</td>
                <td>${s.availablePlaces}</td>
                <td><button class="btn btn-primary" ${full ? 'disabled' : ''}
                    onclick="bookSession('${s.id}')">Book</button></td>
            `;
            body.appendChild(tr);
        });
        loading.classList.add('hidden');
        table.classList.remove('hidden');
    } catch (e) {
        loading.textContent = 'Error loading sessions.';
        showStatus(e.message, 'error');
    }
}

async function bookSession(sessionId) {
    const key = crypto.randomUUID();
    try {
        const resp = await fetch('/api/bookings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Demo-Attendee': attendee(),
                'Idempotency-Key': key
            },
            body: JSON.stringify({ sessionId })
        });
        const data = await resp.json();
        if (resp.status === 201) {
            showStatus('Booking confirmed!', 'success');
            loadSessions();
        } else if (resp.status === 200) {
            showStatus('Booking already exists (replay).', 'info');
        } else {
            showStatus(data.message || 'Booking failed.', 'error');
        }
    } catch (e) {
        showStatus('Network error: ' + e.message, 'error');
    }
}

// Bookings
async function loadBookings() {
    const loading = $('#bookings-loading');
    const table = $('#bookings-table');
    const empty = $('#bookings-empty');
    loading.classList.remove('hidden');
    table.classList.add('hidden');
    empty.classList.add('hidden');
    try {
        const resp = await fetch('/api/bookings', {
            headers: { 'X-Demo-Attendee': attendee() }
        });
        if (!resp.ok) throw new Error('Failed to load bookings');
        const bookings = await resp.json();
        loading.classList.add('hidden');
        if (bookings.length === 0) {
            empty.classList.remove('hidden');
            return;
        }
        const body = $('#bookings-body');
        body.innerHTML = '';
        for (const b of bookings) {
            const tr = document.createElement('tr');
            const time = new Date(b.createdAt).toLocaleString();
            tr.innerHTML = `
                <td>${esc(b.sessionId)}</td>
                <td>${time}</td>
                <td><button class="btn btn-danger"
                    onclick="cancelBooking('${b.id}')">Cancel</button></td>
            `;
            body.appendChild(tr);
        }
        table.classList.remove('hidden');
        // Enrich with session titles
        const sessResp = await fetch('/api/sessions?page=0&size=100');
        if (sessResp.ok) {
            const sessData = await sessResp.json();
            const map = {};
            sessData.items.forEach(s => map[s.id] = s.title);
            body.querySelectorAll('tr').forEach((tr, i) => {
                const sid = bookings[i].sessionId;
                if (map[sid]) tr.children[0].textContent = map[sid];
            });
        }
    } catch (e) {
        loading.textContent = 'Error loading bookings.';
        showStatus(e.message, 'error');
    }
}

async function cancelBooking(id) {
    try {
        const resp = await fetch(`/api/bookings/${id}`, {
            method: 'DELETE',
            headers: { 'X-Demo-Attendee': attendee() }
        });
        if (resp.status === 204) {
            showStatus('Booking cancelled.', 'success');
            loadBookings();
        } else {
            const data = await resp.json();
            showStatus(data.message || 'Cancel failed.', 'error');
        }
    } catch (e) {
        showStatus('Network error: ' + e.message, 'error');
    }
}

// Demo Panel
$('#btn-reset').addEventListener('click', async () => {
    try {
        const resp = await fetch('/api/demo/reset', { method: 'POST' });
        if (resp.ok) {
            showStatus('Data reset to seed state.', 'success');
            loadSessions();
        } else showStatus('Reset failed.', 'error');
    } catch (e) { showStatus('Reset error: ' + e.message, 'error'); }
});

$('#btn-dispatch').addEventListener('click', async () => {
    const result = $('#dispatch-result');
    try {
        const resp = await fetch('/api/demo/dispatch', { method: 'POST' });
        const data = await resp.json();
        if (resp.ok) {
            const count = data.dispatched.length;
            result.classList.remove('hidden');
            if (count === 0) {
                result.textContent = 'No pending notifications.';
            } else {
                result.innerHTML = data.dispatched.map(d =>
                    `<div>${esc(d.deliveryKey)}: ${esc(d.outcome)}</div>`
                ).join('');
            }
            showStatus(`Dispatched ${count} notification(s).`, 'success');
        } else showStatus('Dispatch failed.', 'error');
    } catch (e) { showStatus('Dispatch error: ' + e.message, 'error'); }
});

$('#btn-timeout-on').addEventListener('click', () => setTimeout_(true));
$('#btn-timeout-off').addEventListener('click', () => setTimeout_(false));

async function setTimeout_(enabled) {
    try {
        await fetch(`/api/demo/simulate-timeout?enabled=${enabled}`, { method: 'POST' });
        loadTimeoutStatus();
        showStatus(`Timeout simulation ${enabled ? 'enabled' : 'disabled'}.`, 'info');
    } catch (e) { showStatus('Error: ' + e.message, 'error'); }
}

async function loadTimeoutStatus() {
    try {
        const resp = await fetch('/api/demo/timeout-status');
        const data = await resp.json();
        $('#timeout-status').textContent = data.simulateTimeout ? '⚠ ENABLED' : 'Disabled';
    } catch (e) { /* ignore */ }
}

$('#btn-refresh-activity').addEventListener('click', loadActivity);

async function loadActivity() {
    const log = $('#activity-log');
    try {
        const resp = await fetch('/api/demo/activity');
        const data = await resp.json();
        let html = '';

        html += '<div class="activity-section"><h4>Notifications</h4>';
        if (data.notifications.length === 0) {
            html += '<p>No notifications yet.</p>';
        } else {
            html += '<table class="activity-table"><thead><tr>';
            html += '<th>Attendee</th><th>Session</th><th>Status</th><th>Attempts</th><th>Delivery Key</th>';
            html += '</tr></thead><tbody>';
            data.notifications.forEach(n => {
                const cls = n.status === 'PENDING' ? 'status-pending' : 'status-delivered';
                html += `<tr>
                    <td>${esc(String(n.attendee || ''))}</td>
                    <td>${esc(String(n.session_title || ''))}</td>
                    <td class="${cls}">${esc(String(n.status))}</td>
                    <td>${n.attempts}</td>
                    <td style="font-size:0.7rem">${esc(String(n.delivery_key || ''))}</td>
                </tr>`;
            });
            html += '</tbody></table>';
        }
        html += '</div>';

        html += '<div class="activity-section"><h4>Gateway Deliveries</h4>';
        if (data.gatewayDeliveries.length === 0) {
            html += '<p>No gateway deliveries yet.</p>';
        } else {
            html += '<table class="activity-table"><thead><tr>';
            html += '<th>Delivery Key</th><th>Accepted</th><th>Acknowledged</th><th>Payload</th>';
            html += '</tr></thead><tbody>';
            data.gatewayDeliveries.forEach(d => {
                const ackCls = d.acknowledged ? 'status-accepted' : 'status-not-ack';
                html += `<tr>
                    <td style="font-size:0.7rem">${esc(d.deliveryKey)}</td>
                    <td class="status-accepted">${d.accepted ? 'Yes' : 'No'}</td>
                    <td class="${ackCls}">${d.acknowledged ? 'Yes' : 'No'}</td>
                    <td>${esc(d.payload)}</td>
                </tr>`;
            });
            html += '</tbody></table>';
        }
        html += '</div>';

        log.innerHTML = html;
    } catch (e) {
        log.textContent = 'Error loading activity.';
    }
}

function esc(s) {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}

// Initial load
loadSessions();
