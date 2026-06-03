# FaultPilot Java 故障排查与监控助手 GitHub 仓库设计

版本：V1.6
日期：2026-06-01

> 对应统一规格：`docs/specification.md` V1.6。

> 本文描述目标仓库设计。当前 `v0.1.0` 开发版本已经实现的能力以[构建、启动与验证](getting-started.md)为准。

## 1. 仓库定位

仓库名称：

```text
faultpilot
```

英文描述：

> A lightweight AI troubleshooting and monitoring assistant for Java applications. Run locally or deploy independently to analyze logs, database health, slow SQL, and incidents.

中文描述：

> 面向 Java 应用的轻量级 AI 故障排查与监控助手。支持本地运行和独立部署，可分析日志、数据库状态、慢 SQL 和线上故障。

## 2. 顶层目录

```text
faultpilot/
├── README.md
├── README.zh-CN.md
├── LICENSE
├── CONTRIBUTING.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── .env.example
├── .gitignore
├── .github/
│   ├── workflows/
│   │   ├── ci.yml
│   │   └── secret-scan.yml
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.yml
│       └── feature_request.yml
├── pom.xml
├── docker-compose.yml
├── docs/
│   ├── en/
│   └── zh-CN/
├── faultpilot-server/
├── faultpilot-spring-boot-starter/
├── examples/
└── scripts/
```

## 3. README 语言切换

英文 README 顶部：

```markdown
[English](README.md) | [简体中文](README.zh-CN.md)
```

中文 README 顶部：

```markdown
[English](README.md) | [简体中文](README.zh-CN.md)
```

GitHub 默认展示 `README.md` 英文版本。中文用户点击链接切换。

## 4. 详细文档

```text
docs/
├── en/
│   ├── getting-started.md
│   ├── local-development.md
│   ├── deployment.md
│   ├── integration-guide.md
│   ├── ai-provider.md
│   └── architecture.md
└── zh-CN/
    ├── getting-started.md
    ├── local-development.md
    ├── deployment.md
    ├── integration-guide.md
    ├── ai-provider.md
    └── architecture.md
```

每篇文档顶部提供对应语言链接，例如：

```markdown
[English](../en/getting-started.md) | [简体中文](../zh-CN/getting-started.md)
```

## 5. 运行模式

### 5.1 本地模式

- 读取本地运行 Java 项目的日志文件。
- 使用 JDBC 只读账号分析本地 MySQL 或 PostgreSQL。
- 正式支持 Mock 和 OpenAI API。Codex CLI 仅保留实验性本地设计，默认禁用。

### 5.2 独立部署模式

- 使用 Docker Compose 或容器平台部署。
- 读取只读挂载日志、数据库统计视图和后续日志平台。
- 仅支持 OpenAI API Key，不使用个人 Codex CLI 登录。

## 6. AI 配置

### 6.1 OpenAI API

```env
OPENAI_API_KEY=
OPENAI_MODEL=
OPENAI_BASE_URL=https://api.openai.com
```

### 6.2 Codex CLI 本地登录

```bash
codex login
codex login status
```

助手只调用本机 `codex exec`，不读取认证文件。

该模式在 v0.1.0 中作为实验性本地 provider 提供。官方没有明确说明个人 Codex 登录态可作为第三方通用排障后端自动化调用，使用者需自行确认符合适用条款。

## 7. GitHub 发布检查

- 仓库中不存在真实密钥、内部域名、IP 和业务日志。
- `.env.example` 只包含空值和占位符。
- README 中包含中英文切换链接。
- README 中同时提供本地运行和 Docker Compose 部署步骤。
- LICENSE 使用 Apache-2.0。
- `SECURITY.md` 说明如何提交漏洞。
- 示例数据均为虚构数据。
- CI 至少执行 `mvn test` 和敏感信息扫描。
- `.github/workflows/ci.yml` 执行构建和测试。
- `.github/workflows/secret-scan.yml` 执行敏感信息扫描。

## 8. OpenAI 官方依据

- Codex 可以使用 ChatGPT 账号登录：[Using Codex with your ChatGPT plan](https://help.openai.com/en/articles/11369540)
- OpenAI API Key 创建和管理：[Where do I find my OpenAI API Key?](https://help.openai.com/en/articles/4936850-where-do-i-find-my-openai-api-key)
- API Key 安全建议：[How can I keep my OpenAI accounts secure?](https://help.openai.com/en/articles/8304786)

本项目据此只将账号登录保留为默认禁用的实验性本地设计。线上独立部署使用 API Key。
