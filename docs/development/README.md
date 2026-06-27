# 开发资料

这里保存面向维护者的项目资料。普通使用和部署请先看根目录 [README](../../README.md)。

## 目录

- [产品指导](product-guidance.md)
- [同步 API](api/sync.md)
- [Web 设计方向](design/README.md)
- [历史迁移计划](../archive/migration/memos-style-self-hosted-plan.md)

## 常用命令

```bash
go test ./...
go vet ./...
go build ./cmd/sillage

pnpm --dir web install
pnpm --dir web typecheck
pnpm --dir web lint
pnpm --dir web test
pnpm --dir web build

buf lint
buf generate
```

Android：

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```
