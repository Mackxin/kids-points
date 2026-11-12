# 儿童积分银行 · 安卓 App

把项目根目录的 `index.html`（单文件网页应用）装进一个极简 WebView 壳。
**页面代码只有一份**：`index.html`。构建时由 Gradle 自动拷贝进 `app/src/main/assets/`，不要直接改副本。

## 构建

```bash
cd android
./build.sh
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`（约 280 KB，含本地导出库）

## 安装

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

或者把 APK 拷到手机上点开安装（需允许「安装未知来源应用」）。

## 数据存在哪

| 层 | 位置 | 说明 |
|---|---|---|
| 本机 | WebView 的 localStorage | 日常读写，秒开 |
| 云端 | 你的 GitHub Gist（私有） | 设置页「☁️ 云同步」手动上传/恢复 |

⚠️ **卸载 App 会清掉本机数据**。换手机或重装前，先点一次「⬆️ 上传到云端」。

## 配置云同步（一次性）

1. 手机浏览器或电脑打开 GitHub → **Settings** → **Developer settings**
2. **Personal access tokens** → **Tokens (classic)** → **Generate new token (classic)**
3. 勾选 **`gist`** 这一个权限即可，有效期按需选
4. 生成后复制 token（`ghp_...` 开头，只显示一次）
5. 回到 App → 设置 → 云同步 → 粘贴 token → 点「⬆️ 上传到云端」
6. 首次上传会自动创建私有 Gist，并把 Gist ID 回填到输入框

换手机时：装 App → 填同一个 token → 填（或粘贴）原来的 Gist ID → 点「⬇️ 从云端恢复」。

> Token 只存在本机 localStorage，**不会写进导出的备份文件**。

## 技术要点（改代码时注意）

- 页面用 `https://appassets.androidplatform.net/index.html` 加载（`shouldInterceptRequest` 从 assets 供文件），不是 `file://`。这样 origin 是正常 https，`fetch` 调 GitHub API 不会被 CORS 拦，localStorage 也有稳定 origin。
- WebView **默认不弹 `alert`/`confirm`**，必须由 `WebChromeClient` 自己实现，否则删除/兑换等确认框全部失效（已在 `MainActivity` 里实现）。
- WebView **不支持 blob 链接下载**，导出备份/报告走 JS 桥 `AndroidBridge.saveBase64()` 写进系统「下载」目录（页面里统一用 `saveFile()` 包装）。
- 返回键：不在记分页先回记分页；在记分页连按两次退出。
- 页面改动后必须重新构建，assets 才会更新。
- 导出报告用的 `html2canvas` / `jsPDF` 已放进 `app/src/main/assets/vendor/`（页面优先加载本地副本，**离线也能导出**；网页版没有该目录会自动回退 CDN）。
- 调试：debug 包已开 `WebView.setWebContentsDebuggingEnabled`，可用 `chrome://inspect` 或 CDP 连 `webview_devtools_remote_<pid>`。

## 版本更新（App 内检查 + 安装）

更新源是 GitHub Release（仓库已公开，匿名可读）：

- 检查：页面 `fetch https://api.github.com/repos/Mackxin/kids-points/releases/latest`，拿 `tag_name`（如 `v1.2.1`）和 `assets[].browser_download_url`，与当前版本比大小。
- 当前版本：App 内读原生 `versionName`（`Bridge.getVersionName()`），网页版用 `index.html` 里的 `APP_VERSION` 常量兜底。
- 下载：JS 桥 `Bridge.downloadAndInstall(url, name)` → 后台线程下载到 `cacheDir/update/xxx.apk`（手动跟 302，GitHub 会跳到 objects.githubusercontent.com），进度通过 `window.__onUpdateProgress(pct)` 回传页面。
- 安装：`ApkProvider`（**自己实现的极简 FileProvider**，为了保持零第三方依赖）把缓存里的 APK 用 `content://` 临时授权给系统安装器，`ACTION_VIEW` + `application/vnd.android.package-archive`。
- 权限：`REQUEST_INSTALL_PACKAGES`。首次点下载时若系统未授权，会自动跳到「安装未知应用」设置页，回来再点一次即可。

**发新版的完整流程**：

1. 改 `android/app/build.gradle` 的 `versionCode` +1、`versionName` 改新版本号；
2. 改 `index.html` 里的 `APP_VERSION` 常量（保持一致，网页版判断才准）；
3. `cd android && ./build.sh`（或 `assembleRelease`）出 APK；
4. 打 tag 并建 Release，把 APK 作为 asset 上传（tag 用 `v1.2.1` 这种格式）。

```bash
export PATH="$HOME/.local/bin:$PATH"
git tag v1.2.0 && git push origin v1.2.0
gh release create v1.2.0 android/app/build/outputs/apk/release/app-release.apk \
  -t "v1.2.0" -n "更新说明…"
```

## 已处理的 WebView 坑（改动时别踩回去）

| 坑 | 表现 | 处理 |
|---|---|---|
| 不实现 `onJsConfirm` | 所有确认框静默返回 false，删除/兑换失效 | `WebChromeClient` 自实现 alert/confirm |
| blob 链接下载 | 导出/备份静默失败 | JS 桥 `saveBase64()` 写「下载」目录，页面统一走 `saveFile()` |
| `onPageFinished` 收加载遮罩 | DOM 好了但没画出来，露白屏 | 等 `postVisualStateCallback`，再加 8 秒兜底 |
| 导出库 CDN 加载失败 | 「导出组件加载失败」且**永久失败** | 本地 vendor 优先 + 失败不缓存 Promise（可重试） |
| 批量模式切宝宝 | 选中集合残留，会删到上一个宝宝的数据 | `switchChild` 里退出批量并清空选中 |
| `file://` 给安装器 | targetSdk 24+ 直接 `FileUriExposedException` | 自写 `ApkProvider` 走 `content://` + 临时读授权 |
| 没授「安装未知应用」 | 安装器一闪而过或直接被拒 | 先查 `canRequestPackageInstalls()`，没有就跳系统设置页 |
