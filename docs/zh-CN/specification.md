# Java AI 故障排查与监控助手统一规格

版本：V1.6
日期：2026-06-01
状态：开发前评审基线
仓库名：`faultpilot`

> 本文档是项目的单一事实源（SSOT）。需求、设计、开发计划和 README 只做面向不同读者的展开；如有冲突，以本文档为准。

## 1. 产品定位

本项目是面向 Java 应用的轻量级 AI 故障排查与监控助手。它不是完整可观测平台，也不尝试替代 Prometheus、Loki、Elasticsearch、OpenTelemetry 或专业 AIOps 平台。

第一版聚焦：

```text
本地或授权数据源
  → 只读采集日志和数据库证据
  → 规则分析、聚类和关联
  → AI 解释与排查建议
  → 可审计的结构化报告
```

### 1.1 品牌名称

项目品牌为 `FaultPilot`，GitHub 仓库名为 `faultpilot`。名称表达“故障排查副驾驶”：聚焦证据分析和人工决策辅助，不暗示自动执行运维操作。

MVP 的准确描述是：

> Java AI Troubleshooting and Monitoring Assistant

README 第一屏必须明确这是轻量排障助手，不宣称已经具备完整 AIOps 能力。异常检测、告警降噪和趋势分析属于后续路线。

## 2. 差异化

本项目不与大型平台比拼数据源数量。它解决的是普通 Java 团队的接入门槛：

| 维度 | 本项目 |
| --- | --- |
| 目标用户 | 希望快速接入的 Java 团队 |
| 本地开发 | 无外部监控平台也可读取本地日志和数据库 |
| 旧项目 | 支持 JDBC 日志表和只读数据库统计查询 |
| 接入方式 | L1、L2、L3 渐进接入 |
| 部署 | 本地运行或独立部署 |
| 风险控制 | 只读固定 SQL、脱敏、审计、无自动处置 |
| AI 定位 | 解释已收集证据，不替代确定性规则 |

详细竞品对比见 `docs/competitive-analysis.md`。

## 3. AI 价值边界

### 3.1 规则负责

- 时间范围、条数和证据规模限制。
- 日志脱敏、截断、去重和堆栈归一化。
- 同类日志聚类和频次统计。
- 慢 SQL 模板归一化和耗时统计。
- 数据库连接、长事务和锁等待识别。
- 发布事件与异常窗口的时间关联。
- 数据源不可用时降级。

### 3.2 AI 负责

- 理解用户自然语言问题，映射到已授权证据类型。
- 将多个规则结果组织为可读的故障叙述。
- 在证据不足时明确指出缺口。
- 生成多个根因假设及对应证据引用。
- 给出有顺序、可人工执行的下一步排查建议。
- 将技术证据转换为适合研发、运维或管理人员阅读的摘要。

### 3.3 AI 不负责

- 凭空判断根因。
- 自由生成并执行 SQL。
- 修改配置、重启服务或回滚。
- 将模型自评概率当作经过校准的真实概率。

AI 输出必须标注：

> AI 生成的排障建议，仅供人工核验。请在执行任何变更前复查证据。

## 4. MVP 范围冻结

### 4.1 必须实现

- Java 17、Spring Boot 3.x、Maven。
- 独立运行的助手服务。
- YAML 项目注册。
- 本地日志文件读取与多行堆栈解析。
- JDBC 日志表只读查询。
- MySQL 8.x 和 PostgreSQL 14+ 本地数据库只读分析。
- 慢 SQL 摘要、连接状态、长事务和可用时的锁等待。
- 脱敏、截断、聚类、规则分析和审计。
- Mock 模型和 OpenAI API 模型。
- 规则降级报告。
- REST API、H2 默认存储、Dockerfile、Docker Compose。
- 两个带期望根因的演示案例。
- 中英文 README 和中英文文档导航。

### 4.2 不进入 MVP

- Spring Boot Starter 实际代码。
- `codex-cli` 实验性模型适配器代码。
- Prometheus、Loki、Elasticsearch、OpenTelemetry 正式适配器。
- Alertmanager 自动触发。
- Web 前端。
- 向量数据库和历史案例检索。
- 自动处置。

## 5. 运行模式

| 模式 | 数据来源 | AI 提供方 | 用途 |
| --- | --- | --- | --- |
| Mock | 内置虚构数据 | Mock | 零依赖演示和测试 |
| 本地 | 本地日志、JDBC、本地数据库 | Mock 或 OpenAI API | 本地联调 |
| 独立部署 | 只读日志挂载、授权数据库、后续监控平台 | OpenAI API | 线上检测 |

