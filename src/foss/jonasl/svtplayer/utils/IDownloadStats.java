package foss.jonasl.svtplayer.utils;

public interface IDownloadStats {

  public void deliverBytes(int bytes);

  public void setLength(int length);
}
