# PostgreSQL read-only analysis example

FaultPilot can inspect a PostgreSQL instance through a **read-only role** to summarize connection
state, long transactions, lock waits, and slow-SQL templates — using fixed, built-in SQL only
(design §9.2). It never writes or terminates backends.

## Files

- `readonly-account.sql` — creates a minimum-privilege `faultpilot_ro` role.
- `application.yml` — a project registration using `database.type: postgres`.

## What it reads

| Evidence | Source |
| --- | --- |
| Active / idle / waiting connections | `pg_stat_activity` |
| Long transactions | `pg_stat_activity` (`xact_start` older than threshold) |
| Lock waits | `pg_locks` (ungranted locks) |
| Slow-SQL summary | `pg_stat_statements` (skipped if the extension is not enabled) |

## Setup

```bash
# 1. Create the read-only role (edit the password first).
psql -U postgres -d app -f examples/postgres-local/readonly-account.sql

# 2. (Optional, for slow SQL) enable pg_stat_statements as a superuser:
#    add 'pg_stat_statements' to shared_preload_libraries, restart, then:
#    CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

# 3. Add the PostgreSQL JDBC driver to the classpath (FaultPilot bundles only H2):
#    org.postgresql:postgresql

# 4. Point FaultPilot at the read-only role and run.
export FAULTPILOT_PG_URL=jdbc:postgresql://localhost:5432/app
export FAULTPILOT_PG_USER=faultpilot_ro
export FAULTPILOT_PG_PASSWORD=change-me
mvn -pl faultpilot-server spring-boot:run \
  --spring.config.additional-location=file:examples/postgres-local/application.yml
```

Then request a diagnosis:

```bash
curl -s -X POST http://localhost:8080/api/v1/diagnoses \
  -H 'Content-Type: application/json' \
  -d '{"projectId":"postgres-local","environment":"local","question":"数据库状态如何?",
       "from":"2026-06-01T00:00:00+08:00","to":"2026-06-01T23:59:59+08:00"}' | python3 -m json.tool
```

Expected: the report includes a `DB_HEALTH` evidence entry (connections, long transactions, lock
waits) and slow-SQL evidence when `pg_stat_statements` is enabled.

## Safety

- The connection is opened read-only with connect and query timeouts.
- All SQL is built in code; the model never generates SQL.
- Slow SQL carries only the normalized query text from `pg_stat_statements`, never literal values.
- If `pg_stat_statements` is not installed, slow-SQL analysis is skipped and the snapshot is still
  returned. If the database is unreachable, the source is reported under `unavailableSources`.
