# 发布流程

## Android

Android 手机安装包使用 `.apk`，不是 `.exe`。当前 Preview 渠道发布可安装的 Debug 签名 APK，最低支持 Android 7.0。

1. 保证 `main` 的测试、lint 和 Debug 构建通过。
2. 更新 `versionCode`、`versionName` 和 `CHANGELOG.md`。
3. 创建并推送形如 `v0.2.0-preview` 的 Git 标签。
4. `.github/workflows/release.yml` 自动构建 APK、生成 SHA-256 并发布 GitHub Release。
5. 在手机浏览器打开 Releases 页面，下载 APK 后安装。

Preview 签名只用于测试。稳定版发布前需要生成专用 release keystore，把密钥和口令保存到 GitHub Actions Secrets，禁止提交到仓库。

## Windows

Windows 使用 `.exe` 或 `.msi`。JavaTavern 当前是原生 Android 工程，不能直接把 APK 改名为 EXE。桌面版应共享数据格式和协议层，但需要单独实现桌面 UI、文件系统和安全存储适配。
