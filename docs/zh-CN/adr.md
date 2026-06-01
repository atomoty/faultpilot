# 架构决策记录

版本：V1.6
日期：2026-06-01

## ADR-001：助手独立部署

状态：Accepted

决策：助手作为独立 Spring Boot 服务运行，不把 AI 分析嵌入业务应用。

原因：降低业务风险，允许多个 Java 项目共享服务，便于升级和权限隔离。

## ADR-002：规则先行，LLM 解释

状态：Accepted

决策：聚类、阈值、时间窗口、证据强度和安全限制由确定性代码执行。LLM 负责解释和建议。

原因：排障场景不能依赖模型自信表达代替证据。

## ADR-003：MVP 使用 Spring JDBC

状态：Accepted

决策：助手自身持久化和 JDBC 适配器优先使用 Spring JDBC。

原因：SQL 明确、查询只读、映射简单，减少 ORM 带来的额外抽象。

## ADR-004：MVP 默认 H2

状态：Accepted

决策：默认使用 H2，部署可切换 PostgreSQL 或 MySQL。

原因：GitHub 用户可以零依赖启动。报告 JSON 字段是已知技术债，在历史案例检索前处理。

## ADR-005：MVP 不开发 Web 前端

状态：Accepted

决策：第一版通过 REST API、README 命令和结构化 JSON 演示。

原因：优先验证证据采集、规则分析和 AI 报告链路。

## ADR-006：MVP 只正式支持 OpenAI API

状态：Accepted

决策：正式模型接入使用 API Key。`codex-cli` 仅保留实验性本地设计，不进入 MVP DoD。

原因：Codex 官方定位为编码代理。官方没有明确说明个人 Codex 登录态可作为第三方通用排障后端自动化调用。

## ADR-007：Starter 延后

状态：Accepted

决策：MVP 冻结 Ingestion 协议，但不开发 Spring Boot Starter。

原因：外部数据源已足以闭环。Starter 应在服务端协议稳定后实现。

## ADR-008：证据强度代替模型置信度

状态：Accepted

决策：报告展示规则计算的 `EvidenceStrength`，不展示模型自评概率。

原因：模型自评置信度未经校准，容易误导用户。

## ADR-009：文档使用 SSOT

状态：Accepted

决策：`docs/specification.md` 是单一事实源，其他文档只面向不同读者展开。

原因：仓库结构、范围和配置在多文档复制后已经出现分叉。