实验性 `codex-cli` 方案仅保留为文档化实验设计，不作为 MVP 能力承诺。

## 6. 核心诊断流程

```text
DiagnosisRequest
  → 校验项目、环境、时间范围
  → 并行查询日志、数据库状态、慢 SQL、事件
  → 各证据源独立超时和降级
  → 脱敏、截断、归一化
  → RuleAnalyzer 聚类和关联
  → ContextBudgeter 控制模型输入
  → OpenAI API 或 Mock 模型
  → JSON Schema 校验
  → 失败时生成规则降级报告
  → 保存报告和审计记录
```

## 7. RuleAnalyzer 契约

### 7.1 时间规范

- 内部统一使用 UTC `Instant`。
- API 接受 ISO 8601 时区偏移。
- 报告按用户请求时区展示。
- 证据源缺少时区时，必须在项目配置中声明默认时区。

### 7.2 关联键

按优先级关联：

1. `traceId`
2. `requestUri + SQL 来源 + 时间窗口`
3. `service/module + exceptionClass + 时间窗口`
4. 仅时间窗口弱关联

弱关联必须标记为 `TEMPORAL_ONLY`，不得表述为确定因果关系。

### 7.3 默认关联窗口

| 场景 | 默认窗口 |
| --- | --- |
| 发布事件与异常 | 发布前 10 分钟至发布后 60 分钟 |
| 慢接口与慢 SQL | 同一 `traceId`；无 `traceId` 时前后 30 秒 |
| 慢 SQL 摘要与指标异常 | 无请求级 `traceId` 时前后 10 分钟，仅作为时间相关证据 |
| 日志与数据库异常 | 前后 60 秒 |

窗口可按项目覆盖。

### 7.4 日志聚类键

```text
projectId
+ environment
+ normalizedExceptionClass
+ normalizedMessageTemplate
+ topStackFrame
```

归一化时替换 UUID、数字 ID、时间戳、IP、邮箱和长十六进制串。

### 7.5 突增规则

MVP 使用可解释规则：

```text
currentCount >= absoluteThreshold
AND currentCount >= baselineCount * ratioThreshold
```

默认：

| 项目 | 默认值 |
| --- | --- |
| 当前窗口 | 10 分钟 |
| 基线窗口 | 过去 60 分钟，不含当前窗口 |
| 绝对阈值 | 5 |
| 倍率阈值 | 3.0 |

无足够基线时，只使用绝对阈值，并在报告中标记。

## 8. 模型上下文预算

### 8.1 默认预算

| 内容 | 上限 |
| --- | ---: |
| 模型输入总预算 | 12,000 tokens |
| 系统提示和 JSON Schema 预留 | 2,500 tokens |
| 用户问题和元数据 | 500 tokens |
| 日志聚类 | 最多 20 类，每类 1 个代表样本 |
| 单个日志样本 | 最多 1,200 字符 |
| 单个堆栈 | 最多 12 行 |
| 慢 SQL | 最多 15 个模板 |
| 指标异常 | 最多 20 条摘要 |
| 变更事件 | 最多 20 条 |
| 模型输出 | 最多 2,000 tokens |

### 8.2 超限策略

按以下顺序保留：

1. 与用户问题关键词或 `traceId` 直接相关的证据。
2. 严重级别更高的证据。
3. 频次和耗时更高的证据。
4. 与发布事件更接近的证据。

被截断的数量必须写入审计日志和报告元数据。

## 9. 时延预算

目标：正常情况下 60 秒内返回初步报告。

| 阶段 | 预算 |
| --- | ---: |
| 参数校验和项目加载 | 1 秒 |
| 并行证据采集 | 10 秒 |
| 脱敏、聚类和规则分析 | 4 秒 |
| OpenAI API 调用 | 35 秒 |
| JSON 校验、持久化和响应 | 5 秒 |
| 预留 | 5 秒 |

- 所有证据适配器并行执行。
- 单个适配器默认超时 5 秒，数据库连接超时 2 秒，查询超时 3 秒。
- 模型超时 35 秒。
- 超时后返回已有证据生成的规则降级报告。

## 10. 置信度语义

报告不展示模型自评概率。根因候选使用 `EvidenceStrength`：

| 值 | 规则 |
| --- | --- |
| `STRONG` | 至少 2 个独立证据源，且存在 `traceId` 或明确规则命中 |
| `MODERATE` | 至少 2 个独立证据源，仅时间窗口或模块相关 |
| `WEAK` | 单一证据源或证据不足 |

