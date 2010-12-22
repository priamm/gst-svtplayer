
package foss.jonasl.svtplayer;

import java.io.File;

import android.content.Context;
import android.util.Log;

public class Native {

    private static boolean sInitialized = false;

    private static String[] sLibs = {
            "glib-2.0", "gmodule-2.0", "gthread-2.0", "gobject-2.0", "gstreamer-0.10",
            "gstbase-0.10", "gstcontroller-0.10", "gstdataprotocol-0.10", "gstnet-0.10",
            "gstcoreelements", "svtplayer"
    };

    static synchronized void init(Context context) {
        if (!sInitialized) {
            for (String lib : sLibs) {
                Log.d(Main.TAG, "Loading " + lib);
                System.loadLibrary(lib);
            }
            String dataPath = new File(context.getFilesDir().getParentFile(), "lib")
                    .getAbsolutePath();
            Log.d(Main.TAG, "calling init_native with path: " + dataPath);
            init_native(dataPath);
            sInitialized = true;
        }
    }

    static native void init_native(String dataPath);
    static native void test();
}
