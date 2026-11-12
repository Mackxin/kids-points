package xyz.yihaozhan.kidspoints;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * 极简 FileProvider：只服务于「缓存目录/update/」下的 APK，供系统安装器读取。
 *
 * 为什么不直接用 androidx.core 的 FileProvider：
 *   本项目刻意保持零第三方依赖（保证本机离线也能构建），而系统安装器只用到
 *   openFile() 和 query() 两件事，自己实现几十行就够，行为一致。
 *
 * 安全性：只按「单段文件名」在白名单目录里拼路径，含 .. / \ 的段一律拒绝，
 * 拼完再用 canonicalPath 复核一次，保证越不出 update 目录。
 */
public class ApkProvider extends ContentProvider {

    /** 必须与 AndroidManifest 里 provider 的 authorities 一致 */
    public static final String AUTHORITY = "xyz.yihaozhan.kidspoints.apk";

    /** 目录名固定为 cacheDir/update，下载代码和这里必须一致 */
    public static final String DIR = "update";

    /** 把缓存目录里的文件名拼成可对外临时授权的 content:// uri */
    public static Uri uriFor(String fileName) {
        return Uri.parse("content://" + AUTHORITY + "/apk/" + Uri.encode(fileName));
    }

    /** 从 uri 反解真实文件；任何可疑路径返回 null（宁可不给，也不能给错） */
    private File resolve(Uri uri) {
        List<String> seg = uri.getPathSegments();
        if (seg == null || seg.size() != 2 || !"apk".equals(seg.get(0))) return null;
        String name = seg.get(1);
        if (name == null || name.isEmpty()
                || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        File dir = new File(getContext().getCacheDir(), DIR);
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) return null;
        } catch (Exception e) {
            return null;
        }
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null || !f.isFile()) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    /** 部分机型的安装器会先 query 文件名和大小，缺了会显示「未知应用」甚至装不上 */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        if (f == null || !f.isFile()) return null;
        if (projection == null) {
            projection = new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        }
        MatrixCursor c = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i]))      row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(projection[i]))         row[i] = f.length();
            else                                                        row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
