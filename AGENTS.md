# PocketAgent — 项目约定

## 构建（重要）

这个项目**只打 release 包**，不再产出 debug 包。

- 环境：JDK 17 在 `E:\jdk17`，Gradle 9.8.1 独立安装在 `E:\gradle98\bin\gradle.bat`，
  `GRADLE_USER_HOME` 必须指向 `E:\gradle-home`（wrapper 目录里只有未下载完的 zip.part）。
- 项目**没有** gradlew / wrapper 脚本，直接用上面那套 Gradle。
- 命令（PowerShell）：

  ```powershell
  $env:JAVA_HOME="E:\jdk17"
  $env:GRADLE_USER_HOME="E:\gradle-home"
  & E:\gradle98\bin\gradle.bat :app:assembleRelease --console=plain
  ```

- 产物：`app/build/outputs/apk/release/app-release.apk`（release 用 debug keystore 签名，可直接装机）。
- 装机：`E:\AndroidSDK\platform-tools\adb.exe install -r <apk>`，装完 `adb shell am start -W -n com.pocketagent.app/.MainActivity`
  并扫一遍 `adb logcat` 确认没有 `FATAL EXCEPTION`（R8 开了 minify + shrinkResources）。
- 只编译检查用 `:app:compileReleaseKotlin`，比整包快。

## 代码位置速查

- `app/src/main/java/com/pocketagent/app/AppViewModel.kt`：主链路（send → agentTurn → buildHistory / maybeCompact → 工具循环）、
  上下文预算与压缩、系统提示。
- `app/src/main/java/com/pocketagent/app/agent/ToolTypes.kt`：工具 schema、权限档位（`ToolPolicy`）、`MAX_RESULT_CHARS`。
- `app/src/main/java/com/pocketagent/app/agent/ToolRunner.kt`：工具具体实现。
- `app/src/main/java/com/pocketagent/app/agent/OfficeFiles.kt`：docx / xlsx / pptx 的读取、填写与校验（纯 JDK，可 JVM 直跑）。
- `app/src/main/java/com/pocketagent/app/agent/OfficeCreate.kt`：**原生 OOXML 生成**（`docx_create` / `pptx_create` 的包骨架与模板）。
  纯 JDK，没有 Android 依赖 —— 改它之后优先用 kotlin-compiler-embeddable 直跑一份产物，
  再用 Word / PowerPoint 的 COM 打开验证（WPS 能开不代表 Office 能开，fmtScheme 条目数就只坑 Office）。
- `app/src/main/java/com/pocketagent/app/App.kt`：启动时的**升级残留清理**（`CLEANUP_VERSION`）。
- `app/src/main/java/com/pocketagent/app/agent/WorkspaceFs.kt`：SAF 工作区读写（读窗口上限跟随 `MAX_RESULT_CHARS`）。
- `app/src/main/java/com/pocketagent/app/net/ChatClient.kt`：HTTP/SSE、token 估算。
- `app/src/main/java/com/pocketagent/app/data/ModelCapabilities.kt`：模型的上下文窗口识别。

## 约定

- 工具结果是**回灌给模型的**，任何工具自己声明的窗口都必须 ≤ `ToolPolicy.MAX_RESULT_CHARS`，
  否则会被 `truncateForModel` 从中间截断，模型拿到的东西和 schema 承诺的不一致。
- 上下文相关的预算全部按 **token** 算（`TokenEstimator`），不要再用"字符数 × 系数"。
- **删功能必须配一条升级清理**：覆盖安装不会动 app 私有目录，旧数据会变成没人能删的残留。
  在 `App.kt` 的 `CLEANUP_VERSION` 里加一档，并在设备上确认清理真的跑过。
- **`.docx/.xlsx/.pptx` 一律不能按文本写**：文本写入工具会拦（`rejectOfficeTextPath`），
  新建走 `docx_create` / `pptx_create`，改已有文件走 `docx_fill` / `pptx_fill`。
- 改完后先 `:app:compileReleaseKotlin`，再 `:app:assembleRelease`，然后装机启动确认。
