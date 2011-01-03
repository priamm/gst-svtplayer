
package foss.jonasl.svtplayer.pvr;

import java.util.Timer;
import java.util.TimerTask;

import android.os.SystemClock;
import foss.jonasl.svtplayer.DB;
import foss.jonasl.svtplayer.utils.IDownloadStats;

public class StatKeeper implements IDownloadStats {
    private DB mDb;
    private Timer mTimer;
    private long mId;
    private int mInterval;
    private int mDuration;
    private long mLength;

    private long mBytes;
    private long mAccBytes;
    private int mSecs;
    private int mAccSecs;
    private long mStartTime;

    public StatKeeper(long id, int interval, int duration, long length) {
        mId = id;
        mInterval = interval;
        mDuration = duration;
        mLength = length;
        mDb = DB.instance();
    }

    private synchronized void reset() {
        mBytes = 0;
        mAccBytes = 0;
        mSecs = 0;
        mAccSecs = 0;
    }

    public synchronized void start() {
        reset();
        if (mTimer == null) {
            mTimer = new Timer();
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    tick(true);
                }
            }, mInterval * 1000, mInterval * 1000);
            mStartTime = SystemClock.elapsedRealtime();
        }
    }

    public synchronized void stop() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        tick(false);
    }

    public void deliverBytes(int bytes) {
        mBytes += bytes;
        mAccBytes += bytes;
    }

    public void deliverSecs(int secs) {
        mSecs += secs;
        mAccSecs += secs;
    }

    private synchronized void tick(boolean fromTimer) {
        int bytesPerSecond;
        if (fromTimer) {
            bytesPerSecond = (int) (mBytes / mInterval);
        } else {
            int period = (int) ((SystemClock.elapsedRealtime() - mStartTime) / 1000);
            if (period == 0) {
                period = 1;
            }
            bytesPerSecond = (int) (mAccBytes / period);
        }
        int kiloBytesPerSecond = bytesPerSecond / 1024;
        int progress = -1;
        if (mLength > 0) {
            progress = (int) (100 * mAccBytes / mLength);
        } else if (mDuration > 0) {
            progress = (int) (100 * mAccSecs / mDuration);
        }

        mDb.pvrUpdateRateProgress(mId, kiloBytesPerSecond, progress);
        mBytes = 0;
    }
}
