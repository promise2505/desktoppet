# 手机桌宠

Android 7.0+ 的悬浮桌宠原型：透明 PNG 角色、自动行走、撞边掉头、拖动、长按暂停/继续。

## 本地编译

需要 JDK 17、Android SDK 35、Gradle 8.7。

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

仓库中的 `.github/workflows/build-apk.yml` 会在 push 后自动编译，并把 APK 作为 Actions artifact 保存。

## 手机权限

首次启动时需要允许“显示在其他应用上层”。Android 13+ 还会请求通知权限，因为桌宠使用前台服务保持运行。
