
package foss.jonasl.svtplayer.rtsp;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import foss.jonasl.svtplayer.L;
import foss.jonasl.svtplayer.Native;
import foss.jonasl.svtplayer.utils.Pipelines;
import foss.jonasl.svtplayer.utils.Utils;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class RTSPService extends Service {
    public final static String ACTION_RTSP_READY = "foss.jonasl.svtplayer.rtsp.READY";

    private final static int CLEANUP_INTERVAL = 60 * 1000;

    @SuppressWarnings("unused")
    private static RTSPService sInstance = null;

    private int mServerHandle = 0;
    private int mSourceHandle = 0;
    private int mMainLoopHandle = 0;
    private Thread mMainLoopThread = null;
    private Thread mCleanupThread = null;
    private Handler mCleanupHandler;
    private boolean mLoopStarted = false;
    private ReentrantLock mLoopStartedLock = new ReentrantLock();
    private Condition mLoopStartedCondition = mLoopStartedLock.newCondition();
    private String mPath = null;
    private int mId = 0;
    private RTSPSource mSource = null;

    @Override
    public void onCreate() {
        sInstance = this;
        L.d("RTSPService starting");
        //Native.setLoglevel("mpegtsdemux", Native.GST_LEVEL_DEBUG);
        Native.setLoglevel("svtpsrc", Native.GST_LEVEL_INFO);
        Native.setLoglevel("rtspmedia", Native.GST_LEVEL_INFO);
        int[] tmp = Native.rtspServerCreate();
        mServerHandle = tmp[0];
        mSourceHandle = tmp[1];
        L.d("mServerHandle=" + mServerHandle);
        L.d("mSourceHandle=" + mSourceHandle);
        mMainLoopHandle = Native.mainLoopCreate();
        L.d("mLoopHandle=" + mMainLoopHandle);
        mMainLoopThread = new Thread() {
            @Override
            public void run() {
                L.d("running mainLoop " + mMainLoopHandle);
                mLoopStartedLock.lock();
                mLoopStarted = true;
                mLoopStartedCondition.signal();
                mLoopStartedLock.unlock();
                Native.mainLoopRun(mMainLoopHandle);
                L.d("running returned");
            }
        };
        mLoopStartedLock.lock();
        mMainLoopThread.start();
        try {
            while (!mLoopStarted) {
                mLoopStartedCondition.await();
            }
        } catch (InterruptedException e) {
            L.d("InterruptedException: " + e.getMessage());
        } finally {
            mLoopStartedLock.unlock();
        }
        mCleanupThread = new Thread() {
            @Override
            public void run() {
                L.d("running cleanup thread");
                Looper.prepare();
                mCleanupHandler = new Handler();
                mCleanupHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        int activeSessions = Native.rtspCleanup(mServerHandle);
                        L.d("RTSP server has " + activeSessions + " active sessions");
                        if (activeSessions > 0) {
                            mCleanupHandler.postDelayed(this, CLEANUP_INTERVAL);
                        } else {
                            L.d("Shutting down cleanup Looper");
                            Looper.myLooper().quit();
                            L.d("Calling stopSelf()");
                            stopSelf();
                        }
                    }
                }, CLEANUP_INTERVAL);
                Looper.loop();
                L.d("cleanup thread exiting");
            }
        };
        mCleanupThread.start();
    }

    @Override
    public void onDestroy() {
        L.d("RTSPService destroy");
        sInstance = null;

        L.d("RTSPService mainLoopFree " + mMainLoopHandle);
        Native.mainLoopFree(mMainLoopHandle);
        L.d("RTSPService rtspServerFree " + mServerHandle + "," + mSourceHandle);
        Native.rtspServerFree(mServerHandle, mSourceHandle);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        L.d("RTSPService onStartCommand");
        if (mPath != null) {
            L.d("removing " + mPath);
            Native.rtspServerRemove(mServerHandle, mPath);
        }
        mPath = "/" + Utils.getRandomHexString(10);
        mId++;
        L.d("registering " + mPath);
        Native.rtspServerRegister(mServerHandle, mPath, Pipelines.appleToRtsp(29 * 60, 6));
        L.d("broadcasting");
        broadcastReady();
        mSource = new AppleHttpSource();
        mCleanupHandler.post(new Runnable() {
            @Override
            public void run() {
                // Native.runPipeline("svtpsrc duration=1 id=5 ! fakesink");
            }
        });
        return START_STICKY;
    }

    private void broadcastReady() {
        Intent i = new Intent(RTSPService.ACTION_RTSP_READY);
        i.putExtra("url", "rtsp://127.0.0.1:8554" + mPath);
        L.d("broadcasting ready url: " + i.getStringExtra("url"));
        sendBroadcast(i);
    }

    @SuppressWarnings("unused")
    private void seek(int id, long position) {
        try {
            L.d(id + " seek " + position);
            mSource.seek(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    private int getData(int id, ByteBuffer buffer) {
        try {
            return mSource.getData(buffer);
        } catch (Exception e) {
            e.printStackTrace();
            L.d(e.toString() + e.getMessage());
        }
        return -1;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

}
