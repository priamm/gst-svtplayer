
package foss.jonasl.svtplayer;

import foss.jonasl.svtplayer.rtsp.RTSPService;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Main extends Activity {

    private Thread mThread = null;
    private BroadcastReceiver mRtspReceiver = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Native.init(this);

        /*
         * DB .instance() .pvrInsert(
         * "http://www0.c90910.dna.qbrick.com/90910/od/20110102/11141153-0102RAPPORT1930-PLAY-hts-a-v1/11141153-0102RAPPORT1930-PLAY-hts-a-v1_vod.m3u8"
         * , "rapport", new Date().getTime(), 0, 0, 0, 0); Intent i = new
         * Intent(this, PVRService.class);
         */
        // startService(i);

        mThread = new Thread() {

            @Override
            public void run() {
                /*
                 * Native.runRtspServer(Pipelines
                 * .tsFileToRtsp("/mnt/sdcard/svtplayer/696285.ts"));
                 */
            }
        };
        mThread.start();

        Button btn = (Button) findViewById(R.id.Button01);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(v.getContext(), RTSPService.class);
                startService(i);
            }
        });
        mRtspReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (RTSPService.ACTION_RTSP_READY.equals(intent.getAction())) {
                    Intent rtspIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(intent
                            .getStringExtra("url")));
                    startActivity(rtspIntent);
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(mRtspReceiver, new IntentFilter(RTSPService.ACTION_RTSP_READY));
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mRtspReceiver);
    }
}
