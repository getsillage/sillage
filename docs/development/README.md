# 开发文档

开发环境、修改流程、生成物和验证命令统一见根目录[贡献指南](../../CONTRIBUTING.md)。本目录只保存需要长期维护的工程决策：

- [架构说明](architecture.md)：模块职责、请求链路、数据边界和事实来源。
- [产品指导](product-guidance.md)：产品范围、术语和 AI 行为边界。
- [安全开发边界](security.md)：认证、附件、密钥、外部请求和敏感数据约束。
- [同步 API](api/sync.md)：离线同步、幂等和冲突语义。
- [Web 设计规范](design/README.md)：界面方向、组件约束和验收底线。

部署、数据维护和 AI 外部数据属于使用者文档，分别见[部署说明](../user/deployment.md)、[数据与备份](../user/data.md)和[AI 使用与隐私](../user/ai.md)。

重大、跨模块且难以逆转的技术选择，在实现该决定的提交中新增 `docs/development/decisions/YYYY-MM-DD-<topic>.md`，只写“背景、决定、后果”并链接被替代的决定。任务计划、普通实现选择和待办事项不写 ADR；待办使用 Issue，已完成过程使用 Git 历史。首个真实决定出现前不创建空目录或模板。
