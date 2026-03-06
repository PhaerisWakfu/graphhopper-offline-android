package com.example.graphhopper;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class GraphHopperFileManager {

    public static File getGraphHopperDir(Context context) {
        File dir = new File(context.getFilesDir(), "graphhopper");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getOsmFile(Context context) {
        return new File(getGraphHopperDir(context), "china-260125.osm.pbf");
    }

    public static File getGraphCacheDir(Context context) {
        return new File(getGraphHopperDir(context), "graph-cache");
    }

    /** 目录存在且含有图数据文件（避免误判为空目录）。 */
    public static boolean hasGraphData(File cacheDir) {
        if (cacheDir == null || !cacheDir.isDirectory()) return false;
        File[] files = cacheDir.listFiles();
        return files != null && files.length > 0;
    }

    /** 从 /sdcard/Download 复制 OSM 和 graph-cache 到内部存储（adb push 后打开应用即用）。 */
    public static boolean copyFromDownloadDirectory(Context context) {
        File downloadDir = getDownloadDirectory();
        if (downloadDir == null || !downloadDir.exists()) return false;
        boolean ok = false;
        File osmSource = new File(downloadDir, "china-260125.osm.pbf");
        File osmTarget = getOsmFile(context);
        if (osmSource.exists() && osmSource.canRead() && !osmTarget.exists()) {
            try {
                copyFile(osmSource, osmTarget);
                ok = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        File cacheSource = new File(downloadDir, "graph-cache");
        if (cacheSource.exists() && cacheSource.isDirectory() && cacheSource.canRead()) {
            if (copyGraphCache(context, cacheSource)) ok = true;
        }
        return ok;
    }

    /** 返回可读的「下载」目录，兼容 API 30+ 分区存储。 */
    @SuppressWarnings("deprecation")
    private static File getDownloadDirectory() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir.exists() && dir.canRead()) return dir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            File alt = new File(Environment.getExternalStorageDirectory(), "Download");
            if (alt.exists() && alt.canRead()) return alt;
        }
        return dir;
    }

    /** 从 assets/map/china-260125.osm.pbf 复制到内部存储。 */
    public static boolean copyOsmFromAssets(Context context) {
        File osmFile = getOsmFile(context);
        if (osmFile.exists()) {
            return true;
        }
        try {
            AssetManager am = context.getAssets();
            InputStream is = am.open("map/china-260125.osm.pbf");
            FileOutputStream fos = new FileOutputStream(osmFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();
            is.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 复制 graph-cache 目录到内部存储。 */
    public static boolean copyGraphCache(Context context, File sourceCacheDir) {
        File targetCacheDir = getGraphCacheDir(context);
        if (targetCacheDir.exists()) {
            return true;
        }
        return copyDirectory(sourceCacheDir, targetCacheDir);
    }

    private static boolean copyDirectory(File source, File target) {
        if (!source.exists() || !source.isDirectory()) {
            return false;
        }
        if (!target.exists()) {
            target.mkdirs();
        }
        File[] files = source.listFiles();
        if (files == null) return false;

        for (File file : files) {
            File targetFile = new File(target, file.getName());
            if (file.isDirectory()) {
                if (!copyDirectory(file, targetFile)) {
                    return false;
                }
            } else {
                try {
                    copyFile(file, targetFile);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return true;
    }

    private static void copyFile(File source, File target) throws IOException {
        FileInputStream fis = new FileInputStream(source);
        FileOutputStream fos = new FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int length;
        while ((length = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
        }
        fis.close();
        fos.close();
    }
}
