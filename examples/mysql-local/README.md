# MySQL read-only analysis example

FaultPilot can inspect a MySQL instance through a **read-only account** to summarize connection
state, long transactions, lock waits, and slow-SQL templates — using fixed, built-in SQL only
(design §9.1). It never writes, KILLs connections, or runs EXPLAIN.

## Files

- `readonly-account.sql` — creates a minimum-privilege `faultpilot_ro` account.
- `application.yml` — a project registration using `database.type: mysql`.

## What it reads

| Evidence | Source |
| --- | --- |
| Connection / thread counts | `SHOW GLOBAL STATUS` |
| Long transactions | `information_schema.innodb_trx` |
| Lock waits | `performance_schema.data_lock_waits` (skipped if not granted) |
| Slow-SQL summary | `performance_schema.events_statements_summary_by_digest` (parameterized digest only) |

## Setup

```bash
# 1. Create the read-only account (edit the password first).
mysql -u root -p < examples/mysql-local/readonly-account.sql

# 2. Point FaultPilot at the read-only account and run.
#    (The MySQL JDBC driver ships with faultpilot-server — nothing to add.)
export FAULTPILOT_MYSQL_URL=jdbc:mysql://localhost:3306/app
export FAULTPILOT_MYSQL_USER=faultpilot_ro
export FAULTPILOT_MYSQL_PASSWORD=change-me
mvn -pl faultpilot-server spring-boot:run \
  --spring.config.additional-location=file:examples/mysql-local/application.yml
```

Then request a diagnosis:

```bash
curl -s -X POST http://localhost:8080/api/v1/diagnoses \
  -H 'Content-Type: application/json' \
  -d '{"projectId":"mysql-local","environment":"local","question":"数据库状态如何?",
       "from":"2026-06-01T00:00:00+08:00","to":"2026-06-01T23:59:59+08:00"}' | python3 -m json.tool
```

Expected: the report includes a `DB_HEALTH` evidence entry (connections, long transactions, lock
waits) and slow-SQL evidence when the digest table has data.

## Safety

- The connection is opened read-only with connect and query timeouts.
- All SQL is built in code; the model never generates SQL.
- Slow SQL carries only the parameterized digest, never real parameter values.
- If a sub-query is not permitted (e.g. `data_lock_waits`), it is skipped and the rest of the
  snapshot is still returned. If the database is unreachable, the source is reported under
  `unavailableSources`.
