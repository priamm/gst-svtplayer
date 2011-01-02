
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
import org.apache.http.client.methods.HttpHead;

import foss.jonasl.svtplayer.M3U8.M3U8Entry;

import android.app.IntentService;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
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
        File recDir = new File(ownDir, ".rec");
        File workDir = new File(recDir, String.valueOf(id));
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
                    if (!downloadFile(entry.getUri(), target, stat)) {
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
        }
        stat.stop();

        String input = new File(workDir, String.valueOf(toUse.getBandwidth()) + "_%07d.ts")
                .getAbsolutePath();
        File outputFile = new File(ownDir, id + ".mp4");
        if (outputFile.exists()) {
            int cnt = 2;
            do {
                outputFile = new File(ownDir, id + "-" + cnt + ".mp4");
                cnt++;
            } while (outputFile.exists());
        }
        String pipeline = getTSPartsToMP4Pipeline(input, outputFile.getAbsolutePath());
        L.d("running: " + pipeline);
        boolean pipelineRes = Native.runPipeline(pipeline);
        L.d("result: " + pipelineRes);
        DummyScannerClient client = new DummyScannerClient(
                outputFile.getAbsolutePath()); 
        MediaScannerConnection connection = new MediaScannerConnection(this, client);
        client.setConnection(connection);
        connection.connect();
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

    private boolean downloadFile(URI uri, File target, StatKeeper stat)
            throws ClientProtocolException, IOException {
        BufferedOutputStream outStream = null;
        try {
            boolean append = false;
            long length = -1;
            try {
                append = target.exists();
                length = target.length();
            } catch (Exception e) {
                L.d("could not access " + target + " : " + e.getMessage());
                return false;
            }

            HttpGet request = new HttpGet();
            HttpResponse response;
            if (append) {
                HttpHead head = new HttpHead();
                head.setURI(uri);
                response = Utils.getHttpClient().execute(head);
                if (response.containsHeader("Content-Length")) {
                    int contentLenght = Integer.valueOf(response.getFirstHeader("Content-Length")
                            .getValue());
                    if (length < contentLenght) {
                        request.addHeader("Range", "bytes=" + length + "-"
                                + String.valueOf(contentLenght - 1));
                    } else if (length == contentLenght) {
                        L.d("size matches, skipping");
                        return true;
                    } else {
                        L.d("local file too big, downloading again");
                        append = false;
                    }
                } else {
                    L.d("HEAD has no Content-Length");
                    append = false;
                }
            }
            request.setURI(uri);
            response = Utils.getHttpClient().execute(request);
            if (append && response.getStatusLine().getStatusCode() != 206) {
                L.d("expected partial content 206 but got "
                        + response.getStatusLine().getStatusCode() + ", deleting file and throw");
                try {
                    target.delete();
                } catch (Exception e) {
                    L.d("could not access " + target + " : " + e.getMessage());
                    return false;
                }
                throw new IOException("expected partial content 206 but got "
                        + response.getStatusLine().getStatusCode());
            }

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
                if (getFreeBytes() < 2 * contentLength) {
                    L.d("not enough space");
                    return false;
                }
            }
            try {
                outStream = new BufferedOutputStream(new FileOutputStream(target, append), 8192);
            } catch (Exception e) {
                L.d("could not access " + target + " : " + e.getMessage());
                return false;
            }
            byte[] buf = new byte[8192];
            int read = 0;
            while ((read = inStream.read(buf)) != -1) {
                try {
                    outStream.write(buf, 0, read);
                    if (stat != null) {
                        stat.deliverBytes(read);
                    }
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

    public static String getTSPartsToMP4Pipeline(String input, String output) {
        L.d("input: " + input);
        L.d("output: " + output);
        StringBuilder b = new StringBuilder();
        b.append("mp4mux name=muxer ! filesink location=").append(output).append(
                " multifilesrc location=").append(input).append(" ! mpegtsdemux name=demuxer")
                .append(" demuxer. ! queue ! aacparse ! aacfilter ! muxer.audio_00").append(
                        " demuxer. ! queue ! h264parse output-format=0 access-unit=true").append(
                        " ! h264filter ! muxer.video_00");
        return b.toString();
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
        private long mStartTime;

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
                        tick(true);
                    }
                }, mInterval * 1000, mInterval * 1000);
                mStartTime = SystemClock.elapsedRealtime();
            }
        }

        public synchronized void stop() {
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            tick(false);
        }

        public void deliverBytes(int bytes) {
            mBytes += bytes;
            mAccBytes += bytes;
        }

        public void deliverSecs(int secs) {
            mSecs += secs;
            mAccSecs += secs;
        }

        private synchronized void tick(boolean fromTimer) {
            long bytesPerSecond;
            if (fromTimer) {
                bytesPerSecond = mBytes / mInterval;
            } else {
                long period = (SystemClock.elapsedRealtime() - mStartTime) / 1000;
                if (period == 0) {
                    period = 1;
                }
                bytesPerSecond = mAccBytes / period;
            }
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

    private class DummyScannerClient implements MediaScannerConnection.MediaScannerConnectionClient {

        private String mPath;
        private MediaScannerConnection mConnection;

        public DummyScannerClient(String path) {
            mPath = path;
        }

        public void setConnection(MediaScannerConnection connection) {
            mConnection = connection;
        }

        @Override
        public void onMediaScannerConnected() {
            L.d("onMediaScannerConnected");
            if (mConnection != null && mPath != null) {
                mConnection.scanFile(mPath, null);
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            L.d("onScanCompleted " + path + " " + uri);
        }

    }
}
