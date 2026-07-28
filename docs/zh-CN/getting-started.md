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

在浏览器中打开内置控制台页面：

[http://localhost:8080/](http://localhost:8080/)

选择项目与环境后点击 **Run Diagnosis**（或点 **Load Mock Demo** 预填内置场景）。生成报告期间，
页面会显示「AI is analyzing collected evidence」加载状态。Mock 模式几乎瞬间返回；使用
`openai-api` 或 `codex-cli` 时，真实模型调用可能耗时数十秒，请保持页面打开直到报告渲染完成。
底部可折叠区域提供原始 JSON 响应。

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

报告和写入的事件会持久化到本地 H2 数据库（默认 `./data/`，可用 `FAULTPILOT_STORE_URL` 覆盖），因此重启后 `GET /api/v1/diagnoses/{id}` 仍可查询。`data/` 目录已被 git 忽略，不会发布。

控制台的 **Recent Diagnoses（最近诊断）** 面板会列出已存报告（按时间倒序），点击某行可重新打开其完整报告。同样的数据也可通过 `GET /api/v1/diagnoses?projectId=&environment=&limit=` 获取。

若本地调试需要真实地址或凭据，请写在 `config/application-local.yml`（已被 git 忽略，且位于 `src/main/resources` 之外，不会打进 jar），启动时加 `--spring.profiles.active=local`。切勿把真实凭据写进 `application.yml`。

前台运行时，使用 `Ctrl+C` 停止服务。

## AI 提供方

`FAULTPILOT_AI_PROVIDER` 决定报告如何生成。配置从环境变量读取（直接 `export` 即可，见下方示例）；每个变量都有默认值，未设置即用默认。任何提供方失败（网络、超时、非法输出）都会回退为规则降级报告，请求不会失败。（命令行 flag 如 `--faultpilot.ai.provider=...` 仍可覆盖环境变量。）

| 变量 | 默认值 | 用于 |
| --- | --- | --- |
| `FAULTPILOT_AI_PROVIDER` | `mock` | 选择提供方（`mock` / `openai-api` / `codex-cli`） |
| `OPENAI_API_KEY` | _(空)_ | `openai-api`（必填） |
| `OPENAI_MODEL` | _(空)_ | `openai-api`（必填；需支持 json_schema） |
| `OPENAI_BASE_URL` | `https://api.openai.com` | `openai-api`（可选；兼容网关） |
| `OPENAI_TIMEOUT` | `35s` | `openai-api`（可选） |
| `FAULTPILOT_CODEX_COMMAND` / `FAULTPILOT_CODEX_MODEL` / `FAULTPILOT_CODEX_TIMEOUT` | `codex` / _(空)_ / `120s` | `codex-cli`（可选） |
| `FAULTPILOT_DB_URL` / `FAULTPILOT_DB_USER` / `FAULTPILOT_DB_PASSWORD` | _(空)_ | 某项目的 `logs(type: jdbc)` 或 `database` 块 |
| `FAULTPILOT_INGEST_TOKEN` | _(空)_ | `faultpilot.ingestion.tokens` |

- **`mock`**（默认）：确定性、无网络，用于演示和测试。
- **`openai-api`**：通过 OpenAI API 真实诊断，环境变量配置：
  ```bash
  export FAULTPILOT_AI_PROVIDER=openai-api
  export OPENAI_API_KEY=sk-...
  export OPENAI_MODEL=gpt-4o-mini        # 需支持 json_schema 响应格式的模型
  mvn -pl faultpilot-server spring-boot:run
  ```
  `OPENAI_BASE_URL` 可指向兼容网关。API Key 仅放入 Authorization 头，不写日志。
- **`codex-cli`**（实验性、仅本地）：复用本机已登录的 Codex CLI。
  ```bash
  codex login            # 由你自己执行一次
  export FAULTPILOT_AI_PROVIDER=codex-cli
  mvn -pl faultpilot-server spring-boot:run
  ```
  助手只在只读沙箱中调用 `codex exec`；不执行 `codex login`，也不读取/复制/打印 Codex 凭据文件。
  **线上部署不要启用 `codex-cli`**，线上请用带 Key 的 `openai-api`。

模型只会拿到已脱敏的证据上下文，根因证据强度由规则计算，不由模型决定。

## 接入一个 Java 项目

FaultPilot 不嵌入 SDK 即可分析应用。默认的
[`application.yml`](../../faultpilot-server/src/main/resources/application.yml) 只带 mock 演示项目；要接真实应用，
在 `faultpilot.projects` 下新增一个项目，按需组合多种证据源——每次诊断会一起采集。日志源通过 `logs.type`
**三选一**（`local-file` | `jdbc` | `mock`），并可在同一个项目上额外加一个 `database` 块做只读数据库分析：
日志与数据库是**可叠加**的，不是二选一。

| 接入方式 | 被监控应用需准备 | 必填字段 | 说明 |
| --- | --- | --- | --- |
| **本地日志文件**（[示例](../../examples/local-file/README.md)） | FaultPilot 所在主机上一个可读的日志文件 | `logs.type: local-file`、`logs.paths` | 默认解析器识别 Spring Boot 控制台格式；仅当布局不同才需配 `logs.pattern`。只分析 `WARN`/`ERROR` 行。 |
| **JDBC 日志表**（[示例](../../examples/jdbc-log-table/README.md)） | 一个**只读**数据库视图，列为 `occurred_at, level, trace_id, message, stack_trace` | `logs.type: jdbc`、`logs.url`、`logs.username`、`logs.password`、`logs.view` | MySQL/PostgreSQL 驱动已随 server 内置；其他数据库需自行将 JDBC 驱动加入 classpath。 |
| **只读数据库**（[MySQL](../../examples/mysql-local/README.md) / [PostgreSQL](../../examples/postgres-local/README.md)） | 一个专用的**只读** MySQL/PostgreSQL 账号 | `database.type`（`mysql`\|`postgres`）、`database.url`、`database.username`、`database.password` | 通过内置固定 SQL 采集连接数 / 长事务 / 锁等待快照与慢 SQL 摘要。需慢 SQL 摘要时，确保慢查询统计已启用。 |

示例：一个项目同时读取本地日志文件**和**只读 MySQL 数据库。凭据用环境变量（`FAULTPILOT_DB_*`）；每个字段都对应 `FaultPilotProperties`。

```yaml
faultpilot:
  projects:
    - id: my-app
      display-name: My App
      integration-level: L2
      environments: [local]
      max-query-hours: 168
      max-results: 500
      logs:                                    # 日志源三选一:local-file | jdbc | mock
        type: local-file
        zone: Asia/Shanghai                    # 无时区偏移的时间戳按此解释
        paths:
          - /absolute/path/to/your-app/error.log
        # 仅当日志格式与 Spring Boot 控制台格式不同时才设 `pattern`
      database:                                # 可选 —— 只读 MySQL/PostgreSQL
        type: mysql                            # mysql | postgres
        url: ${FAULTPILOT_DB_URL}              # 例:jdbc:mysql://localhost:3306/app
        username: ${FAULTPILOT_DB_USER}        # 只读账号
        password: ${FAULTPILOT_DB_PASSWORD}
        long-tx-threshold: 30s
```

JDBC 日志表与 PostgreSQL 的写法见 `examples/` 下的可运行示例。所有接入方式均使用只读访问、固定参数化 SQL、时间范围与最大条数限制以及证据脱敏。生产数据发送给模型提供方前，请复查脱敏后的 AI 请求内容。

**下一版本（不在 v0.1.0 范围）：** 用于推送式接入的 Spring Boot Starter、接入向导页面，以及标准化的指标 / Trace / 发布事件接入能力。

完整范围以 [统一规格 SSOT](specification.md) 为准。
