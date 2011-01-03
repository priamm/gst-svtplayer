package foss.jonasl.svtplayer.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import foss.jonasl.svtplayer.L;

public class Http {
  private static DefaultHttpClient sClient = null;

  public static synchronized DefaultHttpClient getClient() {
    if (sClient == null) {
      HttpParams params = new BasicHttpParams();
      HttpConnectionParams.setStaleCheckingEnabled(params, false);
      HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
      HttpConnectionParams.setSoTimeout(params, 20 * 1000);
      HttpConnectionParams.setSocketBufferSize(params, 8192);
      HttpClientParams.setRedirecting(params, true);
      HttpProtocolParams.setUserAgent(params, "android");
      SchemeRegistry schemeRegistry = new SchemeRegistry();
      schemeRegistry.register(new Scheme("http", PlainSocketFactory
          .getSocketFactory(), 80));
      ClientConnectionManager manager = new ThreadSafeClientConnManager(params,
          schemeRegistry);
      sClient = new DefaultHttpClient(manager, params);
    }
    return sClient;
  }

  public static boolean downloadFile(URI uri, File target, IDownloadStats stat)
      throws ClientProtocolException, IOException {
    BufferedOutputStream outStream = null;
    try {
      boolean append = false;
      long length = -1;
      try {
        append = target.exists();
        length = target.length();
      } catch (Exception e) {
        L.d("could not access " + target + " : " + e.getMessage());
        return false;
      }

      HttpGet request = new HttpGet();
      HttpResponse response;
      if (append) {
        HttpHead head = new HttpHead();
        head.setURI(uri);
        response = Http.getClient().execute(head);
        if (response.containsHeader("Content-Length")) {
          int contentLenght = Integer.valueOf(response.getFirstHeader(
              "Content-Length").getValue());
          if (length < contentLenght) {
            request.addHeader("Range", "bytes=" + length + "-"
                + String.valueOf(contentLenght - 1));
          } else if (length == contentLenght) {
            L.d("size matches, skipping");
            return true;
          } else {
            L.d("local file too big, downloading again");
            append = false;
          }
        } else {
          L.d("HEAD has no Content-Length");
          append = false;
        }
      }
      request.setURI(uri);
      response = Http.getClient().execute(request);
      // TODO: Determine total length and position and pass to stat
      int totalLength = -1;
      if (append) {
        if (response.getStatusLine().getStatusCode() == 206) {
          if (response.containsHeader("Content-Range")) {
            String val = response.getFirstHeader("Content-Range").getValue();
            val = val.substring(val.lastIndexOf("/") + 1);
            try {
              totalLength = Integer.valueOf(val);
              if (stat != null) {
                stat.setLength(totalLength);
              }
            } catch (Exception e) {
              L.d("invalid Content-Range: "
                  + response.getFirstHeader("Content-Range").getValue());
            }
          }

        } else {
          L.d("expected partial content 206 but got "
              + response.getStatusLine().getStatusCode()
              + ", deleting file and throw");
          try {
            target.delete();
          } catch (Exception e) {
            L.d("could not access " + target + " : " + e.getMessage());
            return false;
          }
          throw new IOException("expected partial content 206 but got "
              + response.getStatusLine().getStatusCode());
        }
      }

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        L.d("response entity is null, will throw");
      }
      InputStream inStream = entity.getContent();
      if (inStream == null) {
        L.d("response stream is null, will throw");
      }
      long contentLength = entity.getContentLength();
      if (contentLength > 0) {
        if (totalLength < 0) {
          totalLength = (int) contentLength;
          if (stat != null) {
            stat.setLength(totalLength);
          }
        }
        if (Utils.getExternalFreeBytes() < contentLength) {
          L.d("not enough space");
          return false;
        }
      }
      try {
        outStream = new BufferedOutputStream(new FileOutputStream(target,
            append), 8192);
      } catch (Exception e) {
        L.d("could not access " + target + " : " + e.getMessage());
        return false;
      }
      byte[] buf = new byte[8192];
      int read = 0;
      while ((read = inStream.read(buf)) != -1) {
        try {
          outStream.write(buf, 0, read);
          if (stat != null) {
            stat.deliverBytes(read);
          }
        } catch (Exception e) {
          L.d("could not write to " + target + " : " + e.getMessage());
          return false;
        }
      }
      try {
        outStream.flush();
        outStream.close();
        outStream = null;
      } catch (Exception e) {
        L.d("could not flush and close " + target + " : " + e.getMessage());
        return false;
      }
      if (totalLength > 0 && target.length() != totalLength) {
        throw new IOException("entire file not saved to disk");
      }
      return true;
    } finally {
      if (outStream != null) {
        try {
          outStream.flush();
          outStream.close();
        } catch (Exception e) {
          L.d("could not flush and close " + target + " : " + e.getMessage());
          return false;
        }
      }
    }
  }
}
