#!/usr/bin/env bash
set -euo pipefail

# Reproducer for Exercise 2: concurrent overbooking of the last place.
# Prerequisites: app running on localhost:8080 with local-demo profile.
#
# Session 2 (Practical Kotlin) has capacity=20, seeded with 19 bookings.
# This script sends two concurrent booking requests for the last place.
# With the faulty code, both succeed → 21 active bookings (overbooked).
# With the fix, exactly one succeeds and the other gets 409.

BASE_URL="http://localhost:8080"
SESSION_ID="00000000-0000-0000-0000-000000000002"

echo "=== Resetting seed data ==="
curl -s -X POST "$BASE_URL/api/demo/reset" > /dev/null

echo "=== Checking initial state ==="
INITIAL=$(curl -s "$BASE_URL/api/sessions" | python3 -c '
import sys, json
data = json.load(sys.stdin)
for s in data["items"]:
    if s["id"] == "'"$SESSION_ID"'":
        print(f"Session: {s[\"title\"]}, Booked: {s[\"activeBookings\"]}/{s[\"capacity\"]}, Available: {s[\"availablePlaces\"]}")
')
echo "$INITIAL"

echo ""
echo "=== Sending two concurrent booking requests for the last place ==="

# Two parallel requests
STATUS1=$(curl -s -o /tmp/book1.json -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
    -H "Content-Type: application/json" \
    -H "X-Demo-Attendee: alice" \
    -H "Idempotency-Key: race-key-alice" \
    -d "{\"sessionId\":\"$SESSION_ID\"}" &)

STATUS2=$(curl -s -o /tmp/book2.json -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
    -H "Content-Type: application/json" \
    -H "X-Demo-Attendee: bob" \
    -H "Idempotency-Key: race-key-bob" \
    -d "{\"sessionId\":\"$SESSION_ID\"}" &)

wait

STATUS1=$(cat /tmp/book1.json 2>/dev/null && echo "")
STATUS2=$(cat /tmp/book2.json 2>/dev/null && echo "")

HTTP1=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
    -H "Content-Type: application/json" \
    -H "X-Demo-Attendee: alice" \
    -H "Idempotency-Key: race-key-alice" \
    -d "{\"sessionId\":\"$SESSION_ID\"}")

HTTP2=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
    -H "Content-Type: application/json" \
    -H "X-Demo-Attendee: bob" \
    -H "Idempotency-Key: race-key-bob" \
    -d "{\"sessionId\":\"$SESSION_ID\"}")

echo "Alice: HTTP $HTTP1"
echo "Bob:   HTTP $HTTP2"

echo ""
echo "=== Final state ==="
curl -s "$BASE_URL/api/sessions" | python3 -c '
import sys, json
data = json.load(sys.stdin)
for s in data["items"]:
    if s["id"] == "'"$SESSION_ID"'":
        booked = s["activeBookings"]
        cap = s["capacity"]
        ok = "OK" if booked <= cap else "OVERBOOKED!"
        print(f"Session: {s[\"title\"]}, Booked: {booked}/{cap} — {ok}")
'
