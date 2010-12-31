
package foss.jonasl.svtplayer;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;

import android.text.TextUtils;

public class M3U8 {
    public final static int LOAD_OK = 0;
    public final static int LOAD_FAILED_IO = -1;
    public final static int LOAD_FAILED_OTHER = -2;

    private final static String COMMENT_PREFIX = "#";
    private final static String EX_PREFIX = "#EXT";
    private final static String EXTM3U = "#EXTM3U";
    private final static String EXTINF = "#EXTINF";
    private final static String EXT_X_TARGET_DURATION = "#EXT-X-TARGETDURATION";
    private final static String EXT_X_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE";
    private final static String EXT_X_STREAM_INF = "#EXT-X-STREAM-INF";
    private final static String EXT_X_ENDLIST = "#EXT-X-ENDLIST";

    private URI mUri = null;
    private int mBandwidth = -1;
    private boolean mHasEnd = false;
    private int mTargetDuration = -1;
    private int mMediaSequenceNumber = -1;
    private boolean mLoaded = false;
    private List<M3U8> mVariants = null;
    private List<M3U8Entry> mEntries = null;

    public M3U8(URI uri) {
        mUri = uri;
    }

    private void reset() {
        mHasEnd = false;
        mTargetDuration = -1;
        mMediaSequenceNumber = -1;
        mVariants = null;
        mEntries = null;
        mLoaded = false;
    }

    public int load() {
        reset();
        int ret = LOAD_FAILED_OTHER;
        try {
            HttpGet request = new HttpGet();
            request.setURI(mUri);
            HttpResponse response = Utils.getHttpClient().execute(request);

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                L.d("response entity is null");
                return ret;
            }

            Scanner s = new Scanner(entity.getContent());
            boolean firstLine = true;
            boolean isStreamInf = false;
            int bandwidth = -1;
            int duration = -1;
            int mediaSequenceNumber = -1;
            while (s.hasNextLine()) {
                String line = s.nextLine().trim();
                if (TextUtils.isEmpty(line)) {
                    continue;
                }

                L.d("line:" + line);
                if (line.startsWith(EX_PREFIX)) {
                    if (firstLine) {
                        checkFirstLine(line);
                        firstLine = false;
                    } else if (line.startsWith(EXTINF)) {
                        duration = (int) Math.ceil(parseNumber(line));
                    } else if (line.startsWith(EXT_X_ENDLIST)) {
                        mHasEnd = true;
                    } else if (line.startsWith(EXT_X_TARGET_DURATION)) {
                        mTargetDuration = (int) parseNumber(line);
                    } else if (line.startsWith(EXT_X_MEDIA_SEQUENCE)) {
                        mMediaSequenceNumber = (int) parseNumber(line);
                        mediaSequenceNumber = mMediaSequenceNumber;
                    } else if (line.startsWith(EXT_X_STREAM_INF)) {
                        isStreamInf = true;
                        bandwidth = parseBandwidth(line);
                    } else {
                        L.d("ignore:" + line);
                    }
                } else if (line.startsWith(COMMENT_PREFIX)) {
                    // Nothing
                } else {
                    if (firstLine) {
                        checkFirstLine(line);
                        firstLine = false;
                    }
                    URI mediaUri = mUri.resolve(line);
                    if (isStreamInf) {
                        if (mVariants == null) {
                            mVariants = new ArrayList<M3U8>();
                        }
                        M3U8 variant = new M3U8(mediaUri);
                        variant.mBandwidth = bandwidth;
                        mVariants.add(variant);
                        L.d("variant: " + bandwidth + " " + mediaUri);
                        isStreamInf = false;
                    } else {
                        if (mEntries == null) {
                            mEntries = new ArrayList<M3U8Entry>();
                        }
                        M3U8Entry entry = new M3U8Entry(mediaUri, duration, mediaSequenceNumber);
                        mEntries.add(entry);
                        L.d("media:" + mediaSequenceNumber + " " + duration + " " + mediaUri);
                        duration = -1;
                        if (mediaSequenceNumber >= 0) {
                            mediaSequenceNumber++;
                        }
                    }
                }
            }
            mLoaded = true;
            ret = LOAD_OK;
        } catch (ClientProtocolException e) {
            L.d("ClientProtocolException:" + e.getMessage());
        } catch (IOException e) {
            L.d("IOException:" + e.getMessage());
            ret = LOAD_FAILED_IO;
        } catch (NumberFormatException e) {
            L.d("NumberFormatException:" + e.getMessage());
        } catch (ParseException e) {
            L.d("ParseException:" + e.getMessage());
        } catch (Exception e) {
            L.d("Exception:" + e.getMessage());
        }
        
        return ret;
    }

    private void checkFirstLine(String line) throws ParseException {
        if (!line.startsWith(EXTM3U)) {
            throw new ParseException("Playlist doesn't start with " + EXTM3U, 0);
        }
    }

    private float parseNumber(String line) throws NumberFormatException {
        float res = -1;
        int idx = line.indexOf(":");
        if (idx >= 0) {
            line = line.substring(idx + 1).trim();
            idx = line.indexOf(",");
            if (idx >= 0) {
                line = line.substring(0, idx);
            }
            res = Float.parseFloat(line);
        }
        return res;
    }

    private int parseBandwidth(String line) {
        if (!line.contains("BANDWIDTH=")) {
            return -1;
        }
        int idx = line.indexOf(":");
        if (idx >= 0) {
            line = line.substring(idx + 1);
        }
        if (line.contains(",")) {
            String[] parts = line.split(",");
            if (parts.length == 0) {
                return -1;
            }
            for (String part : parts) {
                if (part.contains("BANDWIDTH=")) {
                    line = part;
                    break;
                }
            }
        }
        line = line.trim();
        assert line.startsWith("BANDWIDTH=");
        line = line.substring(10);
        return Integer.parseInt(line);
    }

    public int getBandwidth() {
        return mBandwidth;
    }

    public boolean hasEnd() {
        return mHasEnd;
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public int getTargetDuration() {
        return mTargetDuration;
    }

    public int getMediaSequenceNumber() {
        return mMediaSequenceNumber;
    }

    public List<M3U8Entry> getEntries() {
        return mEntries;
    }

    public List<M3U8> getVariants() {
        return mVariants;
    }

    public URI getUri () {
        return mUri;
    }

    public int getDuration() {
        int res = 0;
        if (mEntries != null) {
            for (M3U8Entry entry : mEntries) {
                if (entry.getDuration() >= 0) {
                    res += entry.getDuration();
                }
            }
        }
        return res;
    }

    public class M3U8Entry {
        private URI mUri;
        private int mDuration;
        private int mMediaSequenceNumber;

        public M3U8Entry(URI uri, int duration, int mediaSequenceNumber) {
            mUri = uri;
            mDuration = duration;
            mMediaSequenceNumber = mediaSequenceNumber;
        }

        public URI getUri() {
            return mUri;
        }

        public int getDuration() {
            return mDuration;
        }

        public int getMediaSequenceNumber() {
            return mMediaSequenceNumber;
        }
    }
}
