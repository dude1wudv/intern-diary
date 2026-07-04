# Android 构建与打包

## 环境要求

- JDK 17+
- Android SDK
- 推荐 Android Studio

## Debug APK

```bash
cd intern-diary-android
./gradlew assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Debug APK 适合测试和 GitHub Release 演示，不适合应用商店分发。

## Release APK

生产发布建议创建自己的签名密钥，并配置 Android Gradle signingConfig。

不要把以下文件提交到 Git：

- `*.jks`
- `*.keystore`
- signing password
- `local.properties`

## 手机端服务器地址

公开版本不会写死服务器地址。安装后在 App 设置页填写：

```text
https://your-diary.example.com
```

本地 Android 模拟器访问电脑本机后端：

```text
http://10.0.2.2:8088
```
