# GitHub Repository Design for Java AI Troubleshooting Assistant

Version: V1.6
Date: 2026-06-01

> This document describes the target repository design. For capabilities available in the current
> `v0.1.0` development build, see [Build, Run, and Verify](getting-started.md).

## Repository Positioning

Repository name:

```text
faultpilot
```

Description:

> A lightweight AI troubleshooting and monitoring assistant for Java applications. Run locally or deploy independently to analyze logs, database health, slow SQL, and incidents.

## Documentation Languages

The repository provides:

```text
README.md
README.zh-CN.md
docs/en/
docs/zh-CN/
```

Every README and detailed guide includes clickable language-switch links:

```markdown
[English](README.md) | [简体中文](README.zh-CN.md)
```

## Runtime Modes

### Local Mode

- Read log files from a locally running Java application.
- Analyze local MySQL or PostgreSQL through a read-only JDBC account.
- Use Mock or OpenAI API. Codex CLI remains an experimental local-only design and is disabled by default.

### Independent Deployment Mode

- Deploy with Docker Compose or a container platform.
- Analyze read-only log mounts, database statistics, and later observability integrations.
- Use OpenAI API Key authentication only.

## AI Authentication

### OpenAI API

Use environment variables:

```env
OPENAI_API_KEY=
OPENAI_MODEL=
OPENAI_BASE_URL=https://api.openai.com
```

### Experimental Local Codex CLI Design

Users authenticate through Codex CLI:

```bash
codex login
codex login status
```

The assistant invokes local `codex exec`. It does not read, copy, or expose Codex authentication files. This mode is available in v0.1.0 as an experimental local-only provider and is not a generic OAuth flow for server deployments.

Users must verify that their use complies with the applicable OpenAI terms.

## Release Checklist

- No real secrets, internal domains, IP addresses, or business logs.
- `.env.example` contains placeholders only.
- English and Chinese navigation links are present.
- Local and Docker Compose instructions are documented.
- Apache-2.0 license is included.
- `SECURITY.md` documents vulnerability reporting.
- CI runs `mvn test` and secret scanning.
