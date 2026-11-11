# 儿童积分银行 · 安卓 App

把项目根目录的 `index.html`（单文件网页应用）装进一个极简 WebView 壳。
**页面代码只有一份**：`index.html`。构建时由 Gradle 自动拷贝进 `app/src/main/assets/`，不要直接改副本。

## 构建

```bash
cd android
./build.sh
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`（约 60 KB）

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
- 调试：debug 包已开 `WebView.setWebContentsDebuggingEnabled`，可用 `chrome://inspect` 或 CDP 连 `webview_devtools_remote_<pid>`。
