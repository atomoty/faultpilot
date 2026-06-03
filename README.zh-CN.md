[English](README.md) | [简体中文](README.zh-CN.md)

# FaultPilot

FaultPilot 是一个默认只读、面向 Java 应用的 AI 故障排查与监控助手。

> 状态：早期 `v0.1.0` 开发版本。当前已提供可运行的 Mock 模式排障闭环、本地日志文件分析、JDBC 日志表分析、只读 MySQL/PostgreSQL 诊断,以及通过 OpenAI API 的真实 AI 诊断(另含实验性本地 Codex CLI 方式)。

## 为什么做 FaultPilot

FaultPilot 不是完整可观测平台，而是 Java 优先的轻量排障助手。无需先搭建完整监控体系，可以从本地日志文件和只读数据库开始，再逐步接入已有可观测平台。

项目目标：

- 分析 Java 应用日志和多行异常堆栈。
- 通过只读账号分析 MySQL 或 PostgreSQL。
- 汇总慢 SQL、长事务、连接状态和锁等待。
- 先用确定性规则关联证据，再让 AI 解释。
- 输出可审计、带证据引用、需要人工核验的报告。
- 支持本地运行和独立部署。

## 当前实现

当前 `v0.1.0` 已实现：

- 可运行的 Mock 模式诊断闭环。
- 本地日志文件分析：多行堆栈聚合、时间范围与级别过滤、按项目配置行正则、有界扫描。
- JDBC 日志表分析：只读、参数绑定查询规范视图，视图名防注入，读失败标记数据源不可用。
- 只读 MySQL/PostgreSQL 诊断：连接状态、长事务、锁等待和参数化慢 SQL 摘要，全部内置固定 SQL，子查询级降级。
- AI 诊断方式：`openai-api`(API Key)与实验性本地 `codex-cli`(复用已有 `codex login`)；模型只解释已采集证据，任何失败回退为规则降级报告。
- 确定性的证据脱敏、日志聚类、关联规则、证据强度和上下文预算。
- 诊断、报告查询、项目列表、健康检查和 Token 保护的事件写入 REST API。
- 内置浏览器测试页面。
- 模型适配器异常时的规则降级报告。

Spring Boot Starter、Prometheus、Loki、Elasticsearch、OpenTelemetry、Alertmanager 和自动处置明确延后。

## 文档

- [文档索引](docs/zh-CN/README.md)
- [启动与验证](docs/zh-CN/getting-started.md)
- [统一规格 SSOT](docs/zh-CN/specification.md)
- [系统设计](docs/zh-CN/design.md)
- [开发实施方案](docs/zh-CN/development-plan.md)
- [闭环自证](docs/zh-CN/closure-review.md)
- [GitHub 仓库设计](docs/zh-CN/github-repository-design.md)

## 快速启动

环境要求：JDK 17、Maven 3.6+。

```bash
mvn -B clean package
java -jar faultpilot-server/target/faultpilot-server-0.1.0.jar
```

验证服务：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/projects
```

打开 [http://localhost:8080/](http://localhost:8080/) 即可使用内置测试页面，无需手动发送 API 请求。

只有 `faultpilot-server` 是可独立启动的 Spring Boot 服务。模块说明和完整 Mock 诊断请求见[启动与验证](docs/zh-CN/getting-started.md)。

## 安全边界

FaultPilot 默认只读：

- 不执行模型生成的 SQL。
- 不重启服务，不修改配置。
- 数据库使用固定查询模板和只读账号。
- 脱敏属于 best-effort，生产数据发送给模型前必须复查。

## AI 提供方

当前 `v0.1.0` 已支持：

- `mock`
- `openai-api`
- `codex-cli`（实验性，仅本地）

线上部署使用 `openai-api`。`codex-cli` 只复用本机已登录的 Codex CLI，不应在服务器上启用。

## License

[Apache-2.0](LICENSE)
