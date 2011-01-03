
package foss.jonasl.svtplayer.pvr;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;

import foss.jonasl.svtplayer.DB;
import foss.jonasl.svtplayer.L;
import foss.jonasl.svtplayer.M3U8;
import foss.jonasl.svtplayer.Native;
import foss.jonasl.svtplayer.M3U8.M3U8Entry;
import foss.jonasl.svtplayer.utils.Http;
import foss.jonasl.svtplayer.utils.Pipelines;
import foss.jonasl.svtplayer.utils.Utils;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;

public class PVRService extends IntentService {

    public PVRService() {
        super("PVRService");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Cursor cur = DB.instance().pvrGetEntries();
        if (cur == null) {
            L.d("entries cursor is null");
            return;
        }

        try {
            long id;
            while ((id = getIdToProcess(cur)) > 0) {
                L.d ("processing id " + id + " pos " + cur.getPosition());
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    L.d("no external storage available");
                    return;
                }
                NetworkInfo currentNetwork = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))
                        .getActiveNetworkInfo();
                if (currentNetwork == null || !currentNetwork.isConnectedOrConnecting()) {
                    L.d("no network");
                    return;
                }
                try {
                    String url = cur.getString(cur.getColumnIndexOrThrow(DB.PVR_URL)).toLowerCase();
                    cur.deactivate();
                    if (url.startsWith("http")) {
                        recordAppleHttp(id);
                    } else if (url.startsWith("rtmp")) {
                        recordRtmp(id);
                    } else {
                        L.d("can't handle " + url);
                        DB.instance().pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
                    }
                } catch (Exception e) {
                    L.d(e.toString());
                    DB.instance().pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
                } finally {
                    cur.requery();
                }
            }
        } catch (Exception e) {
            L.d(e.toString());
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    }

    private int getIdToProcess(Cursor cur) {
        int ret = -1;
        if (cur != null && cur.moveToFirst()) {
            do {
                int state = cur.getInt(cur.getColumnIndexOrThrow(DB.PVR_STATE));
                if (state == DB.PVR_STATE_ERROR_RETRYING || state == DB.PVR_STATE_PENDING) {
                    ret = cur.getInt(cur.getColumnIndexOrThrow(DB.PVR_ID));
                    break;
                }
            } while (cur.moveToNext() && cur.getPosition() != cur.getCount());
        }
        return ret;
    }

    private void recordRtmp(long id) throws Exception {
        throw new Exception("not implemented yet, sorry");
    }

    private void recordAppleHttp(long id) {
        // TODO: Handle pause and cancel
        DB db = DB.instance();
        db.pvrUpdateState(id, DB.PVR_STATE_RECORDING);
        Cursor cur = db.pvrGetEntry(id);
        cur.moveToFirst();
        String urlString = cur.getString(cur.getColumnIndexOrThrow(DB.PVR_URL));
        cur.close();

        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            L.d("invalid recording url");
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
            return;
        }
        File ownDir = Utils.getOwnDir();
        File workDir = Utils.getPVRWorkDir(id);
        workDir.mkdirs();
        if (!workDir.exists()) {
            L.d("can't create workDir");
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
            return;
        }

        boolean again = false;
        M3U8 playlist = new M3U8(uri);
        do {
            db.pvrUpdateState(id, DB.PVR_STATE_RECORDING);
            if (playlist.load() == M3U8.LOAD_FAILED_IO) {
                L.d("playlist load io error, trying again");
                db.pvrUpdateState(id, DB.PVR_STATE_ERROR_RETRYING);
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
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
            return;
        }
        // TODO: Read preferred bandwitdth from config/db
        M3U8 toUse = playlist;
        if (playlist.getVariants() != null) {
            for (M3U8 variant : playlist.getVariants()) {
                if (variant.getBandwidth() > toUse.getBandwidth()) {
                    toUse = variant;
                }
            }
            L.d("loading variant playlist " + toUse.getBandwidth() + " " + toUse.getUri());
            do {
                db.pvrUpdateState(id, DB.PVR_STATE_RECORDING);
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
                db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
                return;
            }
        }
        if (toUse.getEntries() == null || toUse.getEntries().size() == 0) {
            L.d("variant playlist has no entries");
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
            return;
        }
        if (!toUse.hasEnd()) {
            L.d("playlist has no end tag");
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
            return;
        }
        L.d("entries: " + toUse.getEntries().size() + " duration: " + toUse.getDuration());
        StatKeeper stat = new StatKeeper(id, 5, toUse.getDuration(), -1);
        stat.start();
        for (int i = 0; i < toUse.getEntries().size(); i++) {
            do {
                db.pvrUpdateState(id, DB.PVR_STATE_RECORDING);
                again = false;
                M3U8Entry entry = toUse.getEntries().get(i);
                File target = new File(workDir, String
                        .format("%d_%07d.ts", toUse.getBandwidth(), i));
                L.d("DL: " + entry.getUri() + " -> " + target);
                try {
                    if (!Http.downloadFile(entry.getUri(), target, stat)) {
                        L.d("aborting, local write failed");
                        db.pvrUpdateState(id, DB.PVR_STATE_ERROR_SPACE);
                        return;
                    }
                    stat.deliverSecs(entry.getDuration());
                } catch (ClientProtocolException e) {
                    L.d("ClientProtocolException:" + e.getMessage());
                    db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
                    return;
                } catch (IOException e) {
                    L.d("IOException:" + e.getMessage());
                    db.pvrUpdateState(id, DB.PVR_STATE_ERROR_RETRYING);
                    again = true;
                } catch (Exception e) {
                    L.d("Exception:" + e.getMessage());
                    db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
                    return;
                }
                if (again) {
                    try {
                        Thread.sleep(10 * 1000);
                    } catch (InterruptedException ie) {
                        L.d("thread interrupted");
                        db.pvrUpdateState(id, DB.PVR_STATE_ERROR_RETRYING);
                        return;
                    }
                }
            } while (again);
        }
        stat.stop();

        db.pvrUpdateState(id, DB.PVR_STATE_REMUXING);

        String input = new File(workDir, String.valueOf(toUse.getBandwidth()) + "_%07d.ts")
                .getAbsolutePath();
        File outputFile = new File(ownDir, id + ".mp4");

        try {
            if (outputFile.exists()) {
                int cnt = 2;
                do {
                    outputFile = new File(ownDir, id + "-" + cnt + ".mp4");
                    cnt++;
                } while (outputFile.exists());
            }
        } catch (Exception e) {
            L.d(e.toString());
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_SPACE);
        }
        int bytesFree = Utils.getExternalFreeBytes();
        int bytesNeeded = (int) (((float) Utils.getDirSize(workDir)) * 1.25);
        if (bytesFree < bytesNeeded) {
            L.d("not enough space");
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_SPACE);
            return;
        }
        String pipeline = Pipelines.tsPartsToMp4(input, outputFile.getAbsolutePath());
        if (!Native.runPipeline(pipeline)) {
            L.d("failed to run pipeline");
            db.pvrUpdateState(id, DB.PVR_STATE_ERROR_FATAL);
            return;
        }
        Utils.invokeMediaScanner (this, outputFile);
        Utils.deleteDir(workDir);
        db.pvrDelete(id);
    }
}
