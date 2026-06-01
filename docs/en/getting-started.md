[English](getting-started.md) | [简体中文](../zh-CN/getting-started.md)

# Getting Started

FaultPilot is currently a documentation-only pre-alpha release. Runtime setup commands will be added with the first implementation release.

## Prepare Your Java Application

The planned MVP can analyze an application without embedding an SDK:

1. Write application logs to a readable local file, or expose a read-only JDBC log table.
2. Create a dedicated read-only MySQL or PostgreSQL account if database diagnostics are required.
3. Keep database slow-query statistics enabled when slow SQL summaries are required.
4. Review the redacted AI payload before using production data with a model provider.

See the [product overview](overview.md) and [Chinese SSOT](../zh-CN/specification.md) for the reviewed scope.
