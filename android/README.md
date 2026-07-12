# Sillage Android

原生 Android 客户端支持 Android 8.0 及以上版本，可以连接自托管实例，也可以在设备上离线记录。

已发布 APK 从 [GitHub Releases](https://github.com/getsillage/sillage/releases) 下载。安装前核对 Release 中的版本、SHA-256 与签名信息；服务端和 Android 都应使用同一发布版本或发布说明明确兼容的版本组合。

## 连接实例

先启动 Sillage 服务，并确保手机可以访问服务地址：

- 模拟器访问本机：`http://10.0.2.2:5231`
- 真机访问局域网主机：例如 `http://192.168.1.10:5231`
- 公网实例：使用 HTTPS 反向代理或 Tunnel 地址

在线与离线模式都支持记录、日历、搜索、收藏、归档、AI 设置、总结和问答。在线模式另外支持初始化/登录、附件上传与认证下载；本地数据可以导入导出，并可手动拉取、推送或双向同步。

记录编辑器支持 Markdown 编辑与预览。预览覆盖 CommonMark 基础语法、删除线、表格、任务列表和单换行；原始 HTML 不执行，图片语法显示为可安全打开的附件或外链。

当前不提供后台自动同步或推送，离线附件字节与元数据也不会完整同步。Android 导出文件不能替代服务端整目录备份。

“拉取”会完整读取服务端可同步数据并合并到本机；“推送”当前只上传本机记录；“双向同步”先推送再完整拉取。发生版本冲突时，应用只显示数量并保留本地 pending 修改，暂不提供应用内合并界面；不要反复重试覆盖，先保留本地导出并在服务端确认记录后再处理。

## 构建与测试

需要 JDK 17 和 Android SDK 35：

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

调试 APK 位于：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备或模拟器：

```bash
cd android
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 发布签名

应用包名为 `app.sillage`。签名文件不提交仓库；在 `android/` 下准备 `release.keystore` 和 `signing.properties`：

```properties
storeFile=release.keystore
storePassword=...
keyAlias=sillage-release
keyPassword=...
```

构建并校验 release APK：

```bash
cd android
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -v 4 app/build/outputs/apk/release/app-release.apk
```

只有本地签名配置存在时 release 构建才会使用该 keystore。不要发布未校验签名的产物，也不要提交 APK/AAB、keystore、`signing.properties` 或 `local.properties`。

## 安全边界

应用允许明文 HTTP，便于局域网和模拟器开发；生产实例应只使用 HTTPS。登录会话和离线数据通过 Android Keystore 保护，但导出的 JSON 是明文敏感数据，应限制分享和保存位置。

附件链接只接受普通 `http(s)` 外链或同源 `/file/attachments/...`。受保护附件由 App 携带认证下载到缓存，再通过只读 FileProvider URI 交给系统查看器。

完整开发门禁见[贡献指南](../CONTRIBUTING.md)，服务端部署与数据安全见[部署说明](../docs/user/deployment.md)和[数据与备份](../docs/user/data.md)。
