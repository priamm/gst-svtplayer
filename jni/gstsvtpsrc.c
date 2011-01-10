#include <gst/gst.h>

#include <jni.h>

#include "svtplayer.h"
#include "gstsvtpsrc.h"

GST_DEBUG_CATEGORY_STATIC (gst_svtp_src_debug);
#define GST_CAT_DEFAULT gst_svtp_src_debug

#define BUF_SIZE 8192

static GstStaticPadTemplate src_template = GST_STATIC_PAD_TEMPLATE ("src",
    GST_PAD_SRC,
    GST_PAD_ALWAYS,
    GST_STATIC_CAPS_ANY);

enum
{
  PROP_0,
  PROP_ID,
  PROP_DURATION
};

static void gst_svtp_src_set_property (GObject * object, guint prop_id,
    const GValue * value, GParamSpec * pspec);
static void gst_svtp_src_get_property (GObject * object, guint prop_id,
    GValue * value, GParamSpec * pspec);
static void gst_svtp_src_finalize (GObject * object);
static void gst_svtp_src_loop (GstSvtpSrc * self);

static gboolean gst_svtp_src_stop (GstBaseSrc * src);
static gboolean gst_svtp_src_start (GstBaseSrc * src);
static gboolean gst_svtp_src_unlock (GstBaseSrc * src);
static gboolean gst_svtp_src_unlock_stop (GstBaseSrc * src);
static gboolean gst_svtp_src_is_seekable (GstBaseSrc * src);
static gboolean gst_svtp_src_prepare_seek_segment (GstBaseSrc * src,
    GstEvent * event, GstSegment * segment);
static gboolean gst_svtp_src_do_seek (GstBaseSrc * src, GstSegment * segment);
static GstFlowReturn gst_svtp_src_create (GstPushSrc * pushsrc,
    GstBuffer ** buffer);
static gboolean gst_svtp_src_query (GstBaseSrc * src, GstQuery * query);

GST_BOILERPLATE (GstSvtpSrc, gst_svtp_src, GstPushSrc, GST_TYPE_PUSH_SRC);

static void
gst_svtp_src_base_init (gpointer g_class)
{
  GstElementClass *element_class = GST_ELEMENT_CLASS (g_class);

  gst_element_class_add_pad_template (element_class,
      gst_static_pad_template_get (&src_template));

  gst_element_class_set_details_simple (element_class,
      "SVT Player Source",
      "Source/EndOfRainbow",
      "Read streams from SVT player",
      "Jonas Larsson <jonas@hallerud.se>");

  GST_DEBUG_CATEGORY_INIT (gst_svtp_src_debug, "svtpsrc", 0, "svtpsrc element");
}

static void
gst_svtp_src_class_init (GstSvtpSrcClass * klass)
{
  GObjectClass *gobject_class;
  GstBaseSrcClass *gstbasesrc_class;
  GstPushSrcClass *gstpushsrc_class;

  gobject_class = G_OBJECT_CLASS (klass);
  gstbasesrc_class = GST_BASE_SRC_CLASS (klass);
  gstpushsrc_class = GST_PUSH_SRC_CLASS (klass);

  gobject_class->finalize = gst_svtp_src_finalize;
  gobject_class->set_property = gst_svtp_src_set_property;
  gobject_class->get_property = gst_svtp_src_get_property;

  g_object_class_install_property (gobject_class, PROP_DURATION,
      g_param_spec_int ("duration", "duration",
          "Stream duration in seconds. <=0 if unknown", G_MININT, G_MAXINT, 0,
           G_PARAM_READWRITE | G_PARAM_STATIC_STRINGS));
  g_object_class_install_property (gobject_class, PROP_DURATION,
      g_param_spec_int ("id", "id",
          "Stream id", G_MININT, G_MAXINT, 0,
           G_PARAM_READWRITE | G_PARAM_STATIC_STRINGS));
  gstbasesrc_class->start = GST_DEBUG_FUNCPTR (gst_svtp_src_start);
  gstbasesrc_class->stop = GST_DEBUG_FUNCPTR (gst_svtp_src_stop);
  gstbasesrc_class->unlock = GST_DEBUG_FUNCPTR (gst_svtp_src_unlock);
  gstbasesrc_class->unlock_stop = GST_DEBUG_FUNCPTR (gst_svtp_src_unlock_stop);
  gstbasesrc_class->is_seekable = GST_DEBUG_FUNCPTR (gst_svtp_src_is_seekable);
  gstbasesrc_class->prepare_seek_segment =
      GST_DEBUG_FUNCPTR (gst_svtp_src_prepare_seek_segment);
  gstbasesrc_class->do_seek = GST_DEBUG_FUNCPTR (gst_svtp_src_do_seek);
  gstpushsrc_class->create = GST_DEBUG_FUNCPTR (gst_svtp_src_create);
  gstbasesrc_class->query = GST_DEBUG_FUNCPTR (gst_svtp_src_query);
}

