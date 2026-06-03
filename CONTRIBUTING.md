# Contributing to FaultPilot

FaultPilot is in early implementation. Contributions should keep the MVP narrow and preserve the read-only security boundary.

## Before Opening a Pull Request

1. Read [the specification](docs/zh-CN/specification.md) and [architecture decisions](docs/zh-CN/adr.md).
2. Avoid adding generated SQL execution, remote shell access, or automated remediation.
3. Do not commit real logs, credentials, internal domains, or IP addresses.
4. Update both English and Chinese navigation when adding documentation.
5. Run `mvn -B clean verify`, then run the documentation and secret checks.

## Commit Style

Use concise conventional prefixes such as:

```text
docs: add local database integration notes
feat: add local file log adapter
test: cover redaction fallback
```
