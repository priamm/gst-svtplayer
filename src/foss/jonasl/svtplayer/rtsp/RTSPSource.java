
package foss.jonasl.svtplayer.rtsp;

import java.nio.ByteBuffer;

public interface RTSPSource {
    public void seek(long position);

    public int getData(ByteBuffer buffer);
}
