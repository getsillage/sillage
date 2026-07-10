# 数据与备份

默认配置下，Sillage 的持久化单元是完整的数据目录。Docker 示例把容器内 `/var/opt/sillage` 映射到宿主机 `$HOME/.sillage`。显式配置的外置 DSN 或 secret 文件也属于同一个恢复点。

## 目录内容

```text
sillage.db
sillage.db-wal
sillage.db-shm
assets/attachments/
.thumbnail_cache/
runtime/secrets.json
```

- SQLite 保存账号、记录、AI 设置和会话。
- `assets/attachments/` 保存附件字节。
- `.thumbnail_cache/` 是可再生缓存。
- `runtime/secrets.json` 保存自动生成的会话和加密密钥，不是缓存。

记录、附件和备份没有额外的整体静态加密。丢失 `runtime/` 会使现有会话失效，并可能导致已保存的 AI API key 无法解密。

显式设置 `SESSION_SECRET` / `ENCRYPTION_SECRET` 或对应 `_FILE` 时，实际运行值不保证回写到 `runtime/secrets.json`。这些外部 secret 必须单独安全保存并随数据恢复；更换 `SESSION_SECRET` 会使会话失效，更换 `ENCRYPTION_SECRET` 会使已有 AI API key 无法解密。

## 备份

下面的脚本适用于 Compose。若使用 `docker run`、systemd 或本机二进制，必须换成对应的停止/启动命令，并确认没有进程继续写 SQLite。脚本任一步失败都会中止并保持服务停止。

```bash
sh -eu <<'SH'
DATA="$HOME/.sillage"
BACKUP="$HOME/.sillage-backups/sillage-$(date +%Y%m%d-%H%M%S)"

test -f "$DATA/sillage.db"
test -d "$DATA/assets/attachments"
test -r "$DATA/runtime/secrets.json"
docker compose -f scripts/compose.yaml stop sillage
umask 077
mkdir -p "$(dirname "$BACKUP")"
test ! -e "$BACKUP"
cp -a "$DATA" "$BACKUP"
test -f "$BACKUP/runtime/secrets.json"
test "$(sqlite3 -readonly "$BACKUP/sillage.db" "PRAGMA integrity_check;")" = "ok"
docker compose -f scripts/compose.yaml start sillage
SH
```

上述脚本需要本机安装 `sqlite3`。容器默认以 UID/GID `10001` 管理文件；宿主用户无法读取密钥时，应使用有权限的备份账号或配置匹配的 UID/GID，不要用 `chmod 777` 绕过。不要只复制 `sillage.db`：WAL/SHM、附件和运行密钥都可能位于数据库文件之外。若 `SILLAGE_DSN` 指向数据目录外，还必须在停服状态下单独备份该数据库及其 WAL/SHM 文件。

## 验证备份

恢复前至少确认关键路径存在：

```bash
test -f "$BACKUP/sillage.db"
test -d "$BACKUP/assets/attachments"
test -f "$BACKUP/runtime/secrets.json"
test "$(sqlite3 -readonly "$BACKUP/sillage.db" "PRAGMA integrity_check;")" = "ok"
```

最后一条需要本机安装 `sqlite3`。备份应保存在数据目录之外，并通过受保护的介质传输。

## 恢复

恢复流程保留当前数据作为回滚副本，不直接删除：

```bash
sh -eu <<'SH'
DATA="$HOME/.sillage"
BACKUP="$HOME/.sillage-backups/sillage-YYYYMMDD-HHMMSS"
ROLLBACK="$HOME/.sillage.before-restore-$(date +%Y%m%d-%H%M%S)"

test -d "$DATA"
test -f "$BACKUP/sillage.db"
test -d "$BACKUP/assets/attachments"
test -f "$BACKUP/runtime/secrets.json"
test "$(sqlite3 -readonly "$BACKUP/sillage.db" "PRAGMA integrity_check;")" = "ok"
test ! -e "$ROLLBACK"
docker compose -f scripts/compose.yaml stop sillage
mv "$DATA" "$ROLLBACK"
cp -a "$BACKUP" "$DATA"
docker compose -f scripts/compose.yaml start sillage
curl --fail http://localhost:5231/readyz
SH
```

只有在登录、记录和附件都确认正常后，才处理 `ROLLBACK`。如果恢复失败，先停止服务，将失败目录移走，再把 `ROLLBACK` 移回原路径。上述流程假定使用默认 DSN 和自动生成的运行密钥；外置数据库和外部 secret 必须恢复为与备份时一致的值。

## 迁移实例

迁移到另一目录或主机时：

1. 停止源实例与目标实例。
2. 复制完整数据目录，并保留文件权限。
3. 确认数据库、附件和 `runtime/` 都存在。
4. 让目标实例使用新目录，检查 `/readyz` 后再开放流量。
5. 不要让两个实例同时写同一份 SQLite 数据。

`.thumbnail_cache/` 当前只是预留目录，启动时会重新创建空目录；数据库、附件和 `runtime/` 不能独立重置。

Android 导出的 JSON 和手动同步不包含服务端附件字节、账号、会话或运行密钥，不能替代服务端整目录备份。
