[English](getting-started.md) | [简体中文](../zh-CN/getting-started.md)

# Build, Run, and Verify

FaultPilot currently provides a runnable Mock-mode troubleshooting flow, local log-file analysis (see [examples/local-file](../../examples/local-file/README.md)), JDBC log-table analysis (see [examples/jdbc-log-table](../../examples/jdbc-log-table/README.md)), read-only MySQL/PostgreSQL diagnostics (see [examples/mysql-local](../../examples/mysql-local/README.md) and [examples/postgres-local](../../examples/postgres-local/README.md)), and real AI diagnosis via the OpenAI API or an experimental local Codex CLI provider.

## Modules

| Module | Responsibility | Run separately |
| --- | --- | --- |
| `faultpilot-core` | Domain models, sanitization, rules, evidence collection, and context budgeting | No |
| `faultpilot-adapters` | Mock adapters and future log, database, and model adapters | No |
| `faultpilot-server` | Spring Boot REST API that assembles and runs the application | Yes |

Maven packages all three modules as JAR files, but only `faultpilot-server` is an executable service.

## Requirements

- JDK 17
- Maven 3.6+

Verify the environment:

```bash
java -version
mvn -version
```

On macOS, select an installed JDK 17 if `JAVA_HOME` is not configured:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## Build

Run from the repository root:

```bash
mvn -B clean package
```

To run the complete test suite:

```bash
mvn -B clean verify
```

## Start the Server

Start the packaged Spring Boot JAR:

```bash
java -jar faultpilot-server/target/faultpilot-server-0.1.0.jar
```

Alternatively, start it directly with Maven:

```bash
mvn -pl faultpilot-server -am spring-boot:run
```

The default configuration starts the server on `http://localhost:8080` in Mock mode.

Event ingestion is token-protected by default. For a local-only ingestion demo, explicitly opt out
when starting the server:

```bash
java -jar faultpilot-server/target/faultpilot-server-0.1.0.jar \
  --faultpilot.ingestion.require-token=false
```

Never use that override for an online deployment.

## Verify the Server

Check health:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

List configured projects:

```bash
curl http://localhost:8080/api/v1/projects
```

Open the built-in console in a browser:

