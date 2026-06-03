# FaultPilot Java 故障排查与监控助手开发实施方案

版本：V1.6
日期：2026-06-01
目标：文档评审通过后可直接进入编码阶段

> 对应统一规格：`docs/specification.md` V1.6。发生冲突时以统一规格为准。

## 1. 开发目标

实现一个可以独立启动、可以公开提交到 GitHub、可以在无外部依赖条件下完成演示的 Java AI 排障助手 MVP。

第一版验证的是诊断主链路，不追求一次性接入所有监控平台。

## 2. 仓库结构

```text
faultpilot/
├── README.md
├── README.zh-CN.md
├── LICENSE
├── CONTRIBUTING.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── .env.example
├── .github/
│   ├── workflows/
│   └── ISSUE_TEMPLATE/
├── pom.xml
├── docker-compose.yml
├── docs/
│   ├── en/
│   └── zh-CN/
├── faultpilot-server/
│   ├── pom.xml
│   └── src/
├── examples/
│   ├── demo-data/
│   ├── local-file/
│   ├── jdbc-log-table/
│   ├── mysql-local/
│   └── postgres-local/
└── faultpilot-spring-boot-starter/
    └── README.md
```

`faultpilot-spring-boot-starter` 在 MVP 中仅保留 README 和协议说明，不实现代码。

## 3. 服务端包结构

```text
io.github.atomoty.faultpilot
├── controller
│   ├── DiagnosisController
│   └── EventController
├── service
│   ├── DiagnosisService
│   ├── EvidenceCollector
│   ├── EvidenceSanitizer
│   ├── RuleAnalyzer
│   └── ReportFallbackService
├── adapter
│   ├── log
│   │   ├── LogSourceAdapter
│   │   ├── MockLogSourceAdapter
│   │   ├── LocalFileLogSourceAdapter
│   │   └── JdbcLogSourceAdapter
│   ├── metric
│   │   └── MockMetricSourceAdapter
│   ├── slowsql
│   │   ├── MockSlowSqlSourceAdapter
│   │   ├── MysqlSlowSqlSourceAdapter
│   │   └── PostgresSlowSqlSourceAdapter
│   ├── database
│   │   ├── DatabaseHealthSourceAdapter
│   │   ├── MysqlDatabaseHealthSourceAdapter
│   │   └── PostgresDatabaseHealthSourceAdapter
│   └── model
│       ├── DiagnosisModel
│       ├── MockDiagnosisModel
│       ├── OpenAiApiDiagnosisModel
├── config
│   ├── FaultPilotProperties
│   └── ProjectRegistry
├── model
├── repository
└── security
```

## 4. MVP API 清单

### 4.1 健康检查

```http
GET /actuator/health
```

### 4.2 查询已注册项目

```http
GET /api/v1/projects
```

### 4.3 发起诊断

```http
POST /api/v1/diagnoses
```

请求：

```json
{
  "projectId": "order-service",
  "environment": "local",
  "question": "为什么订单创建接口变慢？",
  "from": "2026-06-01T09:00:00+08:00",
  "to": "2026-06-01T11:00:00+08:00"
}
```

### 4.4 查询诊断报告

```http
GET /api/v1/diagnoses/{diagnosisId}
```

### 4.5 写入变更事件

```http
POST /api/v1/events
```

请求：

```json
{
  "projectId": "order-service",
  "environment": "local",
  "type": "DEPLOYMENT",
  "occurredAt": "2026-06-01T09:32:00+08:00",
  "attributes": {
    "version": "v1.4.2"
  }
}
```

### 4.6 Starter 批量上报协议

```http
POST /api/v1/ingestion/events:batch
```

MVP 固定协议但不要求实现 Starter SDK。服务端可以复用事件写入逻辑接收该请求。

## 5. 核心数据模型

| 模型 | 责任 |
| --- | --- |
| `ProjectDefinition` | 项目、环境和数据源配置 |
| `DiagnosisRequest` | 用户诊断请求 |
| `DiagnosisTask` | 诊断任务状态 |
| `Evidence` | 标准化证据基类 |
| `LogEvent` | 标准日志事件 |
| `LogCluster` | 聚类后的日志摘要 |
| `MetricAnomaly` | 指标异常摘要 |
| `SlowSqlSummary` | 慢 SQL 模板和耗时统计 |
| `DatabaseHealthSnapshot` | 数据库连接、长事务、锁等待和数据源可用性 |
| `ChangeEvent` | 发布、回滚和配置变更 |
| `DiagnosisContext` | 提交给模型的结构化上下文 |
| `DiagnosisReport` | 返回给用户的结构化报告 |
| `EvidenceStrength` | 由规则计算的证据强度，不使用模型自评概率 |

## 6. 实现顺序

