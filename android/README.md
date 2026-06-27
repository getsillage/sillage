# Sillage Android

Sillage Android 是连接自托管 Sillage 实例的手机客户端。它适合在手机上记录、搜索、回看历史，并使用已配置好的 AI 总结和问答功能。

## 使用前准备

先确保你的 Sillage 服务已经启动，并且手机可以访问到服务地址。

如果后端运行在本机 Docker：

- Android 模拟器填写 `http://10.0.2.2:5231`
- 真机填写电脑在局域网内的地址，例如 `http://192.168.1.10:5231`

如果通过公网域名、反向代理或 Cloudflare Tunnel 访问，填写对应的 HTTPS 地址。

## 当前功能

- 配置服务器地址。
- 初始化唯一账号或登录已有账号。
- 查看、搜索、新建、编辑、删除记录。
- 置顶、取消置顶、归档、取消归档记录。
- 上传附件并插入记录内容。
- 切换浅色或深色主题。
- 管理 AI 档案并测试连接。
- 查看和生成记录总结。
- 使用基础 Ask 问答，查看来源记录，并把回答保存为记录。
- 退出登录。

当前版本需要网络连接到你的 Sillage 服务，不提供离线编辑队列、后台同步或推送通知。

## 安装调试版

如果你从源码构建，需要准备 JDK 17 和 Android SDK。

```bash
cd android
./gradlew :app:assembleDebug
```

APK 输出位置：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备或模拟器：

```bash
cd android
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 构建发布版

Android 应用包名固定为：

```text
app.sillage
```

发布版 APK 使用本地 release keystore 签名。签名文件不提交到仓库，需要在 `android/`
目录下准备：

```text
release.keystore
signing.properties
```

`signing.properties` 格式：

```properties
storeFile=release.keystore
storePassword=...
keyAlias=sillage-release
keyPassword=...
```

构建 release APK：

```bash
cd android
./gradlew :app:assembleRelease
```

APK 输出位置：

```text
android/app/build/outputs/apk/release/app-release.apk
```

发布前建议校验签名和 zipalign：

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -v 4 app/build/outputs/apk/release/app-release.apk
```

GitHub Release 附件建议命名为：

```text
Sillage-vX.Y.Z.apk
```

## 安全建议

本地调试允许 HTTP 明文连接。生产使用建议通过 HTTPS 反向代理或 Cloudflare Tunnel 访问 Sillage。

Android 当前会保存 access token 和 refresh cookie。退出登录会请求服务端注销会话，并清理本地会话。
