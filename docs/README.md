# 文档中心

按任务选择入口：

| 任务 | 文档 |
| --- | --- |
| 运行和配置实例 | [部署说明](user/deployment.md) |
| 备份、恢复和迁移 | [数据与备份](user/data.md) |
| 配置 AI 并了解外部数据 | [AI 使用与隐私](user/ai.md) |
| 建立开发环境和验证改动 | [贡献指南](../CONTRIBUTING.md) |
| 理解模块、数据和契约边界 | [架构说明](development/architecture.md) |
| 修改产品语义 | [产品指导](development/product-guidance.md) |
| 修改认证、附件、密钥或外部请求 | [安全开发边界](development/security.md) |
| 修改同步行为 | [同步 API](development/api/sync.md) |
| 修改 REST 路由或客户端 | [REST API 说明](development/api/README.md) |
| 修改 Web 界面 | [Web 设计规范](development/design/README.md) |
| 构建 Android | [Android 说明](../android/README.md) |
| 下载发布版本与查看变更 | [GitHub Releases](https://github.com/getsillage/sillage/releases) |
| 报告安全问题 | [安全策略](../SECURITY.md) |

## 维护原则

- 每类信息只保留一个主文档，其他位置只做摘要和链接。
- 文档描述稳定边界与操作，不镜像容易变化的实现细节；冲突时以链接的代码事实源为准。
- 功能、配置、命令、契约或架构变化应在同一提交更新对应文档。
- 已完成的实施计划不长期归档在工作树中；需要追溯时使用 Git 历史。
