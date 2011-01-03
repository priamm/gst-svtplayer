
package foss.jonasl.svtplayer.utils;

public class Pipelines {

    public static String tsPartsToMp4(String input, String output) {
        // TODO: Add delete property on multifilesrc
        StringBuilder b = new StringBuilder();
        b.append("mp4mux name=muxer ! filesink location=").append(output).append(
                " multifilesrc location=").append(input).append(" ! mpegtsdemux name=demuxer")
                .append(" demuxer. ! queue ! aacparse ! aacfilter ! muxer.audio_00").append(
                        " demuxer. ! queue ! h264parse output-format=0 access-unit=true").append(
                        " ! h264filter ! muxer.video_00");
        return b.toString();
    }
}
