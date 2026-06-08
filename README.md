# Premium IPTV Player for Android TV

[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%2F%20Leanback-green.svg)](https://developer.android.com/tv)
[![API Level](https://img.shields.io/badge/API-21%2B-blue.svg)](https://developer.android.com/about/dashboards)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()

一款专为 Android TV / 电视盒子打造的**高性能双引擎 IPTV 播放器**。针对大屏及遥控器操作进行了深度定制与极致优化，提供无缝切台、智能 EPG、多协议代理及软硬双解码支持。

---

## 🌟 核心特性 (Key Features)

### 1. 🚀 双播放引擎架构 (Dual-Engine Architecture)
*   **ExoPlayer (Google Media3)**: 作为默认播放引擎，完美支持 HLS (m3u8)、DASH、MP4 等标准流媒体协议，硬件加速解码，超低延迟。
*   **MPV Player (ffmpeg 软解)**: 集成自研 JNI 及 `libmpv.so`，利用 FFmpeg 强大的软解能力，彻底解决部分老旧电视盒子因缺少硬件解码器而导致 CCTV 等频道**有图无声 (AC3/MP2 音频格式)** 的问题。

### 2. ⚡ 无缝切台体验 (Seamless Channel Switching)
*   **首帧保留技术**: 切换台时，画面会**保持当前频道最后一帧的画面**，直到下一个频道完全加载并渲染出首帧后才进行平滑替换，告别切换过程中的黑屏等待与闪烁。
*   **加载反馈**: 界面右下角配有优雅的「正在加载...」动画与进度状态提示，极具视觉品质感。

### 3. 🎯 智能 EPG 模糊匹配 (Fuzzy Match EPG Engine)
*   **高性能匹配**: 采用高度优化的模糊拼音及字符相似度算法，自动将播放列表中的台名与 EPG 节目单进行高精度匹配。
*   **强兼容性**: EPG 数据抓取支持 HTTP 重定向跟随以及 GZIP 压缩自动解压，大幅降低网络流量并提升提升解析速度。

### 4. 🎛️ 遥控器与 TV UI 极致适配 (D-Pad Optimization)
*   **防丢焦设计**: 专为遥控器十字键 (D-Pad) 优化，列表焦点移动平滑，杜绝按键无响应及焦点丢失问题。
*   **快捷选台**: 稳定支持数字键直接选台及 OK/CENTER 键呼出频道列表与设置菜单。

---

## 🛡️ 核心源码防抄袭隔离说明 (Anti-Plagiarism Source Exclusion)

为了保护您的核心算法与商业机密，我们已在根目录的 `.gitignore` 中排除了以下关键源文件，**它们将不会被提交到 GitHub 公开仓库**：

*   **代理模块**: `ProxyManager.kt`, `SocksProxyServer.kt`, `ForceTVProxy.kt`（排除核心 P2P 与 Socks5 转流机制）
*   **本地服务器**: `LocalServer.kt`（排除本地 HTTP 接口调度逻辑）
*   **解析与解密引擎**: `PlaylistParser.kt`（排除播放列表解密及私有数据解析）
*   **EPG 算法**: `EpgManager.kt`（排除模糊匹配调度核心）

> [!CAUTION]
> ### 🚨 极其重要安全警告
> 由于上述文件被列入了 `.gitignore`，运行 `git add .` 和推送至 GitHub **不会备份这些关键文件**。
> **请务必在本地或私有网盘（如百度网盘、腾讯微云等）中保存好这些源文件的备份！切勿在上传 GitHub 后就直接删除本地工程目录，否则这些核心代码将会永久丢失！**

---

## 🏗️ 编译与运行 (Build & Run)

### 前提条件 (Prerequisites)
1.  **Android Studio** Koala (或更高版本)。
2.  **Android SDK 34**。
3.  **NDK 25+** (项目包含针对 `armeabi-v7a` 和 `arm64-v8a` 的预编译 `libmpv` 和 C++ 代码库)。

### 编译步骤 (Steps)
1.  克隆本项目到本地。
2.  用 Android Studio 打开项目根目录。
3.  确保 `local.properties` 正确指向您的 Android SDK 路径。
4.  **注意**：如果是直接从 GitHub 下载的开源版本，由于缺少上述被隔离的代理、解密、EPG匹配等关键源文件，直接编译会报错。您需要自行实现这些占位接口或使用本地备份文件覆盖恢复。

---

## 📤 准备发布到 GitHub (GitHub Publication Guide)

为了干净、安全地将项目发布到 GitHub，请严格按照以下步骤操作：

### 第一步：安装 Git (如果在本地尚未安装)
如果您在终端输入 `git` 提示命令不存在，请先安装 Git：
*   **Windows**: 推荐在 PowerShell 中运行以下命令安装：
    ```powershell
    winget install --id Git.Git -e --source winget
    ```
    安装后需要**重启终端/IDE**以使环境变量生效。

### 第二步：检查并使用 `.gitignore`
我们在项目根目录下配置了 [.gitignore](file:///.gitignore)，会自动帮您把**临时文件（如 build/ 缓存、decompile 的 .zip 和 .apk 临时文件）**以及上面的**核心机密源码**全部过滤。

### 第三步：在本地初始化 Git 仓库并提交代码
打开您的终端（或在 Android Studio 的 Terminal 中），执行以下命令：

1.  **初始化仓库**：
    ```bash
    git init
    ```
2.  **添加所有源码文件**（已配置忽略的文件会被自动忽略）：
    ```bash
    git add .
    ```
3.  **创建首次提交**：
    ```bash
    git commit -m "initial: 完成高性能双引擎播放器框架，优化无缝切台与EPG加载 UI"
    ```

### 第四步：在 GitHub 上创建新的仓库 (Repository)
1.  登录您的 [GitHub 账号](https://github.com/)。
2.  点击右上角加号 **`+`** -> **`New repository`**。
3.  填写仓库名称（例如 `Android-TV-IPTV-Player`），**不要勾选** "Add a README file"、"Add .gitignore" 或 "Choose a license"（因为我们本地已经配置好了）。
4.  点击 **`Create repository`**。

### 第五步：将本地仓库与 GitHub 关联并推送
在 GitHub 仓库创建成功后的页面上，复制远程仓库的地址（HTTPS 或 SSH），并在终端执行以下命令：

1.  **关联远程地址** (替换成您刚才创建的实际 GitHub URL)：
    ```bash
    git remote add origin https://github.com/你的用户名/Android-TV-IPTV-Player.git
    ```
2.  **将主分支重命名为 `main`**：
    ```bash
    git branch -M main
    ```
3.  **推送代码到 GitHub**：
    ```bash
    git push -u origin main
    ```

---

## 📜 开源协议 (License)

本项目采用 [MIT License](LICENSE) 开源协议。

*(注意：项目中引用的第三方二进制库如 libmpv, ForceTV 库等其版权归原作者所有)*
