# 专题 06：Keystore 与隐私

## 当前安全措施

- API Key 使用 Android Keystore AES/GCM 密钥加密。
- Base URL 仅允许 HTTPS。
- Manifest 禁止 cleartext traffic。
- 关闭系统自动备份。
- 设置页使用 `FLAG_SECURE`。
- 日志不输出 API Key。

## AES/GCM 数据

存储内容由两部分组成：随机 IV 和密文。GCM 同时提供机密性与完整性。每次加密必须使用新的 IV。

## Keystore 边界

Keystore 使密钥更难被导出，但应用需要发送请求时仍会得到明文 API Key。被注入或调试的进程仍可能泄露明文。

## 实践

1. 保存一个测试 Key。
2. 检查 SharedPreferences 中不存在明文。
3. 确认设置页截图受限。
4. 清除应用数据后确认旧密文不能继续使用。

## 追问

为什么不能把 API Key 硬编码在 APK 或 `BuildConfig`？
