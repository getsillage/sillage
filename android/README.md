# Sillage Android

`android/` 是 Sillage 的原生 Android 初版工程，和 Go 后端、Web 前端放在同一仓库。当前版本是在线优先客户端，先覆盖核心记录流程，不实现离线队列、Room 本地库或完整 sync。

## 功能范围

- 配置 Sillage 后端地址。
- 初始化唯一账号。
- 登录已有账号。
- 查看活动记录列表，按置顶、记录日期和创建时间排序。
- 搜索记录。
- 新建、编辑、删除记录。
- 置顶、取消置顶、归档、取消归档记录。
- 退出登录并请求服务端注销 refresh session。

当前不包含附件、AI 设置、记录总结、Ask、离线缓存、后台同步、冲突恢复和推送通知。

## 技术栈

- Kotlin。
- Gradle Kotlin DSL。
- Android Gradle Plugin。
- Jetpack Compose + Material 3。
- ViewModel + Coroutines + StateFlow。
- OkHttp 调用现有 REST v1。
- SharedPreferences 保存服务器地址、access token 和 refresh cookie。

初版暂不接入 Connect-Kotlin 或从 `../proto` 生成 Android 客户端。后续离线同步阶段再评估从根目录 `proto/` 生成客户端代码，不复制 proto 文件到 `android/`。

## 环境要求

- JDK 17。
- Android SDK Platform 35。
- Android SDK Build Tools 35.0.0。
- Android Platform Tools。

本机 SDK 路径写入 `android/local.properties`，该文件不会提交入库。

## 构建

```bash
cd android
./gradlew :app:assembleDebug
```

APK 输出：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备或模拟器：

```bash
cd android
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

静态检查：

```bash
cd android
./gradlew :app:lintDebug
```

## 连接本地后端

先启动 Sillage 后端：

```bash
SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage
```

Android 模拟器访问宿主机本地服务时，应用默认服务器地址为：

```text
http://10.0.2.2:5231
```

真机需要填写局域网或公网可访问地址，例如：

```text
http://192.168.1.10:5231
```

当前 Android Manifest 允许 HTTP 明文流量，方便本地自托管调试。生产部署建议使用 HTTPS 反向代理或 Cloudflare Tunnel。

## 认证说明

登录和初始化接口会返回 `accessToken`，同时通过 cookie 设置 refresh token。Android 初版保存 access token 和 refresh cookie：

- 普通 API 请求使用 `Authorization: Bearer <accessToken>`。
- access token 过期后，会调用 `/api/v1/auth/refresh` 并重试原请求。
- 退出登录会调用 `/api/v1/auth/signout`，随后清理本地会话。

当前会话存储以初版可用为目标。后续如支持生产移动端长期使用，应迁移到 Android Keystore 或加密存储方案。

## 后续方向

- 从根目录 `proto/` 生成 Android 客户端，或继续保留 REST v1 包装层。
- 引入 Room 保存 memo 镜像、sync cursor、tombstone 和附件 metadata。
- 使用 WorkManager 处理后台同步、附件上传重试和网络恢复后的任务恢复。
- 实现 `/api/v1/sync` 与 `/api/v1/sync:push` 的离线写入、幂等 mutation id、冲突返回和恢复流程。
- 增加附件、AI 总结、Ask 和设置界面。
