# 安全开发边界

本文记录修改 Sillage 安全敏感代码时必须保持的稳定边界。漏洞报告流程见根目录[安全策略](../../SECURITY.md)，部署和数据保护分别见[部署说明](../user/deployment.md)与[数据与备份](../user/data.md)。

## 资产与信任边界

受保护资产包括记录、附件、问答历史、账号凭据、登录会话、AI API key 和运行密钥。主要边界是：

```text
Web / Android -> HTTPS proxy or trusted LAN -> Sillage -> SQLite / attachments
                                                |
                                                +-> configured AI Provider
```

Sillage 自身只提供 HTTP。公网 TLS、转发头清理和后端端口隔离由反向代理或 Tunnel 负责。宿主机、完整数据目录、外置 secret 与自定义 AI Provider 都属于部署者明确选择的信任域。

## 认证与会话

- 一个实例只能创建一个未删除账号；初始化检查与写入必须保持在同一事务中。
- 初始化接口在实例未建号前无需认证。部署文档必须要求先在回环地址初始化、确认 bootstrap 状态后，再开放代理、Tunnel 或局域网端口；不能把未初始化实例直接暴露到公网。
- 密码只保存派生后的 hash，不得写入日志、响应或同步数据。
- access token 由服务端签名并在 15 分钟后过期；refresh token 只以 hash 保存、有效期 30 天并在刷新时轮换。退出登录撤销 refresh session，但已签发的 access token 仍可使用到过期。
- Cookie 必须保持 `HttpOnly` 和 `SameSite=Lax`。TLS 或可信 `X-Forwarded-Proto: https` 下还必须设置 `Secure`。
- 受保护的业务写接口只接受 Bearer token。访问 Cookie 的回退只用于浏览器无法设置 Authorization header 的安全 GET，例如附件读取，不能扩展到业务写操作。
- 登录限流使用账号与客户端 IP。应用会读取 `X-Forwarded-For`，因此代理必须覆盖而不是追加客户端提供的转发头。

## 数据与密钥

- `SESSION_SECRET` 用于会话签名；`ENCRYPTION_SECRET` 用于派生 AI key envelope 密钥。二者的生成、文件权限和恢复语义不能静默改变。
- AI API key 只以加密 envelope 写入 SQLite；REST、Connect、同步、日志和导出不得返回明文。
- `runtime/secrets.json` 不是缓存。密钥轮换或存储格式变化必须提供兼容或明确的迁移、备份和回滚说明。
- 数据库、附件与备份没有整体静态加密。不要把字段级 API key 加密描述成完整数据加密。
- AI 档案删除必须清空当前数据库行中的 API key envelope；历史备份、记录 tombstone 与 AI 派生数据的保留语义必须在用户数据文档中明确。

## 附件与内容

- 上传、读取和删除都必须按账号鉴权；只凭 UID、文件名或磁盘路径不能授权。
- 上传同时限制 HTTP body、multipart 文件声明和实际复制字节数。当前非空 MIME 来自客户端，必须视为不可信元数据；不能据此放宽鉴权或文件系统边界。
- 文件名必须去除路径和不安全字符；数据库中的 `storage_ref` 也必须在读取和删除前验证不能逃出数据目录。
- 当前允许 `image/*`、`text/plain` 和 PDF 内联，其余类型强制下载。改变该范围时要单独评估 SVG 等主动内容，并继续保留 `X-Content-Type-Options: nosniff`。
- Web Markdown 不执行原始 HTML，并过滤危险 URL scheme。修改渲染器、链接或附件预览时必须补 XSS 与跨账号访问测试。

## AI 与外部请求

- AI 档案及自定义 Base URL 只能由已认证账号管理。自定义地址可以访问服务运行环境可达的网络目标，应视为受信任配置，不能变成未认证或第三方可控输入。
- 总结会发送记录正文；问答会发送问题、当前分支历史和来源摘录。改变这些范围时必须同步更新[AI 使用与隐私](../user/ai.md)。
- API key 只能放在 Provider 要求的认证 header 中。日志、错误响应和测试失败信息不得包含 key、Authorization header 或请求正文。
- 普通生成接口应返回稳定的用户错误，不能透传 Provider 响应正文；连接测试可以提供诊断信息，但仍须过滤凭据和请求内容。

## 日志与探针

- 请求日志只记录请求 ID、方法、路径、状态、耗时和客户端 IP，不记录 header、body、Cookie、token 或记录内容。
- `/healthz` 和 `/readyz` 无需认证；`readyz` 当前还返回依赖错误文本。不得让错误链新增密钥、账号、记录内容或其他敏感配置，公开部署前应评估是否需要收敛诊断信息。
- 新增错误日志前先确认错误链不会携带密钥、私密正文或 Provider 请求 payload。
- Web 草稿会保存在浏览器 `localStorage`，不进入服务端备份且可能跨退出登录保留。修改草稿或退出登录流程时必须保持这一边界可见，并避免在共享设备上误导用户。

## Android

- 生产实例只使用 HTTPS；允许 HTTP 是模拟器和受信任局域网的兼容边界，不能作为公网部署建议。
- 登录信息、离线数据和本地 AI key 通过 Android Keystore 加密；旧版明文 SharedPreferences 仍会在读取时兼容，并在后续保存时迁移。导出的 JSON 必须移除 API key，并明确提示其余内容仍为明文敏感数据。
- 受保护附件先带认证下载到应用缓存，再通过只读 FileProvider URI 交给外部查看器，不能暴露应用私有文件路径。

## 修改与验证

安全相关改动至少覆盖对应测试：

- 认证、Cookie 或代理头：`server/auth_*_test.go`、`server/connect_routes_test.go`；
- 附件：跨账号访问、路径逃逸、大小限制、下载响应与清理；
- 密钥或 AI 档案：加解密、密钥不可用、响应不泄露明文；
- AI 数据范围或 Prompt：服务端和 Android 同步修改，并核对用户隐私说明；
- Android 存储或导出：Keystore 兼容、旧数据迁移和导出脱敏。

完整命令见[贡献指南](../../CONTRIBUTING.md)。实现事实源是 `server/auth/`、`server/auth_routes.go`、`server/attachment_routes.go`、`server/ai_provider*.go`、`internal/secret/`、`store/`、`web/src/components/Markdown.tsx` 和 Android `data/` 层。
