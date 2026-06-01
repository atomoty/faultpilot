[English](overview.md) | [简体中文](../zh-CN/specification.md)

# FaultPilot Overview

FaultPilot is a lightweight AI troubleshooting and monitoring assistant for Java applications.

## Positioning

FaultPilot is not a replacement for Prometheus, Loki, Elasticsearch, OpenTelemetry, or full AIOps platforms. It focuses on a smaller adoption path:

```text
Authorized local or remote evidence
  -> read-only collection
  -> deterministic normalization, clustering, and correlation
  -> bounded AI explanation
  -> auditable troubleshooting report
```

## AI Boundary

Deterministic rules handle redaction, truncation, clustering, thresholds, evidence strength, and fallback reports. AI explains evidence, highlights missing context, proposes hypotheses, and suggests human-verifiable next steps.

## MVP

- Local log files and multi-line stack traces.
- JDBC log tables.
- Read-only MySQL and PostgreSQL diagnostics.
- Slow SQL summaries from database statistics.
- Mock and OpenAI API providers.
- No generated SQL execution and no automated remediation.

For the full reviewed specification, see the [Chinese SSOT](../zh-CN/specification.md).
