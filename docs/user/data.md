# 数据与备份

Sillage 的持久数据默认放在 `/var/opt/sillage`。使用 README 里的 Docker 命令时，本机会映射到 `$HOME/.sillage`。

## 数据内容

```text
sillage.db
assets/attachments/
.thumbnail_cache/
runtime/
```

- `sillage.db`：SQLite 数据库。
- `assets/attachments/`：上传的附件。
- `.thumbnail_cache/`：缩略图缓存。
- `runtime/`：自动生成的运行密钥。

`runtime/` 很重要。它包含登录会话和 AI API key 加密所需的密钥。丢失后，已有加密 API key 可能无法解开。

## 备份

建议停止容器后备份整个数据目录：

```bash
docker compose -f scripts/compose.yaml stop sillage
cp -a "$HOME/.sillage" "$HOME/.sillage.backup"
docker compose -f scripts/compose.yaml start sillage
```

不要只备份 `sillage.db`。数据库旁边可能存在 WAL/SHM 文件，附件和运行密钥也不在数据库文件里。

## 恢复

停止服务后，把备份目录恢复到原数据目录，再启动 Sillage：

```bash
docker compose -f scripts/compose.yaml stop sillage
rm -rf "$HOME/.sillage"
cp -a "$HOME/.sillage.backup" "$HOME/.sillage"
docker compose -f scripts/compose.yaml start sillage
```

恢复前请确认目标目录没有仍需保留的新数据。
