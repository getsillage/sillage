# 使用与部署

这份文档面向准备运行自己 Sillage 实例的使用者。

## Docker 运行

```bash
docker build -t sillage:latest -f scripts/Dockerfile .
docker run --rm -p 5231:5231 -v "$HOME/.sillage:/var/opt/sillage" sillage:latest
```

打开 `http://localhost:5231` 后，按页面提示创建唯一账号。

## Compose 运行

```bash
docker compose -f scripts/compose.yaml up -d --build sillage
docker compose -f scripts/compose.yaml logs -f sillage
```

默认本机端口是 `5231`。如需改为其他端口：

```bash
SILLAGE_HOST_PORT=8080 docker compose -f scripts/compose.yaml up -d --build sillage
```

## 本机直接运行

如果你已经有 Go 环境，也可以直接运行：

```bash
SILLAGE_DATA="$HOME/.sillage" go run ./cmd/sillage
```

或构建二进制：

```bash
go build -o sillage ./cmd/sillage
SILLAGE_DATA="$HOME/.sillage" ./sillage
```

## 外网访问

Sillage 默认只提供 HTTP。公网使用时，建议通过 HTTPS 反向代理或 Cloudflare Tunnel 暴露。

反向代理需要保留这些请求头：

```text
X-Forwarded-Proto
X-Forwarded-Host
X-Forwarded-For
```

如果外部访问地址固定，建议设置：

```bash
SILLAGE_INSTANCE_URL=https://your-domain.example
```
