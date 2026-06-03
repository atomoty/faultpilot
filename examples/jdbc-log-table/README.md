# JDBC log-table source example

For Java projects that store logs in a database table rather than a file, FaultPilot can read them
through a **read-only view** and produce the same evidence-based diagnosis — no file shipping,
monitoring platform, or model key required.

## How it works

1. Your project keeps logs in its own table (any column names).
2. You create a **read-only view** that aliases the columns to the names FaultPilot expects:
   `occurred_at, level, trace_id, message, stack_trace` (see `schema.sql`).
3. You grant a read-only account `SELECT` on that view.
4. FaultPilot queries the view with a fixed, parameter-bound SQL (time range + row cap). The model
   never generates SQL; the view name is validated as a plain identifier before use.

## Files

- `schema.sql` — example table + canonical view + sample rows (H2 syntax; adapt types for your DB).
- `application.yml` — a project registration using `logs.type: jdbc`.

## Try it with H2

```bash
H2JAR=$(find ~/.m2 -name 'h2-*.jar' | grep -v sources | head -1)

# Seed a file-based H2 DB (use the same user the assistant will connect as).
java -cp "$H2JAR" org.h2.tools.RunScript \
  -url jdbc:h2:/tmp/fpjdbc -user sa -password "" -script examples/jdbc-log-table/schema.sql

export FAULTPILOT_DEMO_DB_URL=jdbc:h2:/tmp/fpjdbc
export FAULTPILOT_DEMO_DB_USER=sa
export FAULTPILOT_DEMO_DB_PASSWORD=

mvn -pl faultpilot-server spring-boot:run \
  --spring.config.additional-location=file:examples/jdbc-log-table/application.yml
```

Then request a diagnosis:

```bash
curl -s -X POST http://localhost:8080/api/v1/diagnoses \
  -H 'Content-Type: application/json' \
  -d '{"projectId":"jdbc-demo","environment":"local","question":"最近的订单错误是什么?",
       "from":"2026-06-01T09:00:00+08:00","to":"2026-06-01T11:00:00+08:00"}' | python3 -m json.tool
```

Expected: the report contains a `NullPointerException` log cluster parsed from the view. If the
database is unreachable or the credentials are wrong, the report lists the log source under
`unavailableSources` instead of silently returning no logs.

## Configuration

```yaml
faultpilot:
  projects:
    - id: jdbc-demo
      environments: [local]
      logs:
        type: jdbc
        url: ${FAULTPILOT_DEMO_DB_URL}       # e.g. jdbc:postgresql://host:5432/app
        username: ${FAULTPILOT_DEMO_DB_USER}  # READ-ONLY account
        password: ${FAULTPILOT_DEMO_DB_PASSWORD}
        view: faultpilot_log_view             # must expose the canonical columns
        zone: Asia/Shanghai                   # interprets occurred_at when it has no offset
        connect-timeout-ms: 2000
        query-timeout-ms: 3000
```

## Other databases

The adapter is driver-agnostic: it connects via the JDBC `url`. For MySQL or PostgreSQL, add the
driver to the classpath (e.g. `com.mysql:mysql-connector-j` or `org.postgresql:postgresql`) and
point `url`/credentials at a **read-only** account. FaultPilot bundles only the H2 driver.

## Safety

- The connection is opened read-only with connect and query timeouts.
- Only the time range and row cap are sent; they are bound parameters, never string-concatenated.
- The `view` value must be a plain SQL identifier (`schema.view` allowed); anything else is rejected.
- If the database is unreachable, the log source is reported as **unavailable** in the report rather
  than silently returning "no logs".
