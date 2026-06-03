# Local file log source example

This example shows FaultPilot reading a local Java log file (Spring Boot / Logback default layout),
aggregating multi-line stack traces, and producing an exception-cluster diagnosis — no database,
monitoring platform, or model key required.

## What it contains

- `sample-app.log` — a sample application log with an `NullPointerException` spike around `10:05–10:07`.
- `application.yml` — a minimal project registration using `logs.type: local-file`.

## Configuration

A project opts into local-file logs via its `logs` block:

```yaml
faultpilot:
  projects:
    - id: local-file-demo
      environments: [local]
      logs:
        type: local-file
        paths:
          - examples/local-file/sample-app.log
        # Optional: override the line-head regex (named groups: ts, level, thread, logger, msg)
        # pattern: "^(?<ts>...)\\s+(?<level>...)\\s+(?<logger>...) - (?<msg>.*)$"
        # Optional: charset (default UTF-8) and zone (default = system zone)
        # charset: UTF-8
        # zone: Asia/Shanghai
```

The default pattern matches the standard Logback layout, e.g.:

```
2026-06-01 10:05:31.501 ERROR [http-nio-8080-exec-5] com.example.order.OrderService - Create order failed
```

Lines that do not match the head pattern (stack-trace frames, wrapped messages) are attached to the
preceding event. Only `WARN` and `ERROR` events within the requested time range are analyzed.

## Run

From the repository root:

```bash
mvn -pl faultpilot-server spring-boot:run
```

Then request a diagnosis over the sample log's time window:

```bash
curl -s -X POST http://localhost:8080/api/v1/diagnoses \
  -H 'Content-Type: application/json' \
  -d '{
        "projectId": "local-file-demo",
        "environment": "local",
        "question": "最近的订单创建错误是什么?",
        "from": "2026-06-01T09:00:00+08:00",
        "to":   "2026-06-01T11:00:00+08:00"
      }' | python3 -m json.tool
```

Expected: the report's timeline and evidence contain a `NullPointerException` cluster
(`com.example.order.OrderService`) parsed from the file, with every root-cause candidate referencing
existing evidence ids.

> Note: the assistant reads log files read-only and never tails, writes, or rotates them.
