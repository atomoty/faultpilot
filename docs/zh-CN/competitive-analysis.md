# 竞品与差异化分析

版本：V1.6
日期：2026-06-01

## 1. 参照项目

| 项目 | 已有能力 | 本项目不重复建设的部分 | 本项目切入点 |
| --- | --- | --- | --- |
| Elastic AI for Observability | 基于 Elastic 数据的日志解释、告警摘要、时间线、查询和可视化 | 不构建完整 Elastic 体验 | 不要求先部署 Elastic；服务普通 Java 项目 |
| Grafana Assistant Investigations | 跨指标、日志、Trace、Profile 和 SQL 的多 Agent 调查 | 不与 Grafana Cloud 比拼跨源 Agent 能力 | 本地零依赖启动；JDBC 和日志文件优先 |
| Coroot AI RCA | 先用 ML 和依赖图识别根因，再由 LLM 解释结论 | 不在 MVP 构建 eBPF、依赖图和完整 RCA | 学习其“规则先行、LLM 解释”边界 |
| HolmesGPT | 连接 Prometheus 和 Alertmanager 调查告警 | 不在 MVP 构建 Kubernetes 和告警生态 | Java 项目本地联调和旧系统接入 |

## 2. 差异化结论

README 第一屏需要明确：

> This project is not a full observability platform. It is a lightweight Java-first troubleshooting assistant that can start from local logs and read-only database access, then grow with your observability stack.

中文：

> 本项目不是完整可观测平台，而是 Java 优先的轻量排障助手。无需先搭建完整监控体系，可以从本地日志和只读数据库开始，再逐步接入现有可观测平台。

## 3. 对产品设计的影响

- 不宣称自动发现全部根因。
- 不让 LLM 代替确定性规则。
- MVP 必须做好本地文件、JDBC 和数据库只读分析。
- Prometheus、Loki、Trace 属于增强接入。
- 报告必须包含证据引用和证据强度，而不是模型自评置信度。

## 4. 参考资料

- [Elastic AI Assistant for Observability and Search](https://www.elastic.co/docs/solutions/observability/observability-ai-assistant/)
- [Grafana Assistant Investigations](https://grafana.com/docs/grafana-cloud/machine-learning/assistant/introduction/investigations/)
- [Coroot AI-powered Root Cause Analysis](https://docs.coroot.com/ai/overview/)
- [HolmesGPT Prometheus Alert Investigation](https://holmesgpt.dev/walkthrough/investigating-prometheus-alerts/)
