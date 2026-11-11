package xyz.yihaozhan.kidspoints;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 纯 WebView 壳：页面本体是 assets/index.html（由 Gradle 从项目根目录自动同步）。
 *
 * 用 WebView + 自实现 shouldInterceptRequest 提供 https://appassets.androidplatform.net/ 虚拟域，
 * 而不是 file:// 加载，好处：
 *   1. 页面 origin 是正常的 https，fetch 调 GitHub Gist API 不会被 CORS 拦住
 *   2. localStorage 有稳定的 origin，数据不丢
 */
public class MainActivity extends Activity {

    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://appassets.androidplatform.net/index.html";
    private static final int REQ_FILE = 1001;

    private WebView web;
    private android.widget.TextView loading;
    private ValueCallback<Uri[]> fileCallback;
    private long lastBackAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#F2F4F8"));
        w.setNavigationBarColor(Color.parseColor("#F2F4F8"));
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 加载遮罩：WebView 首帧绘制在部分设备上要几秒，先显示提示，onPageFinished 后移除
        loading = new android.widget.TextView(this);
        loading.setText("积分银行加载中…");
        loading.setTextColor(Color.parseColor("#8A93A6"));
        loading.setTextSize(15);
        loading.setGravity(android.view.Gravity.CENTER);
        loading.setBackgroundColor(Color.parseColor("#F2F4F8"));
        loading.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F2F4F8"));
        root.addView(web);
        root.addView(loading);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);        // localStorage —— 所有业务数据都存在这里
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);         // 页面走 https 虚拟域，无需 file 权限
        s.setAllowContentAccess(false);
        s.setTextZoom(100);                  // 不受系统字体缩放影响，布局不变形
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // debug 包开远程调试（chrome://inspect），发布包不开
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                android.util.Log.d("KPWEB", "onPageFinished " + url);
                // 不在这里收遮罩：DOM 完成 ≠ 首帧已绘制，提前收会露出白屏
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest req,
                                        android.webkit.WebResourceError err) {
                android.util.Log.e("KPWEB", "error " + req.getUrl() + " :: " + err.getDescription());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (ASSET_HOST.equals(u.getHost())) {
                    String path = u.getPath();
                    if (path == null || "/".equals(path)) path = "/index.html";
                    String asset = path.startsWith("/") ? path.substring(1) : path;
                    try {
                        InputStream is = getAssets().open(asset);
                        return new WebResourceResponse(mimeOf(asset), "utf-8", is);
                    } catch (Exception e) {
                        // favicon.ico 之类缺失的资源静默给 404，不算错误
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream("404".getBytes()));
                    }
                }
                return null;   // api.github.com 等外网请求走正常网络
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (ASSET_HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // 🔴 WebView 默认不弹 alert/confirm —— 不实现则 confirm 永远返回 false，
            //    删除记录、清空数据、兑换确认、批量删除确认会全部失效
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("知道了", (d, i) -> result.confirm())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("确定", (d, i) -> result.confirm())
                        .setNegativeButton("取消", (d, i) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            // 「恢复数据」用的 <input type="file">
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "选择备份文件"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl(START_URL);

        // 首帧真正绘制完成后再收遮罩（onPageFinished 时往往还没画出来，会露白屏）
        web.postVisualStateCallback(1, new WebView.VisualStateCallback() {
            @Override
            public void onComplete(long requestId) {
                runOnUiThread(MainActivity.this::hideLoading);
            }
        });
        // 兜底：8 秒还没画出来也收掉，避免一直卡在提示上
        web.postDelayed(this::hideLoading, 8000);
    }

    private void hideLoading() {
        if (loading != null) {
            loading.setVisibility(View.GONE);
            loading = null;
        }
    }

    /** JS 桥：WebView 不支持 blob 链接下载，导出备份/报告图片/PDF 走这里写进「下载」目录
     *  返回 null = 成功，返回字符串 = 失败原因（页面据此提示，不能一律报"已保存"） */
    class Bridge {
        @JavascriptInterface
        public String saveBase64(String name, String base64) {
            String fname = (name == null || name.trim().isEmpty()) ? "kids-points" : name.trim();
            // 文件名里的路径分隔符会让 MediaStore 直接插不进去
            fname = fname.replace("/", "_").replace("\\", "_");
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fname);
                cv.put(MediaStore.Downloads.MIME_TYPE, mimeOf(fname));
                Uri uri = getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "无法创建文件";
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) return "无法写入文件";
                    os.write(bytes);
                    os.flush();
                }
                return null;
            } catch (Exception e) {
                return e.getMessage() == null ? "写入失败" : e.getMessage();
            }
        }
    }

    private void toastOnUi(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private static String mimeOf(String path) {
        String n = path.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (fileCallback != null) {
                Uri[] out = null;
                if (res == RESULT_OK && data != null && data.getData() != null) {
                    out = new Uri[]{ data.getData() };
                }
                fileCallback.onReceiveValue(out);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    /** 返回键：批量选择中先退出批量 → 不在记分页先回记分页 → 记分页连按两次退出（防误触） */
    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && web != null) {
            web.evaluateJavascript(
                    "(function(){"
                            + "if(typeof batchMode!=='undefined'&&batchMode){toggleBatchMode();return 2;}"
                            + "var a=document.querySelector('.tab-page.active');"
                            + "if(a&&a.id!=='tab-record'){switchTab('record',document.querySelector('nav button'));return 1;}"
                            + "return 0;})()",
                    value -> {
                        if ("1".equals(value) || "2".equals(value)) return;
                        long now = System.currentTimeMillis();
                        if (now - lastBackAt < 2000) finish();
                        else { lastBackAt = now; toastOnUi("再按一次退出"); }
                    });
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
