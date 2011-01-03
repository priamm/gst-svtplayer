
package foss.jonasl.svtplayer.utils;

import java.io.File;

import foss.jonasl.svtplayer.L;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

public class Utils {

    public static int getExternalFreeBytes() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().toString());
        int freeBytes = stat.getAvailableBlocks() * stat.getBlockSize();
        return freeBytes;
    }

    public static void deleteDir(File dir) {
        try {
            if (!dir.isDirectory()) {
                return;
            }
            boolean res;
            for (String file : dir.list()) {
                File tmp = new File(dir, file);
                res = tmp.delete();
                L.d((res ? "deleted " : "failed to delete ") + file);
            }
            res = dir.delete();
            L.d((res ? "deleted " : "failed to delete ") + dir);
        } catch (SecurityException e) {
            L.d("SecurityException while deleting " + dir);
        }
    }

    public static int getDirSize(File dir) {
        int res = 0;
        try {
            if (dir.isDirectory()) {
                for (String file : dir.list()) {
                    res += file.length();
                }
            }
        } catch (Exception e) {
            L.d(e.toString());
        }
        return res;
    }

    public static File getOwnDir() {
        return new File(Environment.getExternalStorageDirectory(), "svtplayer");
    }

    public static File getPVRWorkDir(long id) {
        File pvrDir = new File(getOwnDir(), ".pvr");
        return new File(pvrDir, String.valueOf(id));
    }

    public static void invokeMediaScanner(Context context, File file) {
        SimpleScannerClient client = new SimpleScannerClient(file.getAbsolutePath());
        MediaScannerConnection connection = new MediaScannerConnection(context, client);
        client.setConnection(connection);
        connection.connect();
    }

    private static class SimpleScannerClient implements
            MediaScannerConnection.MediaScannerConnectionClient {

        private String mPath;
        private MediaScannerConnection mConnection;

        public SimpleScannerClient(String path) {
            mPath = path;
        }

        public void setConnection(MediaScannerConnection connection) {
            mConnection = connection;
        }

        @Override
        public void onMediaScannerConnected() {
            if (mConnection != null && mPath != null) {
                mConnection.scanFile(mPath, null);
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            L.d("onScanCompleted: " + path);
        }
    }
}
