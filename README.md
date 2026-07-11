<p align="center">
  <img src="web/public/sillage-icon.svg" alt="Sillage" width="96" height="96" />
</p>

<h1 align="center">Sillage</h1>

Sillage 是一个自托管的单人记录空间，用来保存日常片段、回看历史，并基于自己的记录进行 AI 总结与问答。

首次打开实例时创建唯一账号。之后，记录、附件、总结和问答都需要登录访问。

## 主要能力

- 使用 Markdown 写记录，上传图片或文件，并恢复未提交草稿。
- 按列表、日历和搜索回看记录，使用收藏与归档整理内容。
- 配置 Anthropic、OpenAI 或 OpenAI 兼容服务，生成记录总结和有来源的回答。
- 使用响应式 Web 界面，或通过原生 Android 客户端在线、离线记录和手动同步。

Sillage 不提供多人协作、公开主页、社交分享、后台自动同步或官方托管服务。

## 快速开始

稳定部署应从 [GitHub Releases](https://github.com/getsillage/sillage/releases) 选择发布 tag，而不是直接构建 `main`。以下示例从发布源码构建，并只在本机开放服务：

```bash
git clone https://github.com/getsillage/sillage.git
cd sillage
git checkout vX.Y.Z
VERSION="$(git describe --tags --exact-match)"
REVISION="$(git rev-parse HEAD)"
docker build \
  --build-arg VERSION="$VERSION" \
  --build-arg REVISION="$REVISION" \
  -t "sillage:$VERSION" \
  -f scripts/Dockerfile .
docker run --rm \
  -p 127.0.0.1:5231:5231 \
  -v "$HOME/.sillage:/var/opt/sillage" \
  "sillage:$VERSION"
```

打开 `http://localhost:5231`，按页面提示创建唯一账号。

健康检查：

```bash
curl http://localhost:5231/healthz
curl http://localhost:5231/readyz
```

数据保存在 `$HOME/.sillage`。升级、迁移或备份前应先停止服务，并复制整个目录，不要只复制 `sillage.db`。完整步骤见[数据与备份](docs/user/data.md)。

Compose、反向代理、环境变量和公网部署见[部署说明](docs/user/deployment.md)。

## 技术结构

- Go + Echo 单体后端
- SQLite 数据库与本地附件目录
- React + TypeScript + Vite Web 客户端，构建产物嵌入 Go 二进制
- Kotlin + Jetpack Compose Android 客户端
- Protobuf API 契约，同时提供 REST v1 与 Connect v1

详细边界和事实来源见[架构说明](docs/development/architecture.md)。

## 文档

| 需求 | 入口 |
| --- | --- |
| 部署实例 | [部署说明](docs/user/deployment.md) |
| 备份、恢复和迁移数据 | [数据与备份](docs/user/data.md) |
| 配置 AI 并了解外部数据 | [AI 使用与隐私](docs/user/ai.md) |
| 参与开发 | [贡献指南](CONTRIBUTING.md) |
| 理解系统边界 | [架构说明](docs/development/architecture.md) |
| 修改认证、附件或密钥 | [安全开发边界](docs/development/security.md) |
| 修改同步客户端 | [同步 API](docs/development/api/sync.md) |
| 修改产品或界面 | [产品指导](docs/development/product-guidance.md) / [Web 设计规范](docs/development/design/README.md) |
| 构建 Android | [Android 说明](android/README.md) |
| 下载版本与查看变更 | [GitHub Releases](https://github.com/getsillage/sillage/releases) |

完整索引见[文档中心](docs/README.md)。安全问题请按[安全策略](SECURITY.md)私下报告。

## 开发

后端需要 Go 1.25，Web 推荐 Node.js 24 与 pnpm 11.9；Android 需要 JDK 17 和 Android SDK。安装、启动、生成物与验证规则统一写在[贡献指南](CONTRIBUTING.md)中。

## 许可证

Sillage 使用 [MIT License](LICENSE)。
