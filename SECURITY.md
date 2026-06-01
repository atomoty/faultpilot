# Security Policy

## Supported Versions

FaultPilot is currently pre-alpha. Security fixes are applied to the latest branch only.

## Reporting a Vulnerability

Please report security issues privately through GitHub Security Advisories after the public repository is created. Do not open a public issue for suspected vulnerabilities.

## Security Boundary

FaultPilot is designed to be read-only:

- Database access must use read-only accounts.
- SQL queries must use fixed templates.
- Model output must never be executed as SQL or shell commands.
- Redaction is best-effort. Review production-data handling before enabling an external AI provider.
