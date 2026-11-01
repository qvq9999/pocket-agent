# PocketAgent

运行在 Android 手机上的本地文件 Agent。用你自己的模型 API Key，让 AI 直接读写手机上的文件、生成 Word / PPT，全部在这台设备上完成。

不内置模型、不经过中转服务器、不上传你的文件。

## 功能

- **工作区读写**：通过系统目录授权选一个文件夹，AI 只能在这个目录内读写，越界操作直接拒绝
- **原生生成 Office**：`docx_create` / `pptx_create` 直接在手机上写 OOXML 包，不联网、不跑脚本、一次调用出成品
- **文档编辑**：`docx_fill` / `xlsx_set` / `pptx_fill` 按段落、表格单元格、占位符精确修改，全成功或全不改
- **技能手册**：内置主题工厂、品牌规范、文档协作三套操作手册，也可从 GitHub 或本地 zip 安装自己的
- **联网搜索**：按需检索，匿名额度每天 300 次，填自己的 AnySearch Key 可提到 1500 次/天
- **可选 Linux 环境**：装一个 Ubuntu 沙盒跑 bash 命令，不装也能用全部文件功能
- **应用内更新**：自动检查新版本，下载走国内镜像，安装完成后自动清理安装包

## 权限模型

三档，可在输入框上方的权限胶囊里随时切换：

| 档位 | 行为 |
|---|---|
| 只读 | 写、删、执行类工具从工具表移除，模型看不到 |
| 需确认 | 每次写入或删除弹确认并展示 diff，可「永久允许」 |
| 允许 | 自动执行，但先拍快照，可在工具卡里撤销 |

三种情况即使「允许」也会硬拦截：越界路径、备份失败、文件被外部改动过。
删除类操作不提供「永久授权」，只在本会话内放行。

## 构建

本项目**只产出 release 包，没有 debug 构建，也没有 gradlew wrapper**。

环境要求：

- JDK 17
- Gradle 9.x（独立安装，不走 wrapper）
- Android SDK，`minSdk 26 / targetSdk 37`

```bash
export JAVA_HOME=/path/to/jdk17
export GRADLE_USER_HOME=/path/to/gradle-home
gradle :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 签名

release 构建读根目录的 `keystore.properties`：

```properties
storeFile=keystore/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

该文件与 `*.jks` 都在 `.gitignore` 里，不进版本库。文件缺失时回退 debug 签名——能装，但不能用于发布，也无法覆盖安装已发布的版本。

## 发版

```bash
python scripts/release_manifest.py --notes "本次更新内容"
```

脚本从 `app/build.gradle.kts` 读出 `versionCode` / `versionName`，计算 APK 的 SHA-256，生成 `dist/version.json` 与 `dist/app-release.apk`。把这两个文件作为附件上传到 GitHub Release 即可。

客户端固定读取：

```
https://github.com/qvq9999/pocket-agent/releases/latest/download/version.json
```

所以**文件名必须是 `version.json` 和 `app-release.apk`**，改了客户端就断链。

### 更新判据

- 只比较 `versionCode`（整数单调递增），`versionName` 仅用于展示
- `minSupported` 高于客户端版本时强制更新，弹窗不可取消
- 清单缺少 `sha256` 或下载地址不是 https 时，降级为「打开浏览器」，不自动安装
- 同一版本用户点过「取消」后不再自动提醒，只在设置页显示

## 目录结构

```
app/src/main/java/com/pocketagent/app/
├── AppViewModel.kt          主链路：send → agentTurn → 工具循环、上下文预算与压缩
├── AppRoot.kt               导航栈、抽屉、各类浮层
├── App.kt                   启动清理（升级残留、已安装的更新包）
├── agent/
│   ├── ToolTypes.kt         工具 schema 与权限档位
│   ├── ToolRunner.kt        工具实现
│   ├── OfficeFiles.kt       docx / xlsx / pptx 的读取与填写
│   ├── OfficeCreate.kt      原生 OOXML 生成
│   └── WorkspaceFs.kt       SAF 工作区读写
├── data/                    存档、模型目录、技能、更新状态
├── net/                     对话、发现、搜索、更新检查与下载
├── service/                 前台服务（长任务保活）
└── ui/                      Compose 界面
```

## 已知限制

- Word 读取单次上限 18000 字符且不能翻页，超长文档后半段读不到
- `docx_fill` 一次只处理一个文件，多个文档需要逐个来
- 生成的 Word / PPT 不含图片、图表、母版和动画
- 文本框、形状里的文字读不到也改不了；页眉页脚只支持文字替换
- Linux 环境有自己的文件系统，看不到工作区

## 许可

未指定。仓库内代码仅供学习与个人使用。