static void
gst_svtp_src_init (GstSvtpSrc * self, GstSvtpSrcClass * klass)
{
  self->thread = NULL;
  self->lock = g_mutex_new ();
  self->not_empty = g_cond_new ();
  self->not_full = g_cond_new ();
  self->seek_handled = g_cond_new ();
  self->duration = 0;
  self->seek_pos = GST_CLOCK_TIME_NONE;
  self->started = FALSE;
  self->buf = g_malloc (BUF_SIZE);
  self->buf_len = 0;
  self->id = 0;

  gst_base_src_set_format (GST_BASE_SRC (self), GST_FORMAT_TIME);
}

static void
gst_svtp_src_finalize (GObject * object)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (object);

  g_mutex_free (self->lock);
  g_cond_free (self->not_empty);
  g_cond_free (self->not_full);
  g_cond_free (self->seek_handled);
  g_free (self->buf);
  self->buf_len = 0;

  G_OBJECT_CLASS (parent_class)->finalize (object);
}

static void
gst_svtp_src_set_property (GObject * object, guint prop_id,
    const GValue * value, GParamSpec * pspec)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (object);

  switch (prop_id) {
    case PROP_ID:
      self->id = g_value_get_int (value);
      break;
    case PROP_DURATION:
      self->duration = g_value_get_int (value);
      break;
    default:
      G_OBJECT_WARN_INVALID_PROPERTY_ID (object, prop_id, pspec);
      break;
  }
}

static void
gst_svtp_src_get_property (GObject * object, guint prop_id, GValue * value,
    GParamSpec * pspec)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (object);

  switch (prop_id) {
    case PROP_ID:
      g_value_set_int (value, self->id);
      break;
    case PROP_DURATION:
      g_value_set_int (value, self->duration);
      break;
    default:
      G_OBJECT_WARN_INVALID_PROPERTY_ID (object, prop_id, pspec);
      break;
  }
}

static gboolean
gst_svtp_src_start (GstBaseSrc * basesrc)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (basesrc);

  g_mutex_lock (self->lock);

  self->seek_pos = GST_CLOCK_TIME_NONE;
  self->discont = TRUE;
  self->started = TRUE;
  self->thread = g_thread_create((GThreadFunc) gst_svtp_src_loop, self, TRUE,
      NULL);

  g_mutex_unlock (self->lock);

  GST_INFO_OBJECT (self, "started");

  return TRUE;
}

static gboolean
gst_svtp_src_stop (GstBaseSrc * basesrc)
{
  GstSvtpSrc *self;
  GThread *thread;

  self = GST_SVTP_SRC (basesrc);

  GST_DEBUG_OBJECT (self, "stopping");
 
  g_mutex_lock (self->lock);

  self->started = FALSE;
  g_cond_signal (self->not_empty);
  g_cond_signal (self->not_full);
  g_cond_signal (self->seek_handled);
  thread = self->thread;
  self->thread = NULL;
  self->seek_pos = GST_CLOCK_TIME_NONE;

  g_mutex_unlock (self->lock);

  if (thread != NULL) {
    g_thread_join (thread);
  }

  self->discont = TRUE;

  GST_INFO_OBJECT (self, "stopped");

  return TRUE;
}

static gboolean
gst_svtp_src_unlock (GstBaseSrc * src)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (src);

  GST_DEBUG_OBJECT (self, "unlocking");

  g_mutex_lock (self->lock);
  
  self->unlocked = TRUE;
  g_cond_signal (self->not_empty);
  g_cond_signal (self->seek_handled);
  
  g_mutex_unlock (self->lock);

  GST_INFO_OBJECT (self, "unlocked");
  return TRUE;
}

