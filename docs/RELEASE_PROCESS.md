# 发布流程

## Android

Android 手机安装包使用 `.apk`，不是 `.exe`。最低支持 Android 7.0。

1. 保证 `main` 的单元测试、lint、Debug 与 Release 构建通过。
2. 更新 `app/build.gradle.kts` 的 `versionCode` / `versionName` 和 `CHANGELOG.md`。
3. 创建并推送形如 `v0.5.0`（正式）或 `v0.5.0-preview`（预览）的 Git 标签。
4. `.github/workflows/release.yml` 自动执行 `assembleRelease`、生成 SHA-256 并发布 GitHub Release。
5. 在手机浏览器打开 Releases 页面，下载 APK 后安装。

标签名里包含 `preview` 会发布为 Pre-release，否则为正式版。

###  Release 签名

Release 构建优先使用正式签名，密钥通过环境变量注入：

| 环境变量 | 说明 |
| --- | --- |
| `JAVATAVERN_KEYSTORE_PATH` | keystore 文件路径 |
| `JAVATAVERN_KEYSTORE_PASSWORD` | keystore 口令 |
| `JAVATAVERN_KEY_ALIAS` | 密钥别名 |
| `JAVATAVERN_KEY_PASSWORD` | 密钥口令 |

**未配置以上变量时**（本地开发，或仓库尚未设置 Secrets），Release 构建会自动回退到
debug 签名。这样 `assembleRelease` 永远不因缺密钥而失败，但产出的 APK 仅适合自测分发，
不能提交到 Google Play 等正式渠道。

生成 keystore：

```bash
keytool -genkeypair -v \
  -keystore javatavern-release.jks \
  -alias javatavern \
  -keyalg RSA -keysize 2048 -validity 10000
```

把 keystore 转成 base64，配置到 GitHub 仓库的 Actions Secrets：

```bash
base64 -w 0 javatavern-release.jks   # → JAVATAVERN_KEYSTORE_BASE64
```

需要的 Secrets：`JAVATAVERN_KEYSTORE_BASE64`、`JAVATAVERN_KEYSTORE_PASSWORD`、
`JAVATAVERN_KEY_ALIAS`、`JAVATAVERN_KEY_PASSWORD`。

**keystore 与口令禁止提交到仓库**，并单独备份 —— 一旦丢失，已发布的 APK 无法再用同一签名更新。

### 混淆

`app/proguard-rules.pro` 保留了 XML inflate 所需的 View 构造函数、反射实例化的
`ViewModel`、枚举的 `values()/valueOf()`，以及注解与行号信息。当前 Release 包经 R8
处理后约 3.8 MB（Debug 包约 8.6 MB）。

## Windows

Windows 使用 `.exe` 或 `.msi`。JavaTavern 当前是原生 Android 工程，不能直接把 APK 改名为 EXE。桌面版应共享数据格式和协议层，但需要单独实现桌面 UI、文件系统和安全存储适配。
