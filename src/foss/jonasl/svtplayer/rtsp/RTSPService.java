package foss.jonasl.svtplayer.rtsp;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import foss.jonasl.svtplayer.L;
import foss.jonasl.svtplayer.Native;
import foss.jonasl.svtplayer.utils.Pipelines;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;

public class RTSPService extends Service {

  private int mServerHandle = 0;
  private int mSourceHandle = 0;
  private int mLoopHandle = 0;
  private Thread mLoopThread = null;
  private Handler mHandler = new Handler();
  private boolean mLoopStarted = false;
  private ReentrantLock mLoopStartedLock = new ReentrantLock();
  private final Condition mLoopStartedCondition = mLoopStartedLock
      .newCondition();
// TODO: Thread for watching number of sessions + shutdown
// TODO: Remove URLs
  @Override
  public void onCreate() {
    L.d("RTSPService starting");
    int[] tmp = Native.rtspServerCreate();
    mServerHandle = tmp[0];
    mSourceHandle = tmp[1];
    L.d("mServerHandle=" + mServerHandle);
    L.d("mSourceHandle=" + mSourceHandle);
    mLoopHandle = Native.mainLoopCreate();
    L.d("mLoopHandle=" + mLoopHandle);
    mLoopThread = new Thread() {

      @Override
      public void run() {
        L.d("running mainLoop " + mLoopHandle);
        mLoopStartedLock.lock();
        mLoopStarted = true;
        mLoopStartedCondition.signal();
        mLoopStartedLock.unlock();
        Native.mainLoopRun(mLoopHandle);
        L.d("running returned");
      }
    };
    mLoopStartedLock.lock();
    mLoopThread.start();
    try {
      while (!mLoopStarted) {
        mLoopStartedCondition.await();
      }
    } catch (InterruptedException e) {
      L.d("InterruptedException: " + e.getMessage());
    } finally {
      mLoopStartedLock.unlock();
    }
  }

  @Override
  public void onDestroy() {
    L.d("RTSPService destroy");

    L.d("RTSPService mainLoopFree " + mLoopHandle);
    Native.mainLoopFree(mLoopHandle);
    L.d("RTSPService rtspServerFree " + mServerHandle + "," + mSourceHandle);
    Native.rtspServerFree(mServerHandle, mSourceHandle);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    L.d("RTSPService onStartCommand");
    Native.rtspServerRegister(mServerHandle, "/test", Pipelines
        .tsFileToRtsp("/mnt/sdcard/svtplayer/696285.ts"));
    /*
    mHandler.postDelayed(new Runnable() {

      @Override
      public void run() {
        L.d("calling stopSelf");
        stopSelf();
      }
    }, 10 * 1000); */
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }

}
