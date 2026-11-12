# 儿童积分银行

给小朋友记「好习惯积分」的小工具：表现好加分、做错事扣分，攒够分换礼物。

单文件网页应用 + 安卓 App（WebView 壳），**数据全存本地**，可选同步到自己的 GitHub Gist。

## 下载安装（安卓）

📱 **[点这里下载最新 APK](https://github.com/Mackxin/kids-points/releases/latest)**

装好后在 App 里：设置 → 🔄 版本更新 → 检查更新，以后升级不用再回来手动下载。

> 首次安装需要在手机上允许一次「安装未知来源应用」。

## 功能

| 模块 | 说明 |
|---|---|
| 记分 | 点奖励/惩罚项目一键记分；也可以自定义加减分，或对着积分表输序号批量记 |
| 补记 | 忘了记的那天，把「记分日期」切到那一天，之后记的分都落在那天 |
| 明细 | 累计加分/扣减、近 7 天走势图、按年月日+类型筛选、点单条可编辑、批量删除 |
| 兑换 | 按目标攒分，进度条显示还差多少；兑换后自动扣分并留痕 |
| 多宝宝 | 每个宝宝积分独立，顶部一键切换 |
| 导出 | 导出积分报告为图片 / PDF；也可以导出 JSON 备份 |
| 云同步 | 用 GitHub Gist 存一份，换手机能找回 |

## 数据存在哪

| 层 | 位置 | 说明 |
|---|---|---|
| 本机 | 浏览器 / App 的 localStorage | 日常读写，秒开 |
| 云端 | 你自己的 GitHub 私有 Gist | 设置页手动上传 / 恢复，可选 |

⚠️ **卸载 App 会清掉本机数据**。换手机或重装前，先点一次「⬆️ 上传到云端」或「📤 备份数据」。

## 网页版

直接用浏览器打开 `index.html` 就行，不需要服务器、不需要联网（导出图片/PDF 时要联网加载组件，App 内已内置可离线导出）。

## 自己构建

安卓工程在 `android/`，页面本体就是根目录的 `index.html`（构建时自动同步进 assets，不要改副本）。

```bash
cd android
./build.sh          # 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

发新版流程、WebView 踩过的坑，见 [`android/README.md`](android/README.md)。

## 技术栈

- 页面：原生 HTML/CSS/JS，单文件，零框架、零构建
- App：Android WebView 壳（Java，零第三方依赖），minSdk 29 / targetSdk 34
- 导出：html2canvas + jsPDF（本地内置）
- 同步：GitHub Gist API

## License

MIT
