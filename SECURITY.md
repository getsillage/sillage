# 安全策略

Sillage 保存私密记录、附件、登录会话和加密后的 AI API key。请不要在公开 Issue、讨论区、日志或截图中披露漏洞细节、真实数据或密钥。

## 报告漏洞

本仓库当前尚未启用 GitHub 私密漏洞报告，也没有公开固定的安全邮箱。请先创建一个不含漏洞细节的 Issue，请求维护者建立私密联系；在私密渠道确认前不要发送复现和敏感信息。

完整报告应包含：

- 受影响版本或提交；
- 影响与攻击前提；
- 最小复现步骤；
- 已知缓解方式。

请使用虚构数据复现。维护者应在公开发布前启用 GitHub Private Vulnerability Reporting 或提供专用安全邮箱，并同步更新本文件。

## 支持范围

安全修复以最新发布版本和 `main` 为目标；旧版本不保证单独维护。自托管实例应在完整备份后及时升级。

## 部署责任

- Sillage 只提供 HTTP 服务，公网访问必须由可信反向代理或 Tunnel 提供 HTTPS。
- 数据目录和备份没有额外的整体静态加密，应限制宿主机权限并保护传输过程。
- 不要把 `SESSION_SECRET`、`ENCRYPTION_SECRET`、AI API key 或数据库提交到仓库。
- 暴露端口前先阅读[部署说明](docs/user/deployment.md)和[数据与备份](docs/user/data.md)。

修改认证、附件、密钥或外部请求时，还应遵守[安全开发边界](docs/development/security.md)。