static gboolean
gst_svtp_src_unlock_stop (GstBaseSrc * src)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (src);

  GST_DEBUG_OBJECT (self, "unlock stopping");

  g_mutex_lock (self->lock);  
  
  self->unlocked = FALSE;
  
  g_mutex_unlock (self->lock);

  GST_INFO_OBJECT (self, "unlock stopped");
  return TRUE;
}

static GstFlowReturn
gst_svtp_src_create (GstPushSrc * pushsrc, GstBuffer ** buffer)
{
  return GST_FLOW_UNEXPECTED;
/*
  GstSvtpSrc *self;
  GstBuffer* buf;

  self = GST_SVTP_SRC (pushsrc);

  g_mutex_lock (self->lock);

  if (self->unlocked) {
    g_mutex_unlock (self->lock);
    return GST_FLOW_WRONG_STATE;
  }

  if (self->buf_len == 0) {
    GST_LOG_OBJECT (self, "waiting for data");
    g_cond_wait (self->not_empty, self->lock);    
  }

  if (self->unlocked) {
    GST_INFO_OBJECT (self, "wrong state (unlocked)");
    g_mutex_unlock (self->lock);
    return GST_FLOW_WRONG_STATE;
  }

  if (self->buf_len == 0) {
    GST_INFO_OBJECT (self, "no data (EOS)");
    g_mutex_unlock (self->lock);
    return GST_FLOW_UNEXPECTED;
  }

  buf = gst_buffer_try_new_and_alloc (self->buf_len);
  memcpy (GST_BUFFER_DATA (buf), self->buf, self->buf_len);  
  GST_BUFFER_SIZE (buf) = self->buf_len;
  if (self->discont) {
    GST_BUFFER_FLAG_SET (buf, GST_BUFFER_FLAG_DISCONT);
    self->discont = FALSE;
  }
  self->buf_len = 0;

  g_cond_signal (self->not_full);

  g_mutex_unlock (self->lock);

  GST_LOG_OBJECT (self, "return buffer with length %i",
      GST_BUFFER_SIZE (buf));

  *buffer = buf;

  return GST_FLOW_OK; */
}

static gboolean
gst_svtp_src_query (GstBaseSrc * basesrc, GstQuery * query)
{
  GstSvtpSrc *self;
  gboolean ret;

  self = GST_SVTP_SRC (basesrc);
  ret = FALSE;

  switch (GST_QUERY_TYPE (query)) {
    case GST_QUERY_DURATION:
      if (self->duration > 0) {
        GstFormat format;
        gdouble duration;

        gst_query_parse_duration (query, &format, NULL);
        if (format == GST_FORMAT_TIME) {
          gst_query_set_duration (query, format, duration * GST_SECOND);
          ret = TRUE;
        }
      }
      break;
    default:
      ret = FALSE;
      break;
  }

  if (!ret) {
    ret = GST_BASE_SRC_CLASS (parent_class)->query (basesrc, query);
  }

  return ret;
}

static gboolean
gst_svtp_src_is_seekable (GstBaseSrc * basesrc)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (basesrc);

  return self->duration > 0;
}

static gboolean
gst_svtp_src_prepare_seek_segment (GstBaseSrc * basesrc, GstEvent * event,
    GstSegment * segment)
{
  GstSvtpSrc *self;
  GstSeekType cur_type, stop_type;
  gint64 cur, stop;
  GstSeekFlags flags;
  GstFormat format;
  gdouble rate;

  self = GST_SVTP_SRC (basesrc);

  gst_event_parse_seek (event, &rate, &format, &flags,
      &cur_type, &cur, &stop_type, &stop);

  if (self->duration <= 0) {
    GST_DEBUG_OBJECT (self, "Not seekable");
    return FALSE;
  }

  if (format != GST_FORMAT_TIME) {
    GST_DEBUG_OBJECT (self, "Seeking only supported in TIME format");
    return FALSE;
  }

  if (stop_type != GST_SEEK_TYPE_NONE) {
    GST_LOG_OBJECT (self, "Setting a stop position is not supported");
    return FALSE;
  }

  gst_segment_init (segment, GST_FORMAT_TIME);
  gst_segment_set_seek (segment, rate, format, flags, cur_type, cur, stop_type,
      stop, NULL);

  return TRUE;
}

