# REST API 开发说明

本文定义 Echo REST v1 的稳定使用边界。实现事实源是 `server/*_routes.go` 与 REST 行为测试；变更步骤见[贡献指南](../../../CONTRIBUTING.md)。

## 契约定位

- `proto/api/v1/` 是 Connect 契约源，`buf` 生成 Connect、Gateway 与 `proto/gen/openapi/openapi.yaml`。
- 生成的 OpenAPI 仅反映 Proto HTTP 注解，可能不包含 Echo 手写路由、认证模型和 REST DTO；不能直接用于 REST SDK codegen。
- REST v1 的字段名、状态码与错误响应以路由实现和本文件为准。需要机器可读的 REST OpenAPI 时，先补齐完整规范与契约测试，再将其作为公开输入。

## 认证与错误

除 `GET /healthz`、`GET /readyz`、认证 bootstrap/initialize/signin/refresh/signout 外，业务 REST 接口使用：

```http
Authorization: Bearer <access_token>
```

浏览器原生安全读取（当前为 `/file/attachments/...`）可回退到 HttpOnly access cookie；Cookie 不能用于业务写操作。未认证、校验、冲突和限流等 Echo REST 错误统一为：

```json
{
  "error": {
    "code": "stable_machine_code",
    "message": "面向用户的中文说明"
  }
}
```

HTTP 状态与 `error.code` 必须同时在受影响路由测试中覆盖。Connect 错误使用 Connect code，不要求复用此 JSON 结构。

## REST 范围

| 范围 | 路由事实源 | 备注 |
| --- | --- | --- |
| 认证 | `server/auth_routes.go` | 初始化、登录、刷新、退出和当前账号 |
| 记录与同步 | `server/memo_routes.go`、`server/sync_routes.go` | `memoDTO` 使用 `createdAt`、`updatedAt`、数值 `version` |
| 附件 | `server/attachment_routes.go` | multipart 上传、metadata、删除与认证下载 |
| AI 设置 | `server/ai_routes.go` | 配置、模型列表、连接测试与自动总结 |
| 问答 | `server/ask_routes.go` | 会话、消息、分支、head 与 SSE 流式回答 |

SSE 路由返回 `text/event-stream`；上传、附件下载、SSE 和操作型 `POST` 都是 REST 手写扩展，必须在修改时同步本表与测试。

## 版本与兼容

`/api/v1` 只允许向后兼容的新增字段、可选参数和新端点。删除、重命名、改变字段类型/含义、改变认证或错误模型都需要新的版本路径，并在发布说明中写明迁移与回滚要求。

Proto 变化必须执行 `buf lint`、`buf breaking` 与 `buf generate`。REST 与 Connect 共享语义时，必须同时覆盖两种传输；仅手写 REST 的扩展也要维护等价的 REST 回归测试。
