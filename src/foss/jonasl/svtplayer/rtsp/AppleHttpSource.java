
package foss.jonasl.svtplayer.rtsp;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import foss.jonasl.svtplayer.L;

public class AppleHttpSource implements RTSPSource {
    FileInputStream mFS;
    BufferedInputStream mBS;
    private byte[] mBuf;

    public AppleHttpSource() {
        try {
            mFS = new FileInputStream("/mnt/sdcard/svtplayer/696285.ts");
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mBS = new BufferedInputStream(mFS);
        mBuf = null;
    }

    @Override
    public int getData(ByteBuffer buffer) {
        if (mBuf == null || mBuf.length != buffer.capacity()) {
            mBuf = new byte[buffer.capacity()];
        }

        int res;
        try {
            res = mBS.read(mBuf);
        } catch (IOException e) {
            e.printStackTrace();
            L.d(e.toString() + e.getMessage());
            res = -1;
        }
        if (res == 0) {
            L.d("read 0 bytes");
            res = -1;
        } else if (res > 0) {
            buffer.clear();
            buffer.put(mBuf, 0, res);
        }
        return res;
    }

    @Override
    public void seek(long position) {
        // TODO Auto-generated method stub

    }

}