static gboolean
gst_svtp_src_do_seek (GstBaseSrc * basesrc, GstSegment * segment)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (basesrc);

  if (self->duration <= 0) {
    GST_DEBUG_OBJECT (self, "Not seekable");
    return FALSE;
  }

  if (segment->format != GST_FORMAT_TIME) {
    GST_DEBUG_OBJECT (self, "Seeking only supported in TIME format");
    return FALSE;
  }

  self->discont = TRUE;

  GST_DEBUG_OBJECT (self, "Seek to %" GST_TIME_FORMAT,
      GST_TIME_ARGS (segment->start));

  g_mutex_lock (self->lock);

  self->seek_pos = segment->start;
  g_cond_signal (self->not_full);

  g_mutex_unlock (self->lock);

  return TRUE;
}

static void
gst_svtp_src_loop (GstSvtpSrc * self)
{
  GST_INFO_OBJECT (self, "jni loop starting");

  JNIEnv *env;
  jint jni_res;
  jfieldID jni_field;
  jobject rtsp;
  jmethodID seek_id;
  jmethodID get_id;
  jobject buf_obj;
  GTimeVal wait_time;

  jni_res = (*svtp_vm)->AttachCurrentThread (svtp_vm, &env, NULL);
  GST_DEBUG_OBJECT (self, "AttachCurrentThread %i %p", jni_res, env);
  if (jni_res != 0) {
    goto cleanup;
  }
  jni_field = (*env)->GetStaticFieldID (env, svtp_rtsp_class, "sInstance",
      "Lfoss/jonasl/svtplayer/rtsp/RTSPService;");
  rtsp = (*env)->GetStaticObjectField (env, svtp_rtsp_class, jni_field);
  if (rtsp == NULL || (*env)->IsSameObject (env, rtsp, NULL)) {
    GST_ERROR_OBJECT (self, "rtsp is null");
    goto cleanup;
  }
  seek_id = (*env)->GetMethodID (env, svtp_rtsp_class, "seek", "(IJ)V");
  get_id = (*env)->GetMethodID (env, svtp_rtsp_class, "getData",
      "(ILjava/nio/ByteBuffer;I)I");
  buf_obj = (*env)->NewDirectByteBuffer (env, self->buf, BUF_SIZE);

  while (self->started) {
    g_mutex_lock (self->lock);

    if (G_UNLIKELY (self->seek_pos != GST_CLOCK_TIME_NONE)) {
      (*env)->CallVoidMethod (env, rtsp, seek_id, self->id, self->seek_pos);
      self->seek_pos = GST_CLOCK_TIME_NONE;
      self->buf_len = 0;
      GST_INFO_OBJECT (self, "seek handled");
      g_cond_signal (self->seek_handled);
    }

    if (G_LIKELY (self->buf_len == 0)) {
      GST_LOG_OBJECT (self, "getting bytes");
      self->buf_len = (*env)->CallIntMethod (env, rtsp, get_id, self->id,
          buf_obj, BUF_SIZE);
      GST_LOG_OBJECT (self, "got %i bytes", self->buf_len);
      if (G_LIKELY (self->buf_len > 0)) {
        g_cond_signal (self->not_empty);
      } else if (self->buf_len < 0) {
        self->buf_len = 0;
        GST_LOG_OBJECT (self, "signalling eos");
        g_cond_signal (self->not_empty);
      }
    } else {
      GST_DEBUG_OBJECT (self, "waiting for data to be consumed");
      g_get_current_time (&wait_time);
      g_time_val_add (&wait_time, 500 * 1000); // 500 ms
      g_cond_timed_wait (self->not_full, self->lock, &wait_time);
    }

    g_mutex_unlock (self->lock);
  }
   //(*env)->CallVoidMethod (env, rtsp, test_id, self->id);

cleanup:
  jni_res = (*svtp_vm)->DetachCurrentThread (svtp_vm);
  GST_INFO_OBJECT (self, "jni loop exiting");
}