[http://localhost:8080/](http://localhost:8080/)

Pick a project and environment, then click **Run Diagnosis** (or **Load Mock Demo** to
prefill the built-in scenario). While the report is generated the console shows an
"AI is analyzing collected evidence" loading state. In Mock mode this returns almost
instantly; with `openai-api` or `codex-cli` a real model call can take tens of seconds,
so keep the page open until the report renders. The raw JSON response is available under
the collapsible section at the bottom.

Run the built-in local slow-SQL scenario:

```bash
curl -X POST http://localhost:8080/api/v1/diagnoses \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "order-service",
    "environment": "local",
    "question": "Why is the order API slow?",
    "from": "2026-06-01T01:00:00Z",
    "to": "2026-06-01T03:00:00Z"
  }'
```

The JSON report should include a `slow-sql-pool-contention` root-cause candidate with referenced evidence.

Reports and ingested events are persisted to a local H2 database (`./data/` by default, override with
`FAULTPILOT_STORE_URL`), so `GET /api/v1/diagnoses/{id}` still works after a restart. The `data/`
directory is git-ignored and never published.

The console's **Recent Diagnoses** panel lists stored reports (newest first); click a row to reopen
its full report. The same data is available via `GET /api/v1/diagnoses?projectId=&environment=&limit=`.

For local experiments that need real hosts or credentials, put them in
`config/application-local.yml` (git-ignored, and outside `src/main/resources` so it never lands in
the packaged jar) and start with `--spring.profiles.active=local`. Never put real credentials in
`application.yml`.

Stop the foreground server with `Ctrl+C`.

## AI Providers

`FAULTPILOT_AI_PROVIDER` selects how reports are generated. Configuration is read from environment
variables (just `export` them, as shown below); every variable has a default, so an unset one uses
it. Any provider failure (transport, timeout, malformed output) falls back to a rule-only report —
the request never fails. (A flag such as `--faultpilot.ai.provider=...` still overrides the env var.)

| Variable | Default | Used by |
| --- | --- | --- |
| `FAULTPILOT_AI_PROVIDER` | `mock` | provider selection (`mock` / `openai-api` / `codex-cli`) |
| `OPENAI_API_KEY` | _(empty)_ | `openai-api` (required) |
| `OPENAI_MODEL` | _(empty)_ | `openai-api` (required; must support json_schema) |
| `OPENAI_BASE_URL` | `https://api.openai.com` | `openai-api` (optional; compatible gateway) |
| `OPENAI_TIMEOUT` | `35s` | `openai-api` (optional) |
| `FAULTPILOT_CODEX_COMMAND` / `FAULTPILOT_CODEX_MODEL` / `FAULTPILOT_CODEX_TIMEOUT` | `codex` / _(empty)_ / `120s` | `codex-cli` (optional) |
| `FAULTPILOT_DB_URL` / `FAULTPILOT_DB_USER` / `FAULTPILOT_DB_PASSWORD` | _(empty)_ | a project's `logs(type: jdbc)` or `database` block |
| `FAULTPILOT_INGEST_TOKEN` | _(empty)_ | `faultpilot.ingestion.tokens` |

- **`mock`** (default): deterministic, no network, for demos and tests.
- **`openai-api`**: real diagnosis via the OpenAI API. Configure via environment:
  ```bash
  export FAULTPILOT_AI_PROVIDER=openai-api
  export OPENAI_API_KEY=sk-...
  export OPENAI_MODEL=gpt-4o-mini        # a model that supports json_schema response format
  mvn -pl faultpilot-server spring-boot:run
  ```
  `OPENAI_BASE_URL` may point at a compatible gateway. The API key is sent only in the
  Authorization header and is never logged.
- **`codex-cli`** (experimental, local-only): reuses an already-authenticated Codex CLI.
  ```bash
  codex login            # you run this yourself, once
  export FAULTPILOT_AI_PROVIDER=codex-cli
  mvn -pl faultpilot-server spring-boot:run
  ```
  FaultPilot only invokes `codex exec` in a read-only sandbox; it never runs `codex login` and never
  reads, copies, or logs Codex credential files. **Do not enable `codex-cli` for online
  deployments** — use `openai-api` with a key there.

The model only ever receives the already-sanitized evidence context. Rules stay authoritative: they
decide which root-cause candidates exist, with what evidence and what strength — the model
contributes the summary and the per-candidate explanation, and cannot add or remove a finding.

### Evaluating answer quality

Prompt changes are otherwise unmeasurable, so the repository ships an evaluation that runs the
built-in scenarios (which have known-correct answers) against a **real** provider and grades each
report: right root cause, required evidence cited, no invented evidence ids, no fallback. It calls
a paid API, so it is excluded from the normal build and runs only under the `eval` profile:

```bash
export FAULTPILOT_AI_PROVIDER=openai-api OPENAI_API_KEY=sk-... OPENAI_MODEL=gpt-4o-mini
mvn -pl faultpilot-server -am verify -Peval
```

A score card is printed per scenario and the run fails if any scenario is diagnosed wrongly. Run it
after changing the prompt to see whether quality moved.

## Connect a Java Application

FaultPilot analyzes an application without embedding an SDK. The default
[`application.yml`](../../faultpilot-server/src/main/resources/application.yml) ships only the mock
demo project; to connect a real app, add a project to `faultpilot.projects` and combine the
evidence sources you need — every diagnosis collects them together. Pick **one** log source via
`logs.type` (`local-file` | `jdbc` | `mock`), and optionally add a `database` block on the same
project for read-only DB analysis: logs and database are additive, not either/or.

| Integration | Prepare on the monitored app | Required fields | Notes |
| --- | --- | --- | --- |
| **Local log files** ([example](../../examples/local-file/README.md)) | A readable log file on the FaultPilot host | `logs.type: local-file`, `logs.paths` | The default parser reads the Spring Boot console format; set `logs.pattern` only for other layouts. Only `WARN`/`ERROR` lines are analyzed. |
| **JDBC log table** ([example](../../examples/jdbc-log-table/README.md)) | A **read-only** DB view with columns `occurred_at, level, trace_id, message, stack_trace` | `logs.type: jdbc`, `logs.url`, `logs.username`, `logs.password`, `logs.view` | MySQL/PostgreSQL drivers ship with the server; for other databases add the JDBC driver to the classpath. |
| **Read-only database** ([MySQL](../../examples/mysql-local/README.md) / [PostgreSQL](../../examples/postgres-local/README.md)) | A dedicated **read-only** MySQL/PostgreSQL account | `database.type` (`mysql`\|`postgres`), `database.url`, `database.username`, `database.password` | Connection / long-transaction / lock-wait snapshots and slow-SQL summaries via built-in fixed SQL. Keep slow-query statistics enabled for slow-SQL summaries. |

Example: one project that reads local log files **and** a read-only MySQL database. Credentials use
environment variables (`FAULTPILOT_DB_*`); every field maps to `FaultPilotProperties`.

```yaml
faultpilot:
  projects:
    - id: my-app
      display-name: My App
      integration-level: L2
      environments: [local]
      max-query-hours: 168
      max-results: 500
      logs:                                    # pick ONE source: local-file | jdbc | mock
        type: local-file
        zone: Asia/Shanghai                    # interpret timestamps without an offset
        paths:
          - /absolute/path/to/your-app/error.log
        # set `pattern` only if your layout differs from the Spring Boot console format
      database:                                # OPTIONAL — read-only MySQL/PostgreSQL
        type: mysql                            # mysql | postgres
        url: ${FAULTPILOT_DB_URL}              # e.g. jdbc:mysql://localhost:3306/app
        username: ${FAULTPILOT_DB_USER}        # read-only account
        password: ${FAULTPILOT_DB_PASSWORD}
        long-tx-threshold: 30s
```

For JDBC log tables and PostgreSQL, see the runnable samples under `examples/`. All integrations
use read-only access, fixed parameterized SQL, time-range and max-result limits, and evidence
redaction. Before sending production data to a model provider, review the redacted AI payload.

**Next version (not in v0.1.0):** a Spring Boot Starter for push-based ingestion, an integration
wizard UI, and standardized metrics / trace / release-event sources.

See the [product overview](overview.md) and [Chinese SSOT](../zh-CN/specification.md) for the reviewed scope.
