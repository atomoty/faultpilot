[English](../en/getting-started.md) | [简体中文](getting-started.md)

# 接入准备

FaultPilot 当前是文档预览版。首个可运行版本发布后，本页会补充实际启动命令。

## Java 项目需要准备什么

MVP 计划支持不嵌入 SDK 的接入方式：

1. 将应用日志写入 FaultPilot 可读取的本地文件，或提供只读 JDBC 日志表。
2. 如需数据库诊断，为 MySQL 或 PostgreSQL 创建专用只读账号。
3. 如需慢 SQL 汇总，确保数据库慢查询统计能力已启用。
4. 生产数据发送给模型前，复查脱敏后的 AI 请求内容。

完整范围以 [统一规格 SSOT](specification.md) 为准。
