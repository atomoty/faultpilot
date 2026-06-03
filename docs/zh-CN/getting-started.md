[English](../en/getting-started.md) | [简体中文](getting-started.md)

# 构建、启动与验证

FaultPilot 当前已提供可运行的 Mock 模式排障闭环、本地日志文件分析（见 [examples/local-file](../../examples/local-file/README.md)）、JDBC 日志表分析（见 [examples/jdbc-log-table](../../examples/jdbc-log-table/README.md)）、只读 MySQL/PostgreSQL 诊断（见 [examples/mysql-local](../../examples/mysql-local/README.md) 与 [examples/postgres-local](../../examples/postgres-local/README.md)），以及通过 OpenAI API 或实验性本地 Codex CLI 的真实 AI 诊断。

## 模块说明

| 模块 | 职责 | 是否单独启动 |
| --- | --- | --- |
| `faultpilot-core` | 领域模型、脱敏、规则分析、证据采集和上下文预算 | 否 |
| `faultpilot-adapters` | Mock 适配器，以及后续日志、数据库和模型适配器 | 否 |
| `faultpilot-server` | 组合前两个模块并提供 REST API 的 Spring Boot 服务 | 是 |

Maven 会为三个模块生成 JAR 文件，但只有 `faultpilot-server` 是可独立运行的服务。

## 环境要求

- JDK 17
- Maven 3.6+

检查本机环境：

```bash
java -version
mvn -version
```

macOS 如果没有正确配置 `JAVA_HOME`，可选择本机已安装的 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## 构建

在仓库根目录执行：

```bash
mvn -B clean package
```

运行完整测试：

```bash
mvn -B clean verify
```

## 启动服务

运行打包后的 Spring Boot JAR：

```bash
java -jar faultpilot-server/target/faultpilot-server-0.1.0.jar
```

也可以直接通过 Maven 启动：

```bash
mvn -pl faultpilot-server -am spring-boot:run
```

默认配置会以 Mock 模式在 `http://localhost:8080` 启动服务。

事件写入默认要求 Token。如需进行仅限本机的事件写入演示，启动时显式关闭校验：

```bash
java -jar faultpilot-server/target/faultpilot-server-0.1.0.jar \
  --faultpilot.ingestion.require-token=false
```

线上部署禁止使用该参数。

## 验证服务

检查健康状态：

```bash
curl http://localhost:8080/actuator/health
```

预期响应：

```json
{"status":"UP"}
```

查询已经配置的项目：

```bash
curl http://localhost:8080/api/v1/projects
```

在浏览器中打开内置测试页面：

[http://localhost:8080/](http://localhost:8080/)

运行内置的本地慢 SQL 场景：

```bash
curl -X POST http://localhost:8080/api/v1/diagnoses \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "order-service",
    "environment": "local",
    "question": "为什么订单接口变慢了？",
    "from": "2026-06-01T01:00:00Z",
    "to": "2026-06-01T03:00:00Z"
  }'
```

返回的 JSON 报告应包含 `slow-sql-pool-contention` 根因候选，并引用对应证据。

前台运行时，使用 `Ctrl+C` 停止服务。

## AI 提供方

`faultpilot.ai.provider` 决定报告如何生成。任何提供方失败（网络、超时、非法输出）都会回退为规则降级报告，请求不会失败。

- **`mock`**（默认）：确定性、无网络，用于演示和测试。
- **`openai-api`**：通过 OpenAI API 真实诊断，环境变量配置：
  ```bash
  export OPENAI_API_KEY=sk-...
  export OPENAI_MODEL=gpt-4o-mini        # 需支持 json_schema 响应格式的模型
  mvn -pl faultpilot-server spring-boot:run --faultpilot.ai.provider=openai-api
  ```
  `base-url` 可指向兼容网关（`faultpilot.ai.base-url`）。API Key 仅放入 Authorization 头，不写日志。
- **`codex-cli`**（实验性、仅本地）：复用本机已登录的 Codex CLI。
  ```bash
  codex login            # 由你自己执行一次
  mvn -pl faultpilot-server spring-boot:run --faultpilot.ai.provider=codex-cli
  ```
  助手只在只读沙箱中调用 `codex exec`；不执行 `codex login`，也不读取/复制/打印 Codex 凭据文件。
  **线上部署不要启用 `codex-cli`**，线上请用带 Key 的 `openai-api`。

模型只会拿到已脱敏的证据上下文，根因证据强度由规则计算，不由模型决定。

## Java 项目需要准备什么

MVP 计划支持不嵌入 SDK 的接入方式：

1. 将应用日志写入 FaultPilot 可读取的本地文件，或提供只读 JDBC 日志表。
2. 如需数据库诊断，为 MySQL 或 PostgreSQL 创建专用只读账号。
3. 如需慢 SQL 汇总，确保数据库慢查询统计能力已启用。
4. 生产数据发送给模型前，复查脱敏后的 AI 请求内容。

完整范围以 [统一规格 SSOT](specification.md) 为准。
