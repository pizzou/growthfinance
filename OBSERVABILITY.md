# Observability — Logging &amp; Uptime Monitoring

## Centralized logging — fixed this pass

Added `logback-spring.xml` (structured JSON logs in every profile except local `dev`) and
`RequestIdFilter` (every request gets an ID, attached to every log line logged during it, and
echoed back as an `X-Request-Id` response header). This is the piece a log aggregator and any
alerting rule actually needs — a single JSON record per line, with a correlation ID to tie a
whole request's logs together.

**This is not "centralized logging" by itself** — it makes the logs ready to centralize.
Actually centralizing them still needs one more piece: something shipping the JSON log file (or
container stdout) to wherever you want to query/alert on it. Concretely, pick one:
- **Free/cheap self-hosted**: Grafana Loki + Promtail (Promtail tails the log file this app
  already writes and ships it to Loki; Grafana queries it)
- **Managed**: CloudWatch Logs (if on AWS — just point the `awslogs` Docker logging driver at
  the container instead of a file), Datadog, or similar
- **Simple ELK**: Filebeat tailing the same file, shipping to Elasticsearch/Kibana

Whichever you pick, the app's job is done — it's emitting structured, correlatable logs. The
remaining work is standing up and pointing that shipper, and writing the actual alert rules
(e.g. "alert if error rate > X in 5 minutes") on top of the aggregated logs — that's genuinely a
decision only you can make about what should page someone at 2am versus what can wait for a
morning review.

## Uptime / synthetic monitoring — not done, but genuinely a 15-minute task

`GET /actuator/health` already exists and already reports Postgres + disk + app status (it's
what `deploy/scripts/health-check.sh` polls). What's missing is something checking it from
*outside* your infrastructure, on a schedule, and paging someone when it fails — if your host
goes down, nothing currently notices except a customer complaining.

This is deliberately not something worth writing code for — free/cheap external services do
this well already:
- **UptimeRobot** (free tier) or **Better Stack** (formerly Better Uptime) — point either at
  `https://yourdomain/actuator/health`, check every 1–5 minutes, configure SMS/email/Slack
  alerting on failure. Genuinely a 15-minute setup once you have a real domain live.
- If you want it to check something deeper than "is the process up" — e.g. "can a test loan
  actually be created end-to-end" — that's a synthetic check you'd script yourself (a small
  cron job hitting the real API with a test account and alerting on failure), which is more
  work and worth doing later, not before launch.

Do this before go-live — it costs nothing and closes real exposure (finding out about an outage
from a customer instead of a monitor).
