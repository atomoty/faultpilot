[English](README.md) | [简体中文](README.zh-CN.md)

# FaultPilot

FaultPilot 是一个默认只读、面向 Java 应用的 AI 故障排查与监控助手。

> 状态：规划阶段 / Pre-alpha（`v0.0.1`）。当前仓库提供已经评审的规格与开发方案，尚未提交可运行代码。

## 为什么做 FaultPilot

FaultPilot 不是完整可观测平台，而是 Java 优先的轻量排障助手。无需先搭建完整监控体系，可以从本地日志文件和只读数据库开始，再逐步接入已有可观测平台。

项目目标：

- 分析 Java 应用日志和多行异常堆栈。
- 通过只读账号分析 MySQL 或 PostgreSQL。
- 汇总慢 SQL、长事务、连接状态和锁等待。
- 先用确定性规则关联证据，再让 AI 解释。
- 输出可审计、带证据引用、需要人工核验的报告。
- 支持本地运行和独立部署。

## MVP 范围

第一个可运行版本将包含：

- 本地日志文件分析。
- JDBC 日志表分析。
- MySQL 8.x 和 PostgreSQL 14+ 只读诊断。
- 零依赖演示用 Mock 模式。
- OpenAI API 接入。
- 模型不可用时的规则降级报告。

Spring Boot Starter、Prometheus、Loki、Elasticsearch、OpenTelemetry、Alertmanager 和自动处置明确延后。

## 文档

- [文档索引](docs/zh-CN/README.md)
- [统一规格 SSOT](docs/zh-CN/specification.md)
- [系统设计](docs/zh-CN/design.md)
- [开发实施方案](docs/zh-CN/development-plan.md)
- [闭环自证](docs/zh-CN/closure-review.md)
- [GitHub 仓库设计](docs/zh-CN/github-repository-design.md)

## 安全边界

FaultPilot 默认只读：

- 不执行模型生成的 SQL。
- 不重启服务，不修改配置。
- 数据库使用固定查询模板和只读账号。
- 脱敏属于 best-effort，生产数据发送给模型前必须复查。

## AI 提供方

MVP 正式支持：

- `mock`
- `openai-api`

实验性本地 Codex CLI 方案仅保留为后续评估设计，默认禁用，不属于 MVP。

## License

[Apache-2.0](LICENSE)
