package foss.jonasl.svtplayer;

import java.io.File;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

public class Native {

  public static final int GST_LEVEL_NONE = 0;
  public static final int GST_LEVEL_ERROR = 1;
  public static final int GST_LEVEL_WARNING = 2;
  public static final int GST_LEVEL_INFO = 3;
  public static final int GST_LEVEL_DEBUG = 4;
  public static final int GST_LEVEL_LOG = 5;

  private static boolean sInitialized = false;

  private static String[] sLibs = { "glib-2.0", "gmodule-2.0", "gthread-2.0",
      "gobject-2.0", "gstreamer-0.10", "gstbase-0.10", "gstcontroller-0.10",
      "gstdataprotocol-0.10", "gstnet-0.10", "gstnetbuffer-0.10", "gsttag-0.10",
      "gstapp-0.10", "gstaudio-0.10", "gstrtp-0.10", "gstrtsp-0.10",
      "gstsdp-0.10", "gstrtspserver-0.10", "svtplayer" };

  private static String[] sPlugins = { "libgstcoreelements.so",
      "libgstaudioparsersbad.so", "libgsth264parse.so", "libgstmpegdemux.so",
      "libgstqtmux.so", "libgstrtp.so", "libgstrtpmanager.so", "libgstsvthelper.so" };
 
  static synchronized void init(Context context) {
    if (sInitialized) {
      return;
    }
    for (String lib : sLibs) {
      System.loadLibrary(lib);
    }
    initNative(GST_LEVEL_INFO);


    String dataPath;
    try {
      dataPath = context.getPackageManager().getApplicationInfo(
          context.getPackageName(), 0).dataDir;
    } catch (NameNotFoundException e) {
      dataPath = context.getFilesDir().getParentFile().getAbsolutePath();
    }
    File libDir = new File(dataPath, "lib");
    for (String plugin : sPlugins) {
      if (!loadPlugin(new File(libDir, plugin).getAbsolutePath())) {
        L.d("failed: " + plugin);
      }
    }

    sInitialized = true;
  }

  static native void initNative(int logLevel);

  private static native boolean loadPlugin(String path);

  public static native boolean runPipeline(String pipeline);
  public static native void runRtspServer(String pipeline);
}