AI 可以生成解释，但 `EvidenceStrength` 由规则计算。

## 11. AI 报告质量评估

每个演示案例必须定义：

- 预期根因标签。
- 必须引用的证据 ID。
- 禁止出现的无证据结论。
- 可接受的排查建议关键词。

MVP 验收：

| 指标 | 目标 |
| --- | ---: |
| JSON Schema 合法率 | 100% |
| 两个演示案例根因标签命中率 | 100% |
| 必须证据引用完整率 | 100% |
| 无证据根因数量 | 0 |
| 模型失败后的规则降级报告可用率 | 100% |

## 12. 脱敏与模型数据边界

- 脱敏属于 best-effort，不承诺零泄漏。
- 默认使用字段名规则、正则和 SQL 参数化。
- 进入模型前保存可审计的 payload 摘要、哈希、字段计数和截断统计。
- 默认不落盘完整模型 payload。
- 本地调试可显式开启加密 payload review 文件，默认关闭并设置短期保留。
- README 必须警告用户：接入真实模型前审查日志内容和数据处理政策。

## 13. Ingestion 安全

`POST /api/v1/ingestion/events:batch` 必须满足：

- Token 绑定 `projectId` 和允许的环境集合。
- 请求中的 `projectId` 与 Token 不匹配时返回 `403`。
- 每个 Token 配置每分钟事件数和单批最大条数。
- 单事件最大 16 KB，单批最大 1 MB。
- 请求包含 `requestId`、`sentAt` 和可选签名。
- 使用 `requestId` 做幂等去重。
- `sentAt` 超过默认 5 分钟窗口时拒绝，降低重放风险。
- 审计记录 Token 标识、项目、结果、数量和拒绝原因。

## 14. 持久化与已知技术债

MVP 使用 H2 和 JSON 字段保存报告，目的是降低启动成本。

已知技术债：

- JSON 字段不利于按根因和证据检索。
- 历史案例检索前需要迁移为可检索结构或增加索引。
- PostgreSQL 部署时可评估 `jsonb` 和向量检索。

该技术债不阻塞 MVP，但必须在历史案例功能前处理。

## 15. AI 提供方

### 15.1 MVP 正式支持

| 提供方 | 场景 |
| --- | --- |
| `mock` | 测试和演示 |
| `openai-api` | 正式本地运行和线上部署 |
| `codex-cli` | 实验性本地运行，不用于服务器或容器 |

### 15.2 Codex CLI 实验性本地模式

`codex-cli` 在 v0.1.0 中作为实验性本地 provider 提供。官方说明 Codex CLI 可以使用 ChatGPT 账号登录，但 Codex 的产品定位是编码代理。官方没有明确说明个人 Codex 登录态可作为第三方通用排障后端自动化调用。

因此：

- 默认禁用。
- 不用于服务器或容器。
- 不读取、复制或展示 Codex 认证文件。
- 使用者必须自行确认符合适用条款。

官方参考：

- [Using Codex with your ChatGPT plan](https://help.openai.com/en/articles/11369540)
- [Codex CLI and Sign in with ChatGPT](https://help.openai.com/en/articles/11381614)
- [Where do I find my OpenAI API Key?](https://help.openai.com/en/articles/4936850-where-do-i-find-my-openai-api-key)

## 16. GitHub 交付

- 英文 `README.md` 和中文 `README.zh-CN.md`。
- `docs/en/` 和 `docs/zh-CN/` 双语导航。
- Apache-2.0 License。
- `CONTRIBUTING.md`、`SECURITY.md`、`CODE_OF_CONDUCT.md`。
- `.github/workflows/ci.yml` 和敏感信息扫描。
- 本地模式和独立部署模式文档。
- 虚构示例数据，不提交真实凭据、IP、域名和日志。

## 17. Definition of Done

- `mvn test` 通过。
- Mock 模式零依赖启动。
- 本地日志、多行堆栈、JDBC 日志表可分析。
- MySQL 和 PostgreSQL 数据库分析样例可运行。
- 两个演示案例的根因标签和必须证据均命中。
- 证据适配器并行执行并满足时延预算。
- 上下文预算和截断统计可审计。
- 脱敏明确标注 best-effort。
- 模型失败时返回规则降级报告。
- Ingestion Token 项目绑定、限流、幂等和时间窗口测试通过。
- README 提供中英文切换、本地运行和独立部署说明。
- 仓库敏感信息扫描通过。
