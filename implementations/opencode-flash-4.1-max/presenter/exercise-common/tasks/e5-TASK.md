# Task: the app container never becomes healthy

`docker compose up --build -d` starts both containers. The web UI loads and the API
answers requests, but `docker compose ps` reports the app container as `unhealthy`, so
tooling that waits for a healthy stack never proceeds.

Inspect the configuration, find why the health check fails and correct it. Then validate
recovery locally:

1. use this exercise's isolated Compose project and disposable data;
2. show the app container reaching `healthy`;
3. tear everything down afterwards (containers and volumes), leaving other projects
   untouched.

The app and test commands are in `README.md`.
