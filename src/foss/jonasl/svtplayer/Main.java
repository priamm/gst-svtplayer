
package foss.jonasl.svtplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class Main extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Native.init(this);
        Intent i = new Intent(this, RecordingService.class);
        i.putExtra("url", "http://www0.c90910.dna.qbrick.com/90910/od/20101223/KULTUR19-PLAY-hts-a-v1/KULTUR19-PLAY-hts-a-v1_vod.m3u8");
        i.putExtra("id", 1);
        startService(i);
    }
}
