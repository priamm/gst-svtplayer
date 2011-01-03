
package foss.jonasl.svtplayer;

import android.app.Application;

public class SVTPlayerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DB.init(getApplicationContext());
    }
}
