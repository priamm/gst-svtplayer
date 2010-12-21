
package foss.jonasl.svtplayer;

import android.app.Activity;
import android.os.Bundle;

public class Main extends Activity {

    static final String TAG = "svtplayer";

   
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Native.init();
        Native.test();
    }
}
