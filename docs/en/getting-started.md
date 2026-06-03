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

Open the built-in test console in a browser:

[http://localhost:8080/](http://localhost:8080/)

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

Stop the foreground server with `Ctrl+C`.

## AI Providers

`faultpilot.ai.provider` selects how reports are generated. Any provider failure (transport,
timeout, malformed output) falls back to a rule-only report — the request never fails.

- **`mock`** (default): deterministic, no network, for demos and tests.
- **`openai-api`**: real diagnosis via the OpenAI API. Configure via environment:
  ```bash
  export OPENAI_API_KEY=sk-...
  export OPENAI_MODEL=gpt-4o-mini        # a model that supports json_schema response format
  mvn -pl faultpilot-server spring-boot:run --faultpilot.ai.provider=openai-api
  ```
  `base-url` may point at a compatible gateway (`faultpilot.ai.base-url`). The API key is sent only
  in the Authorization header and is never logged.
- **`codex-cli`** (experimental, local-only): reuses an already-authenticated Codex CLI.
  ```bash
  codex login            # you run this yourself, once
  mvn -pl faultpilot-server spring-boot:run --faultpilot.ai.provider=codex-cli
  ```
  FaultPilot only invokes `codex exec` in a read-only sandbox; it never runs `codex login` and never
  reads, copies, or logs Codex credential files. **Do not enable `codex-cli` for online
  deployments** — use `openai-api` with a key there.

The model only ever receives the already-sanitized evidence context, and root-cause strength is
computed by rules, not the model.

## Prepare a Java Application

The planned MVP will analyze an application without embedding an SDK:

1. Write application logs to a readable local file, or expose a read-only JDBC log table.
2. Create a dedicated read-only MySQL or PostgreSQL account if database diagnostics are required.
3. Keep database slow-query statistics enabled when slow SQL summaries are required.
4. Review the redacted AI payload before using production data with a model provider.

See the [product overview](overview.md) and [Chinese SSOT](../zh-CN/specification.md) for the reviewed scope.
