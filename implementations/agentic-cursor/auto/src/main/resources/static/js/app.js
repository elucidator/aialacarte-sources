(() => {
  const attendeeSelect = document.getElementById("attendee");
  const statusEl = document.getElementById("status");
  const sessionsBody = document.getElementById("sessions-body");
  const sessionsLoading = document.getElementById("sessions-loading");
  const bookingsList = document.getElementById("bookings-list");
  const bookingsEmpty = document.getElementById("bookings-empty");
  const bookingsLoading = document.getElementById("bookings-loading");
  const demoPanel = document.getElementById("demo-panel");

  function attendeeId() {
    return attendeeSelect.value;
  }

  function setStatus(kind, message) {
    statusEl.className = "status " + (kind || "");
    statusEl.textContent = message || "";
  }

  function headers(extra = {}) {
    return Object.assign(
      {
        "X-Demo-Attendee": attendeeId(),
        Accept: "application/json",
      },
      extra,
    );
  }

  async function parseError(res) {
    try {
      const body = await res.json();
      return body.message || body.code || res.statusText;
    } catch {
      return res.statusText || "Request failed";
    }
  }

  async function loadSessions() {
    sessionsLoading.classList.remove("hidden");
    setStatus("loading", "Loading sessions…");
    try {
      const res = await fetch("/api/sessions?page=0&size=20");
      if (!res.ok) throw new Error(await parseError(res));
      const data = await res.json();
      sessionsBody.innerHTML = "";
      for (const s of data.items) {
        const tr = document.createElement("tr");
        const full = s.availablePlaces <= 0;
        tr.innerHTML = `
          <td>${escapeHtml(s.title)}</td>
          <td>${escapeHtml(s.room)}</td>
          <td>${s.capacity}</td>
          <td>${s.activeBookings}</td>
          <td class="${full ? "full" : ""}">${s.availablePlaces}</td>
          <td></td>`;
        const td = tr.lastElementChild;
        const btn = document.createElement("button");
        btn.textContent = "Book";
        btn.disabled = full;
        btn.addEventListener("click", () => bookSession(s.id));
        td.appendChild(btn);
        sessionsBody.appendChild(tr);
      }
      setStatus("ok", `Loaded ${data.items.length} session(s).`);
    } catch (err) {
      setStatus("error", String(err.message || err));
    } finally {
      sessionsLoading.classList.add("hidden");
    }
  }

  async function loadBookings() {
    bookingsLoading.classList.remove("hidden");
    try {
      const res = await fetch("/api/bookings", { headers: headers() });
      if (!res.ok) throw new Error(await parseError(res));
      const items = await res.json();
      bookingsList.innerHTML = "";
      bookingsEmpty.classList.toggle("hidden", items.length > 0);
      for (const b of items) {
        const li = document.createElement("li");
        const info = document.createElement("div");
        info.innerHTML = `<strong>${escapeHtml(b.sessionTitle)}</strong><br><span>#${b.id}</span>`;
        const btn = document.createElement("button");
        btn.className = "secondary";
        btn.textContent = "Cancel";
        btn.addEventListener("click", () => cancelBooking(b.id));
        li.appendChild(info);
        li.appendChild(btn);
        bookingsList.appendChild(li);
      }
    } catch (err) {
      setStatus("error", String(err.message || err));
    } finally {
      bookingsLoading.classList.add("hidden");
    }
  }

  async function bookSession(sessionId) {
    setStatus("loading", "Booking…");
    const key = crypto.randomUUID();
    try {
      const res = await fetch("/api/bookings", {
        method: "POST",
        headers: headers({
          "Content-Type": "application/json",
          "Idempotency-Key": key,
        }),
        body: JSON.stringify({ sessionId }),
      });
      if (!res.ok) throw new Error(await parseError(res));
      const booking = await res.json();
      setStatus("ok", res.status === 201
        ? `Booked #${booking.id} successfully.`
        : `Replay returned booking #${booking.id}.`);
      await Promise.all([loadSessions(), loadBookings(), maybeRefreshDemo()]);
    } catch (err) {
      setStatus("error", String(err.message || err));
      await loadSessions();
    }
  }

  async function cancelBooking(id) {
    setStatus("loading", "Cancelling…");
    try {
      const res = await fetch(`/api/bookings/${id}`, {
        method: "DELETE",
        headers: headers(),
      });
      if (!res.ok) throw new Error(await parseError(res));
      setStatus("ok", `Cancelled booking #${id}.`);
      await Promise.all([loadSessions(), loadBookings(), maybeRefreshDemo()]);
    } catch (err) {
      setStatus("error", String(err.message || err));
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  async function probeDemo() {
    const res = await fetch("/api/demo/status");
    if (res.status === 404) {
      demoPanel.classList.add("hidden");
      return false;
    }
    if (!res.ok) {
      demoPanel.classList.add("hidden");
      return false;
    }
    demoPanel.classList.remove("hidden");
    await renderDemo(await res.json());
    return true;
  }

  async function maybeRefreshDemo() {
    if (!demoPanel.classList.contains("hidden")) {
      await refreshDemo();
    }
  }

  async function refreshDemo() {
    const res = await fetch("/api/demo/status");
    if (!res.ok) return;
    await renderDemo(await res.json());
  }

  async function renderDemo(data) {
    document.getElementById("m-pending").textContent = data.pendingConfirmations;
    document.getElementById("m-accepted").textContent = data.acceptedDeliveries;
    document.getElementById("m-acks").textContent = data.acknowledgements;
    document.getElementById("simulate-failure").checked = !!data.simulateNotificationFailure;
    const list = document.getElementById("activity-list");
    list.innerHTML = "";
    for (const a of data.recentActivity || []) {
      const li = document.createElement("li");
      li.textContent = `[${a.createdAt}] ${a.kind}: ${a.message}`;
      list.appendChild(li);
    }
  }

  document.getElementById("refresh-sessions").addEventListener("click", loadSessions);
  document.getElementById("refresh-bookings").addEventListener("click", loadBookings);
  attendeeSelect.addEventListener("change", () => {
    loadBookings();
    setStatus("ok", `Demo identity: ${attendeeSelect.options[attendeeSelect.selectedIndex].text}`);
  });

  document.getElementById("demo-reset").addEventListener("click", async () => {
    setStatus("loading", "Resetting seed data…");
    const res = await fetch("/api/demo/reset", { method: "POST" });
    if (!res.ok) {
      setStatus("error", await parseError(res));
      return;
    }
    setStatus("ok", "Seed data restored.");
    await Promise.all([loadSessions(), loadBookings(), refreshDemo()]);
  });

  document.getElementById("simulate-failure").addEventListener("change", async (e) => {
    const enabled = e.target.checked;
    const res = await fetch("/api/demo/simulate-failure", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled }),
    });
    if (!res.ok) {
      setStatus("error", await parseError(res));
      return;
    }
    setStatus("ok", `Notification failure simulation: ${enabled}`);
    await refreshDemo();
  });

  document.getElementById("demo-dispatch").addEventListener("click", async () => {
    setStatus("loading", "Dispatching pending confirmations…");
    const res = await fetch("/api/demo/dispatch", { method: "POST" });
    if (!res.ok) {
      setStatus("error", await parseError(res));
      return;
    }
    const summary = await res.json();
    setStatus(
      "ok",
      `Dispatch done — attempted ${summary.attempted}, acked ${summary.acknowledged}, failed ${summary.failed}.`,
    );
    await refreshDemo();
  });

  document.getElementById("demo-refresh").addEventListener("click", refreshDemo);

  Promise.all([loadSessions(), loadBookings(), probeDemo()]);
})();
