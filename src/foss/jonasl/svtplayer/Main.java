package foss.jonasl.svtplayer;

import java.util.Date;

import foss.jonasl.svtplayer.pvr.PVRService;
import foss.jonasl.svtplayer.rtsp.RTSPService;
import foss.jonasl.svtplayer.utils.Pipelines;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Main extends Activity {

  private Thread mThread = null;
  private Handler mHandler = new Handler();

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
        /*Native.runRtspServer(Pipelines
            .tsFileToRtsp("/mnt/sdcard/svtplayer/696285.ts"));*/
      }
    };
    mThread.start();

    
    Button btn = (Button) findViewById(R.id.Button01);
    btn.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(View v) {
        
        Intent i = new Intent(v.getContext(), RTSPService.class);
        startService(i);
        mHandler.postDelayed(new Runnable() {

          @Override
          public void run() {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("rtsp://127.0.0.1:8554/test"));
            startActivity(i);
          }
        }, 10 * 1000);
      }
    });

  };
}
