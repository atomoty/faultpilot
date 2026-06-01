[English](README.md) | [简体中文](README.zh-CN.md)

# FaultPilot

FaultPilot is a read-only AI troubleshooting and monitoring assistant for Java applications.

> Status: Planning / Pre-alpha (`v0.0.1`). This repository currently contains the reviewed specification and implementation plan. Runtime code is not available yet.

## Why FaultPilot

FaultPilot is not a full observability platform. It is a lightweight, Java-first troubleshooting assistant that can start with local log files and read-only database access, then grow with your observability stack.

The project is designed to:

- Analyze Java application logs and multi-line stack traces.
- Inspect MySQL or PostgreSQL through read-only accounts.
- Summarize slow SQL, long transactions, connection status, and lock waits.
- Correlate evidence with deterministic rules before asking AI to explain it.
- Produce auditable reports with evidence references and human-verification warnings.
- Run locally or as an independently deployed service.

## MVP Scope

The first runnable version will include:

- Local file log analysis.
- JDBC log-table analysis.
- Read-only MySQL 8.x and PostgreSQL 14+ diagnostics.
- Mock mode for zero-dependency demos.
- OpenAI API integration for AI-generated explanations.
- Rule-based fallback reports when the model is unavailable.

Spring Boot Starter, Prometheus, Loki, Elasticsearch, OpenTelemetry, Alertmanager, and automated remediation are intentionally deferred.

## Documentation

- [Documentation index](docs/en/README.md)
- [Product overview](docs/en/overview.md)
- [Repository design](docs/en/github-repository-design.md)
- [Chinese specification SSOT](docs/zh-CN/specification.md)
- [Chinese development plan](docs/zh-CN/development-plan.md)

## Security Boundary

FaultPilot is read-only by default:

- It does not execute generated SQL.
- It does not restart services or change configuration.
- Database queries use fixed templates and read-only accounts.
- Redaction is best-effort and must be reviewed before sending production data to a model.

## AI Provider

The MVP formally supports:

- `mock`
- `openai-api`

An experimental local Codex CLI design is documented for future evaluation but is disabled by default and is not part of the MVP.

## License

[Apache-2.0](LICENSE)
