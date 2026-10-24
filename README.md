# 小瑞的助手 / XiaoRuiAssistant

基于 [Eta](https://github.com/Mangi-11/Eta) 的独立 Android 应用修改版。

本仓库用于构建修改版 APK。源码需上传到仓库根目录（包含 `gradlew`、`settings.gradle.kts` 和 `app/`）。

在 GitHub 的 Actions 页面选择 **Build XiaoRuiAssistant APK**，点击 **Run workflow**；构建成功后在运行详情底部的 Artifacts 下载 APK。

应用需使用独立的 `applicationId`，以避免覆盖原版 Eta。
