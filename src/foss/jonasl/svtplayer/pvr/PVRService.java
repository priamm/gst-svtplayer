package foss.jonasl.svtplayer.pvr;

import foss.jonasl.svtplayer.DB;
import foss.jonasl.svtplayer.L;

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
        L.d("processing id " + id + " pos " + cur.getPosition());
        if (!Environment.getExternalStorageState().equals(
            Environment.MEDIA_MOUNTED)) {
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
          String url = cur.getString(cur.getColumnIndexOrThrow(DB.PVR_URL))
              .toLowerCase();
          cur.deactivate();
          if (url.startsWith("http")) {
            AppleHttpRecorder.record(this, id);
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
        if (state == DB.PVR_STATE_ERROR_RETRYING
            || state >= DB.PVR_STATE_PAUSING) {
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

}
