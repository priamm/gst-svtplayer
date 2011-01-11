package foss.jonasl.svtplayer.utils;

import foss.jonasl.svtplayer.L;

public class Pipelines {

  public static String tsPartsToMp4(String input, String output) {
    StringBuilder b = new StringBuilder();
    b.append("mp4mux name=muxer ! filesink location=").append(output).append(
        " filesrc location=").append(input).append(
        " ! mpegtsdemux name=demuxer").append(
        " demuxer. ! queue ! aacparse ! aacfilter ! muxer.audio_00").append(
        " demuxer. ! queue ! h264parse output-format=0 access-unit=true")
        .append(" ! h264filter ! muxer.video_00");
    return b.toString();
  }

  public static String tsFileToRtsp(String input) {
    StringBuilder b = new StringBuilder();
    b.append("( filesrc location=");
    b.append(input);
    b.append(" ! mpegtsdemux name=d");
    b.append(" d. ! queue ! h264parse output-format=0").append(
        " ! h264filter ! rtph264pay name=pay0 pt=96").append(
        " d. ! queue ! aacparse ! aacfilter !").append(
        " rtpmp4apay name=pay1 pt=97 )");
    L.d(b.toString());
    return b.toString();
  }

  public static String appleToRtsp(int duration, int id) {
      StringBuilder b = new StringBuilder();
      b.append("( svtpsrc duration=");
      b.append(duration);
      b.append(" id=");
      b.append(id);
      b.append(" ! mpegtsdemux name=d");
      b.append(" d. ! queue ! h264parse output-format=0").append(
          " ! h264filter ! rtph264pay name=pay0 pt=96").append(
          " d. ! queue ! aacparse ! aacfilter !").append(
          " rtpmp4apay name=pay1 pt=97 )");
      L.d(b.toString());
      return b.toString();
    }
}
