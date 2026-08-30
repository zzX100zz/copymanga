# copymanga
拷贝漫画的第三方APP，优化阅读/下载体验

## Android 5 / API 21 构建

此分支基于最早的稳定 1.x 标签 `release`（v1.1），最低系统版本已降至 Android 5.0（API 21）。
原版使用的 AndResGuard 1.2.19 已无法从原仓库解析，因此本分支改用标准 Android Gradle 构建，不再执行资源混淆。

构建环境：

- JDK 8 或 JDK 11
- Android SDK 30
- Android Build Tools 30.0.2

调试包：

```shell
./gradlew assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

发布包默认保持未签名，不再引用原作者机器上的密钥。需要签名时，通过 Gradle 属性或同名环境变量提供以下四项：

```text
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```
