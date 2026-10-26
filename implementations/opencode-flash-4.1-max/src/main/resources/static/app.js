"use strict";

const state = {
    identity: "Alice",
    page: 0,
    size: 20,
    totalElements: 0,
    sessions: [],
    bookings: [],
    lastRequest: null,
    busy: false,
};

const el = (id) => document.getElementById(id);

/* ---------- generic API helper with explicit states ---------- */

async function api(path, options = {}) {
    const headers = Object.assign({ "X-Demo-Attendee": state.identity }, options.headers || {});
    const response = await fetch(path, Object.assign({}, options, { headers }));
    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch (ignored) {
        body = { code: "UNPARSEABLE_RESPONSE", message: text };
    }
    if (!response.ok) {
        const error = new Error(body && body.message ? body.message : response.statusText);
        error.status = response.status;
        error.code = body && body.code ? body.code : "HTTP_" + response.status;
        throw error;
    }
    return { status: response.status, body };
}

function showError(error) {
    const banner = el("banner");
    banner.textContent = `${error.code || "ERROR"}: ${error.message}`;
    banner.hidden = false;
}

function clearError() {
    const banner = el("banner");
    banner.hidden = true;
    banner.textContent = "";
}

let toastTimer = null;
function showToast(message, warn = false) {
    const toast = el("toast");
    toast.textContent = message;
    toast.classList.toggle("warn", warn);
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.hidden = true; }, 5000);
}

function setBusy(busy) {
    state.busy = busy;
    document.querySelectorAll("button").forEach((button) => {
        if (button.id !== "identity") button.disabled = busy;
    });
    if (!busy) {
        el("prev-page").disabled = state.page <= 0;
        el("next-page").disabled = (state.page + 1) * state.size >= state.totalElements;
    }
}

/* ---------- sessions view ---------- */

async function loadSessions() {
    el("sessions-status").textContent = "Loading sessions…";
    el("sessions-status").classList.remove("error");
    try {
        const { body } = await api(`/api/sessions?page=${state.page}&size=${state.size}`);
        state.sessions = body.items;
        state.totalElements = body.totalElements;
        renderSessions();
        el("sessions-status").textContent = `${body.totalElements} session(s) in the catalogue.`;
    } catch (error) {
        el("sessions-status").textContent = "Could not load sessions.";
        el("sessions-status").classList.add("error");
        showError(error);
    }
}

function renderSessions() {
    const table = el("sessions-table");
    const tbody = table.querySelector("tbody");
    tbody.innerHTML = "";
    for (const session of state.sessions) {
        const full = session.availablePlaces <= 0;
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(session.title)}</td>
            <td>${escapeHtml(session.room)}</td>
            <td class="num">${session.capacity}</td>
            <td class="num">${session.activeBookings}</td>
            <td class="num"><strong>${session.availablePlaces}</strong></td>
            <td></td>`;
        const action = document.createElement("button");
        action.textContent = full ? "Full" : "Book";
        action.disabled = full;
        action.addEventListener("click", () => book(session));
        row.lastElementChild.appendChild(action);
        tbody.appendChild(row);
    }
    table.hidden = state.sessions.length === 0;
    el("page-info").textContent = `Page ${state.page + 1} of ${Math.max(1, Math.ceil(state.totalElements / state.size))}`;
    el("prev-page").disabled = state.page <= 0;
    el("next-page").disabled = (state.page + 1) * state.size >= state.totalElements;
}

async function book(session) {
    clearError();
    setBusy(true);
    const requestKey = crypto.randomUUID();
    state.lastRequest = { sessionId: session.id, requestKey, title: session.title };
    try {
        const { status } = await api("/api/bookings", {
            method: "POST",
            headers: { "Content-Type": "application/json", "Idempotency-Key": requestKey },
            body: JSON.stringify({ sessionId: session.id }),
        });
        if (status === 201) {
            showToast(`Booked ${state.identity} into '${session.title}'.`);
        } else {
            showToast(`Replayed request: existing booking returned for '${session.title}' (HTTP 200).`, true);
        }
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
        await refreshAll();
    }
}

/* ---------- my bookings view ---------- */

async function loadBookings() {
    el("bookings-status").textContent = "Loading bookings…";
    el("bookings-status").classList.remove("error");
    el("bookings-identity").textContent = state.identity;
    try {
        const { body } = await api("/api/bookings");
        state.bookings = body;
        renderBookings();
        el("bookings-status").textContent = body.length === 0
            ? `No active bookings for ${state.identity}.`
            : `${body.length} active booking(s) for ${state.identity}.`;
        el("booking-count").textContent = String(body.length);
    } catch (error) {
        el("bookings-status").textContent = "Could not load bookings.";
        el("bookings-status").classList.add("error");
        showError(error);
    }
}

function renderBookings() {
    const table = el("bookings-table");
    const tbody = table.querySelector("tbody");
    tbody.innerHTML = "";
    for (const booking of state.bookings) {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${escapeHtml(booking.sessionTitle)}</td>
            <td>${escapeHtml(booking.sessionRoom)}</td>
            <td>${new Date(booking.createdAt).toLocaleString()}</td>
            <td></td>`;
        const action = document.createElement("button");
        action.textContent = "Cancel";
        action.className = "danger";
        action.addEventListener("click", () => cancel(booking));
        row.lastElementChild.appendChild(action);
        tbody.appendChild(row);
    }
    table.hidden = state.bookings.length === 0;
}

