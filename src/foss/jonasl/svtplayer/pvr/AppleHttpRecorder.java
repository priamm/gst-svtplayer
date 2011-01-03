package foss.jonasl.svtplayer.pvr;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.client.ClientProtocolException;

import android.content.Context;
import android.database.Cursor;
import foss.jonasl.svtplayer.DB;
import foss.jonasl.svtplayer.L;
import foss.jonasl.svtplayer.M3U8;
import foss.jonasl.svtplayer.Native;
import foss.jonasl.svtplayer.M3U8.M3U8Entry;
import foss.jonasl.svtplayer.utils.Http;
import foss.jonasl.svtplayer.utils.Pipelines;
import foss.jonasl.svtplayer.utils.Utils;

public class AppleHttpRecorder {
  public static void record(Context context, long id) {
    if (!changeCheckState(id, DB.PVR_STATE_RECORDING)) {
      return;
    }
    DB db = DB.instance();
    Cursor cur = db.pvrGetEntry(id);
    cur.moveToFirst();
    String urlString = cur.getString(cur.getColumnIndexOrThrow(DB.PVR_URL));
    String title = cur.getString(cur.getColumnIndexOrThrow(DB.PVR_TITLE));
    Date date = new Date(cur.getLong(cur.getColumnIndexOrThrow(DB.PVR_DATE)));
    cur.close();

    URI uri;
    try {
      uri = new URI(urlString);
    } catch (URISyntaxException e) {
      L.d("invalid recording url");
      changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
      return;
    }
    File ownDir = Utils.getOwnDir();
    File workDir = Utils.getPVRWorkDir(id);
    workDir.mkdirs();
    if (!workDir.exists()) {
      L.d("can't create workDir");
      changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
      return;
    }

    boolean again = false;
    M3U8 playlist = new M3U8(uri);
    do {
      if (!changeCheckState(id, DB.PVR_STATE_RECORDING)) {
        return;
      }
      if (playlist.load() == M3U8.LOAD_FAILED_IO) {
        L.d("playlist load io error, trying again");
        if (!changeCheckState(id, DB.PVR_STATE_ERROR_RETRYING)) {
          return;
        }
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
      changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
      return;
    }
    // TODO: Read preferred bandwidth from config/db
    M3U8 toUse = playlist;
    if (playlist.getVariants() != null) {
      for (M3U8 variant : playlist.getVariants()) {
        if (variant.getBandwidth() > toUse.getBandwidth()) {
          toUse = variant;
        }
      }
      L.d("loading variant playlist " + toUse.getBandwidth() + " "
          + toUse.getUri());
      do {
        if (!changeCheckState(id, DB.PVR_STATE_RECORDING)) {
          return;
        }
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
        changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
        return;
      }
    }
    if (toUse.getEntries() == null || toUse.getEntries().size() == 0) {
      L.d("variant playlist has no entries");
      changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
      return;
    }
    if (!toUse.hasEnd()) {
      L.d("playlist has no end tag");
      changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
      return;
    }

    if (getJoinedTSFile(workDir, toUse).exists()) {
      remux(context, id, title, date, ownDir, workDir, toUse);
      return;
    }

    L.d("entries: " + toUse.getEntries().size() + " duration: "
        + toUse.getDuration());
    StatKeeper stat = new StatKeeper(id, 5, toUse.getDuration(), -1);
    stat.start();
    for (int i = 0; i < toUse.getEntries().size(); i++) {
      do {
        if (!changeCheckState(id, DB.PVR_STATE_RECORDING)) {
          return;
        }
        again = false;
        M3U8Entry entry = toUse.getEntries().get(i);
        File target = new File(workDir, String.format("%d_%07d.ts", toUse
            .getBandwidth(), i));
        L.d("DL: " + entry.getUri() + " -> " + target);
        try {
          if (!Http.downloadFile(entry.getUri(), target, stat)) {
            L.d("aborting, local write failed");
            changeCheckState(id, DB.PVR_STATE_ERROR_SPACE);
            return;
          }
          stat.deliverSecs(entry.getDuration());
        } catch (ClientProtocolException e) {
          L.d("ClientProtocolException:" + e.getMessage());
          changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
          return;
        } catch (IOException e) {
          L.d("IOException:" + e.getMessage());
          again = true;
        } catch (Exception e) {
          L.d("Exception:" + e.getMessage());
          changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
          return;
        }
        if (again) {
          if (!changeCheckState(id, DB.PVR_STATE_ERROR_RETRYING)) {
            return;
          }
          try {
            Thread.sleep(10 * 1000);
          } catch (InterruptedException ie) {
            L.d("thread interrupted");
            changeCheckState(id, DB.PVR_STATE_ERROR_RETRYING);
            return;
          }
        }
      } while (again);
    }
    stat.stop();

    remux(context, id, title, date, ownDir, workDir, toUse);
  }

  private static File getJoinedTSFile(File workDir, M3U8 toUse) {
    return new File(workDir, String.valueOf(toUse.getBandwidth()) + ".ts");
  }

  private static void remux(Context context, long id, String title, Date date,
      File ownDir, File workDir, M3U8 toUse) {
    if (!changeCheckState(id, DB.PVR_STATE_REMUXING)) {
      return;
    }

    File inputFile = getJoinedTSFile(workDir, toUse);
    if (!inputFile.exists()) {
      BufferedOutputStream joinedFileStream = null;
      BufferedInputStream partFileStream = null;
      try {
        int idx = 0;
        joinedFileStream = new BufferedOutputStream(new FileOutputStream(
            inputFile), 8192);
        File part = new File(workDir, String.valueOf(toUse.getBandwidth())
            + String.format("_%07d.ts", idx));
        if (!part.exists()) {
          L.d("first part not found");
          changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
          return;
        }
        byte[] buf = new byte[8192];
        int read;
        do {
          L.d("joining " + part.getAbsolutePath());
          partFileStream = new BufferedInputStream(new FileInputStream(part));
          while ((read = partFileStream.read(buf)) != -1) {
            joinedFileStream.write(buf, 0, read);
          }
          partFileStream.close();
          part.delete();

          idx++;
          part = new File(workDir, String.valueOf(toUse.getBandwidth())
              + String.format("_%07d.ts", idx));
        } while (part.exists());
      } catch (Exception e) {
        L.d(e.toString());
        changeCheckState(id, DB.PVR_STATE_ERROR_SPACE);
        return;
      } finally {
        if (joinedFileStream != null) {
          try {
            joinedFileStream.flush();
            joinedFileStream.close();
          } catch (Exception e) {
            L.d("couldn't flush and close " + inputFile + " : "
                + e.getMessage());
            changeCheckState(id, DB.PVR_STATE_ERROR_SPACE);
            return;
          }
        }
      }
    }

    String outputBase = title + "_"
        + new SimpleDateFormat("yyyyMMdd").format(date) + "_"
        + new SimpleDateFormat("HHmm").format(date);
    outputBase = outputBase.replace(" ", "_");
    StringBuilder outputBuilder = new StringBuilder();
    for (char c : outputBase.toCharArray()) {
      if (c == '_' || Character.isLetterOrDigit(c)) {
        outputBuilder.append(c);
      }
    }
    outputBase = outputBuilder.toString();
    File outputFile = new File(ownDir, outputBase + ".mp4");
    try {
      if (outputFile.exists()) {
        int cnt = 2;
        do {
          outputFile = new File(ownDir, outputBase + "-" + cnt + ".mp4");
          cnt++;
        } while (outputFile.exists());
      }
    } catch (Exception e) {
      L.d(e.toString());
      changeCheckState(id, DB.PVR_STATE_ERROR_SPACE);
      return;
    }
    int bytesFree = Utils.getExternalFreeBytes();
    int bytesNeeded = (int) (((float) inputFile.length()) * 1.25);
    L.d(String.format("need: %d, free: %d", bytesNeeded, bytesFree));
    if (bytesFree < bytesNeeded) {
      L.d("not enough space");
      changeCheckState(id, DB.PVR_STATE_ERROR_SPACE);
      return;
    }
    String pipeline = Pipelines.tsPartsToMp4(inputFile.getAbsolutePath(),
        outputFile.getAbsolutePath());
    if (!Native.runPipeline(pipeline)) {
      L.d("failed to run pipeline");
      changeCheckState(id, DB.PVR_STATE_ERROR_FATAL);
      try {
        outputFile.delete();
      } catch (Exception e) {
        L.d(e.toString());
      }
      return;
    }
    Utils.invokeMediaScanner(context, outputFile);
    Utils.deleteDir(workDir);
    DB.instance().pvrDelete(id);
  }

  private static boolean changeCheckState(long id, int newState) {
    DB db = DB.instance();
    boolean res = false;
    Cursor cur = null;
    try {
      cur = DB.instance().pvrGetEntry(id);
      if (cur.moveToFirst()) {
        int oldState = cur.getInt(cur.getColumnIndexOrThrow(DB.PVR_STATE));
        if (oldState == DB.PVR_STATE_PAUSING) {
          L.d(id + " is marked PAUSING, setting to PAUSED");
          newState = DB.PVR_STATE_PAUSED;
          res = false;
        } else if (oldState == DB.PVR_STATE_DELETED) {
          L.d(id + " is marked DELETED");
          Utils.deleteDir(Utils.getPVRWorkDir(id));
          db.pvrDelete(id);
          res = false;
        } else {
          res = true;
        }
        if (newState != oldState) {
          db.pvrUpdateState(id, newState);
        }
      } else {
        L.d(id + " doesn't exist anymore, cleaning up");
        Utils.deleteDir(Utils.getPVRWorkDir(id));
        res = false;
      }
    } catch (Exception e) {
      L.d(e.toString());
      res = false;
    } finally {
      if (cur != null) {
        cur.close();
      }
    }
    return res;
  }
}
