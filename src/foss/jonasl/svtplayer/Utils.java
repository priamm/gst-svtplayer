
package foss.jonasl.svtplayer;

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

class Utils {
    private static DefaultHttpClient sClient = null;

    static synchronized DefaultHttpClient getHttpClient() {
        if (sClient == null) {
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setStaleCheckingEnabled(params, false);
            HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
            HttpConnectionParams.setSoTimeout(params, 20 * 1000);
            HttpConnectionParams.setSocketBufferSize(params, 8192);
            HttpClientParams.setRedirecting(params, true);
            HttpProtocolParams.setUserAgent(params, "android");
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            ClientConnectionManager manager = new ThreadSafeClientConnManager(params,
                    schemeRegistry);
            sClient = new DefaultHttpClient(manager, params);
        }
        return sClient;
    }
}