async function cancel(booking) {
    clearError();
    setBusy(true);
    try {
        await api(`/api/bookings/${booking.id}`, { method: "DELETE" });
        showToast(`Cancelled booking ${booking.id} for '${booking.sessionTitle}'.`);
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
        await refreshAll();
    }
}

/* ---------- demo panel ---------- */

async function loadDemo() {
    el("demo-status").textContent = "Loading demo state…";
    el("demo-status").classList.remove("error");
    try {
        const [{ body: demoState }, { body: notifications }, { body: activity }] = await Promise.all([
            api("/api/demo/state"),
            api("/api/demo/notifications?limit=50"),
            api("/api/demo/activity?limit=50"),
        ]);
        const failureArmed = demoState.controls.simulate_notification_failure === "true";
        el("failure-toggle").checked = failureArmed;
        el("demo-status").textContent =
            `Pending confirmations: ${demoState.pendingNotifications} · acknowledged: ${demoState.acknowledgedNotifications}` +
            ` · bookings: ${demoState.totalBookings} · failure simulation: ${failureArmed ? "ARMED" : "off"}`;
        renderNotifications(notifications);
        renderActivity(activity);
    } catch (error) {
        el("demo-status").textContent = "Demo panel unavailable. Start the app with the local-demo profile.";
        el("demo-status").classList.add("error");
        showError(error);
    }
}

function renderNotifications(notifications) {
    const table = el("notifications-table");
    const tbody = table.querySelector("tbody");
    tbody.innerHTML = "";
    if (notifications.length === 0) {
        table.hidden = true;
        return;
    }
    for (const notification of notifications) {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>#${notification.bookingId}</td>
            <td>${escapeHtml(notification.sessionTitle)}</td>
            <td>${notification.status}</td>
            <td class="num">${notification.attempts}</td>
            <td class="num">${notification.acceptedDeliveries}</td>
            <td><code>${escapeHtml(notification.deliveryKey)}</code></td>`;
        tbody.appendChild(row);
    }
    table.hidden = false;
}

function renderActivity(activity) {
    const list = el("activity-list");
    list.innerHTML = "";
    if (activity.length === 0) {
        const item = document.createElement("li");
        item.className = "empty";
        item.textContent = "No demo activity yet.";
        list.appendChild(item);
        return;
    }
    for (const entry of activity) {
        const item = document.createElement("li");
        item.innerHTML = `<span class="kind">${escapeHtml(entry.kind)}</span>${escapeHtml(entry.message)}` +
            `<span class="time">${new Date(entry.createdAt).toLocaleTimeString()}</span>`;
        list.appendChild(item);
    }
}

async function resetDemo() {
    clearError();
    setBusy(true);
    try {
        await api("/api/demo/reset", { method: "POST" });
        showToast("Demo data reset to the deterministic seed (Alice and Bob have no bookings).", true);
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
        await refreshAll();
    }
}

async function toggleFailure(enabled) {
    clearError();
    try {
        await api("/api/demo/simulate-notification-failure", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ enabled }),
        });
        showToast(enabled
            ? "Failure simulation armed: the next dispatch records its message then loses the acknowledgement."
            : "Failure simulation disabled.", enabled);
    } catch (error) {
        showError(error);
    } finally {
        await loadDemo();
    }
}

async function dispatchPending() {
    clearError();
    setBusy(true);
    try {
        const { body } = await api("/api/demo/dispatch", { method: "POST" });
        showToast(
            `Dispatch: ${body.attempted} attempted · ${body.accepted} accepted · ${body.duplicates} duplicate` +
            ` · ${body.timedOut} timed out · ${body.acknowledged} acknowledged · ${body.stillPending} still pending`,
            body.timedOut > 0,
        );
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
        await refreshAll();
    }
}

async function repeatLastRequest() {
    if (!state.lastRequest) return;
    clearError();
    setBusy(true);
    try {
        const { status } = await api("/api/bookings", {
            method: "POST",
            headers: { "Content-Type": "application/json", "Idempotency-Key": state.lastRequest.requestKey },
            body: JSON.stringify({ sessionId: state.lastRequest.sessionId }),
        });
        showToast(`Replay with the same Idempotency-Key returned HTTP ${status}: no second booking was created.`, true);
    } catch (error) {
        showError(error);
    } finally {
        setBusy(false);
        await refreshAll();
    }
}

/* ---------- wiring ---------- */

async function refreshAll() {
    await Promise.all([loadSessions(), loadBookings(), loadDemo()]);
    el("repeat-request").disabled = state.lastRequest === null;
}

function switchView(name) {
    document.querySelectorAll(".tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.view === name));
    document.querySelectorAll(".view").forEach((view) => { view.hidden = view.id !== `view-${name}`; });
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
    }[char]));
}

document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => switchView(tab.dataset.view));
});

el("identity").addEventListener("change", async (event) => {
    state.identity = event.target.value;
    state.page = 0;
    clearError();
    showToast(`Demo identity switched to ${state.identity}.`);
    await refreshAll();
});

el("prev-page").addEventListener("click", async () => { state.page -= 1; await loadSessions(); });
el("next-page").addEventListener("click", async () => { state.page += 1; await loadSessions(); });
el("refresh-sessions").addEventListener("click", () => loadSessions());
el("refresh-bookings").addEventListener("click", () => loadBookings());
el("refresh-demo").addEventListener("click", () => loadDemo());
el("reset-demo").addEventListener("click", () => resetDemo());
el("dispatch").addEventListener("click", () => dispatchPending());
el("repeat-request").addEventListener("click", () => repeatLastRequest());
el("failure-toggle").addEventListener("change", (event) => toggleFailure(event.target.checked));

refreshAll();