### 6.1 里程碑一：项目骨架和 Mock 闭环

- 创建 Maven 多模块项目。
- 创建 Spring Boot 服务端。
- 增加 H2 和数据库初始化脚本。
- 实现 YAML 项目注册。
- 实现诊断 API。
- 实现 Mock 证据适配器和 Mock 模型。
- 返回结构化诊断报告。
- 增加适配器并行采集骨架和单源超时。

验证：执行一次请求，得到完整报告。

### 6.2 里程碑二：真实本地日志

- 实现 `LocalFileLogSourceAdapter`。
- 解析常见时间、级别、线程、Logger 和消息字段。
- 支持多行异常堆栈。
- 增加强制时间范围和最大条数。
- 实现脱敏、截断、去重和日志聚类。

验证：读取本地 Java 项目日志，生成异常聚类报告。

### 6.3 里程碑三：JDBC 日志表

- 实现 `JdbcLogSourceAdapter`。
- 使用项目配置定义只读 SQL 模板和字段映射。
- 使用参数绑定传递时间范围和最大条数。
- 增加 SQL 注入和查询范围测试。

验证：读取示例日志表，结果转换为统一 `LogEvent`。

### 6.4 里程碑四：本地数据库分析

- 实现 MySQL 只读数据库状态适配器。
- 实现 PostgreSQL 只读数据库状态适配器。
- 从统计视图读取慢 SQL 摘要。
- 增加连接超时、查询超时和权限不足降级处理。
- 禁止执行写操作、终止连接和模型生成 SQL。

验证：在本地数据库中识别慢 SQL 摘要、连接状态和可用的锁等待信息。

### 6.5 里程碑五：模型接入

- 实现 OpenAI API HTTP 客户端。
- 实现 `openai-api` 配置模式。
- 实现实验性本地 `codex-cli` provider，默认禁用且仅用于本地。
- 固定模型输入和输出 JSON Schema。
- 实现 12,000 tokens 上下文预算、采样和截断统计。
- 模型调用默认超时 35 秒。
- 增加超时、非法 JSON 和服务不可用降级处理。
- 默认仍保留 Mock 模型。

验证：Mock 和真实模型模式均返回合法报告。

### 6.6 里程碑六：开源交付

- 增加 Dockerfile 和 Docker Compose。
- 编写 README、接入指南和演示脚本。
- 编写中英文 README 和中英文详细文档。
- 在 README 顶部增加语言切换链接。
- 增加 `CONTRIBUTING.md`、`SECURITY.md` 和 `CODE_OF_CONDUCT.md`。
- 增加 `.env.example`。
- 扫描敏感信息。
- 准备两个演示案例。

验证：新环境仅按照 README 即可运行 Demo。

## 7. 演示案例

### 7.1 发布后异常增加

输入：查询订单服务发布后一小时内的错误。

证据：

- 新版本发布事件。
- `NullPointerException` 数量明显增加。
- 异常集中于同一模块。

输出：发布可能引入空值处理问题，建议检查对应提交并人工评估回滚。

### 7.2 慢接口关联慢 SQL

输入：查询订单创建接口变慢原因。

证据：

- 接口 P95 从 300ms 增长到 2.8s。
- 相同 SQL 模板平均耗时达到 2.1s。
- 数据库连接池等待数增加。

输出：慢 SQL 可能导致连接池等待，建议人工运行 `EXPLAIN` 并评估索引。

## 8. Definition of Done

MVP 只有满足以下条件才算完成：

- `mvn test` 通过。
- Mock 模式不依赖外部服务即可启动。
- API Key 模式可以调用 OpenAI API。
- 本地文件日志和 JDBC 日志表均有可运行示例。
- MySQL 和 PostgreSQL 至少各有一个数据库分析测试样例。
- 数据库连接失败、统计视图缺失或权限不足时，其他诊断流程仍可返回报告。
- 两个演示案例均返回结构化报告。
- 两个演示案例的预期根因标签和必须证据均命中。
- 根因候选使用规则计算的 `EvidenceStrength`。
- 适配器并行执行，正常情况下 60 秒内返回初步报告。
- Ingestion Token 项目绑定、限流、幂等和时间窗口测试通过。
- 超范围查询被拒绝或截断。
- 敏感字段在发送给模型前被脱敏。
- 模型不可用时返回规则分析降级报告。
- README 包含启动、配置、调用和接入说明。
- 仓库不包含真实密钥、内部域名、IP 和真实业务日志。

## 9. MVP 完成后的下一步

- 实现 Prometheus 指标适配器。
- 实现 Alertmanager Webhook。
- 实现 Loki 或 Elasticsearch 日志适配器。
- 实现可选 Spring Boot Starter。
- 再评估 OpenTelemetry Trace 和历史案例检索。
