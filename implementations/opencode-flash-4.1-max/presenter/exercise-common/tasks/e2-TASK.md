# Task: capacity is occasionally exceeded

Pilot feedback from the deployment that runs two application instances against one
PostgreSQL database:

> When two attendees click *Book* at almost the same moment for the last place, both
> sometimes get a booking and the session shows more active bookings than its capacity.
> It does not happen when we test alone.

Find the cause and fix it so that, under simultaneous requests from two application
processes sharing the database:

- active bookings never exceed the session capacity;
- one attendee still cannot hold two active bookings for the same session;
- replaying a request key still returns the original booking instead of creating one.

Run the existing checks and add a regression test for simultaneous booking if you can.
The app and test commands are in `README.md`.
