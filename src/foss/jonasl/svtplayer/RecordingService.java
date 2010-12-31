
package foss.jonasl.svtplayer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import foss.jonasl.svtplayer.M3U8.M3U8Entry;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

public class RecordingService extends IntentService {

    public RecordingService() {
        super("RecordingService");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            L.d("no external storage available");
            return;
        }
        String urlString = intent.getStringExtra("url");
        int id = intent.getIntExtra("id", -1);
        if (TextUtils.isEmpty(urlString) || id < 0) {
            L.d("invalid recording parameters");
            return;
        }
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            L.d("invalid recording url");
            return;
        }
        File ownDir = new File(Environment.getExternalStorageDirectory(), "svtplayer");
        File tmpDir = new File(ownDir, ".tmp");
        File workDir = new File(tmpDir, String.valueOf(id));
        workDir.mkdirs();
        if (!workDir.exists()) {
            L.d("can't create workDir");
            return;
        }

        int bytesFree = getFreeBytes();
        if (bytesFree < 1024 * 1024 * 1024) {
            L.d("not enough space");
            return;
        }

        boolean again = false;
        M3U8 playlist = new M3U8(uri);
        do {
            if (playlist.load() == M3U8.LOAD_FAILED_IO) {
                L.d("playlist load io error, trying again");
                again = true;
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                    again = false;
                }
            } else {
                again = false;
            }
        } while (again);
        if (!playlist.isLoaded()) {
            L.d("can not load playlist");
            return;
        }
        M3U8 toUse = playlist;
        if (playlist.getVariants() != null) {
            for (M3U8 variant : playlist.getVariants()) {
                if (variant.getBandwidth() > toUse.getBandwidth()) {
                    toUse = variant;
                }
            }
            L.d("loading variant playlist " + toUse.getBandwidth() + " " + toUse.getUri());
            do {
                if (toUse.load() == M3U8.LOAD_FAILED_IO) {
                    again = true;
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (InterruptedException e) {
                        again = false;
                    }
                } else {
                    again = false;
                }
            } while (again);
            if (!toUse.isLoaded()) {
                L.d("can not load variant playlist");
                return;
            }
        }
        if (toUse.getEntries() == null || toUse.getEntries().size() == 0) {
            L.d("variant playlist has no entries");
            return;
        }
        if (!toUse.hasEnd()) {
            L.d("playlist has no end tag");
            return;
        }
        L.d("entries: " + toUse.getEntries().size() + " duration: " + toUse.getDuration());
        StatKeeper stat = new StatKeeper(id, 2, toUse.getDuration(), -1);
        stat.start();
        for (int i = 0; i < toUse.getEntries().size(); i++) {
            do {
                again = false;
                M3U8Entry entry = toUse.getEntries().get(i);
                File target = new File(workDir, String
                        .format("%d_%07d.ts", toUse.getBandwidth(), i));
                L.d("DL: " + entry.getUri() + " -> " + target);
                try {
                    if (!downloadFile(stat, entry.getUri(), target)) {
                        L.d("aborting, local write failed");
                        return;
                    }
                    stat.deliverSecs(entry.getDuration());
                } catch (ClientProtocolException e) {
                    L.d("ClientProtocolException:" + e.getMessage());
                    return;
                } catch (IOException e) {
                    L.d("IOException:" + e.getMessage());
                    again = true;
                } catch (Exception e) {
                    L.d("Exception:" + e.getMessage());
                    return;
                }
                if (again) {
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (InterruptedException ie) {
                        L.d("thread interrupted");
                        return;
                    }
                }
            } while (again);
            if (i > 10) {
                //break;
            }
        }
        stat.stop();
    }

    private void cleanUpDir(File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        try {
            for (String file : dir.list()) {
                File tmp = new File(dir, file);
                boolean res = tmp.delete();
                L.d((res ? "deleted " : "failed to delete ") + file);
            }
        } catch (SecurityException e) {
            L.d("SecurityException while cleaning " + dir);
        }
    }

    private boolean downloadFile(StatKeeper stat, URI uri, File target)
            throws ClientProtocolException, IOException {
        BufferedOutputStream outStream = null;
        try {
            HttpGet request = new HttpGet();
            request.setURI(uri);
            HttpResponse response = Utils.getHttpClient().execute(request);

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                L.d("response entity is null, will throw");
            }
            InputStream inStream = entity.getContent();
            if (inStream == null) {
                L.d("response stream is null, will throw");
            }
            long contentLength = entity.getContentLength();
            if (contentLength > 0) {
                boolean exists = false;
                long length = -1;
                try {
                    exists = target.exists();
                    length = target.length();
                } catch (Exception e) {
                    L.d("could not access " + target + " : " + e.getMessage());
                    return false;
                }
                if (exists && length == contentLength) {
                    L.d("exists with same size, skipping: " + target);
                    inStream.close();
                    return true;
                } else {
                    if (getFreeBytes() < 2 * contentLength) {
                        L.d("not enough space");
                        return false;
                    }
                }
            }
            try {
                outStream = new BufferedOutputStream(new FileOutputStream(target), 8192);
            } catch (Exception e) {
                L.d("could not access " + target + " : " + e.getMessage());
                return false;
            }
            byte[] buf = new byte[8192];
            int read = 0;
            while ((read = inStream.read(buf)) != -1) {
                try {
                    outStream.write(buf, 0, read);
                    stat.deliverBytes(read);
                } catch (Exception e) {
                    L.d("could not write to " + target + " : " + e.getMessage());
                    return false;
                }
            }
            return true;
        } finally {
            if (outStream != null) {
                try {
                    outStream.flush();
                    outStream.close();
                } catch (Exception e) {
                    L.d("could not flush and close " + target + " : " + e.getMessage());
                    return false;
                }
            }
        }
    }

    private int getFreeBytes() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().toString());
        int freeBytes = stat.getAvailableBlocks() * stat.getBlockSize();
        L.d("freeBytes: " + freeBytes);
        return freeBytes;
    }

    private class StatKeeper {
        private Timer mTimer;
        private int mId;
        private int mInterval;
        private int mDuration;
        private long mLength;

        private long mBytes;
        private long mAccBytes;
        private int mSecs;
        private int mAccSecs;

        public StatKeeper(int id, int interval, int duration, long length) {
            mId = id;
            mInterval = interval;
            mDuration = duration;
            mLength = length;
        }

        private synchronized void reset() {
            mBytes = 0;
            mAccBytes = 0;
            mSecs = 0;
            mAccSecs = 0;
        }

        public synchronized void start() {
            reset();
            if (mTimer == null) {
                mTimer = new Timer();
                mTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        tick();
                    }
                }, mInterval * 1000, mInterval * 1000);
            }
        }

        public synchronized void stop() {
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            tick();
        }

        public void deliverBytes(int bytes) {
            mBytes += bytes;
            mAccBytes += bytes;
        }

        public void deliverSecs(int secs) {
            mSecs += secs;
            mAccSecs += secs;
        }

        private synchronized void tick() {
            long bytesPerSecond = mBytes / mInterval;
            long kiloBytesPerSecond = bytesPerSecond / 1024;
            int progress = -1;
            if (mLength > 0) {
                progress = (int) (100 * mAccBytes / mLength);
            } else if (mDuration > 0) {
                progress = (int) (100 * mAccSecs / mDuration);
            }

            L.d("id: " + mId + " rate: " + kiloBytesPerSecond + " kb/s progress: "
                    + (progress >= 0 ? String.valueOf(progress) : "unknown"));
            mBytes = 0;
        }
    }
}
