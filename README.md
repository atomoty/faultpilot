[English](README.md) | [简体中文](README.zh-CN.md)

# FaultPilot

FaultPilot is a read-only AI troubleshooting and monitoring assistant for Java applications.

> Status: Early `v0.1.0` development build. The current implementation provides a runnable Mock-mode troubleshooting flow, local log-file analysis, JDBC log-table analysis, read-only MySQL/PostgreSQL diagnostics, and real AI diagnosis via the OpenAI API (plus an experimental local Codex CLI provider).

## Why FaultPilot

FaultPilot is not a full observability platform. It is a lightweight, Java-first troubleshooting assistant that can start with local log files and read-only database access, then grow with your observability stack.

The project is designed to:

- Analyze Java application logs and multi-line stack traces.
- Inspect MySQL or PostgreSQL through read-only accounts.
- Summarize slow SQL, long transactions, connection status, and lock waits.
- Correlate evidence with deterministic rules before asking AI to explain it.
- Produce auditable reports with evidence references and human-verification warnings.
- Run locally or as an independently deployed service.

## Current Implementation

The current `v0.1.0` implementation includes:

- A runnable Mock-mode diagnosis flow.
- Local log-file analysis: multi-line stack-trace aggregation, time-range and level filtering, per-project line patterns, and bounded scanning.
- JDBC log-table analysis: read-only, parameter-bound queries over a canonical view, with injection-safe view names and unavailable-source reporting.
- Read-only MySQL/PostgreSQL diagnostics: connection state, long transactions, lock waits, and parameterized slow-SQL summaries via fixed built-in SQL, with per-sub-query degradation.
- AI diagnosis providers: `openai-api` (API key) and an experimental local `codex-cli` (reuses an existing `codex login`); the model only explains collected evidence and any failure falls back to a rule-only report.
- Deterministic evidence sanitization, clustering, correlation rules, evidence strength, and context budgeting.
- REST endpoints for diagnosis, report lookup, project listing, health checks, and token-protected event ingestion.
- A built-in browser console.
- Rule-only fallback behavior when a model adapter fails.

Spring Boot Starter, Prometheus, Loki, Elasticsearch, OpenTelemetry, Alertmanager, and automated remediation are intentionally deferred.

## Documentation

- [Documentation index](docs/en/README.md)
- [Getting started](docs/en/getting-started.md)
- [Product overview](docs/en/overview.md)
- [Repository design](docs/en/github-repository-design.md)
- [Chinese specification SSOT](docs/zh-CN/specification.md)
- [Chinese development plan](docs/zh-CN/development-plan.md)

## Quick Start

Requirements: JDK 17 and Maven 3.6+.

```bash
mvn -B clean package
java -jar faultpilot-server/target/faultpilot-server-0.1.0.jar
```

Verify the running service:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/projects
```

Open [http://localhost:8080/](http://localhost:8080/) to use the built-in console without manually sending API requests.

Only `faultpilot-server` is an executable Spring Boot service. See the [getting-started guide](docs/en/getting-started.md) for module responsibilities and a complete Mock diagnosis request.

## Security Boundary

FaultPilot is read-only by default:

- It does not execute generated SQL.
- It does not restart services or change configuration.
- Database queries use fixed templates and read-only accounts.
- Redaction is best-effort and must be reviewed before sending production data to a model.

## AI Provider

The current `v0.1.0` implementation supports:

- `mock`
- `openai-api`
- `codex-cli` (experimental, local-only)

Use `openai-api` for online deployments. `codex-cli` only reuses an already-authenticated local Codex CLI and should not be enabled on servers.

## License

[Apache-2.0](LICENSE)
