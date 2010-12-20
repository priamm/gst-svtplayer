
package foss.jonasl.svtplayer;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class Main extends Activity {

    static final String TAG = "svtplayer";

    static {
        Log.d(TAG, "glib-2.0");
        System.loadLibrary("glib-2.0");
        Log.d(TAG, "gmodule-2.0");
        System.loadLibrary("gmodule-2.0");
        Log.d(TAG, "gobject-2.0");
        System.loadLibrary("gobject-2.0");
        Log.d(TAG, "gthread-2.0");
        System.loadLibrary("gthread-2.0");
        Log.d(TAG, "jni");
        System.loadLibrary("svtplayer");
        Log.d(TAG, "libraries loaded");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
