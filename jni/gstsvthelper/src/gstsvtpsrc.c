#include <gst/gst.h>

#include "gstsvthelper.h"
#include "gstsvtpsrc.h"

#define GST_CAT_DEFAULT gst_svthelper_debug

static GstStaticPadTemplate src_template = GST_STATIC_PAD_TEMPLATE ("src",
    GST_PAD_SRC,
    GST_PAD_ALWAYS,
    GST_STATIC_CAPS_ANY);

enum
{
  PROP_0,
  PROP_DURATION
};

static void gst_svtp_src_set_property (GObject * object, guint prop_id,
    const GValue * value, GParamSpec * pspec);
static void gst_svtp_src_get_property (GObject * object, guint prop_id,
    GValue * value, GParamSpec * pspec);
static void gst_svtp_src_finalize (GObject * object);

static gboolean gst_svtp_src_stop (GstBaseSrc * src);
static gboolean gst_svtp_src_start (GstBaseSrc * src);
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

  gstbasesrc_class->start = GST_DEBUG_FUNCPTR (gst_svtp_src_start);
  gstbasesrc_class->stop = GST_DEBUG_FUNCPTR (gst_svtp_src_stop);
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
  self->duration = 0;
}

static void
gst_svtp_src_finalize (GObject * object)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (object);

  G_OBJECT_CLASS (parent_class)->finalize (object);
}

static void
gst_svtp_src_set_property (GObject * object, guint prop_id,
    const GValue * value, GParamSpec * pspec)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (object);

  switch (prop_id) {
    case PROP_DURATION:{
      self->duration = g_value_get_int (value);
      break;
    }
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
    case PROP_DURATION:
      g_value_set_int (value, self->duration);
      break;
    default:
      G_OBJECT_WARN_INVALID_PROPERTY_ID (object, prop_id, pspec);
      break;
  }
}

static GstFlowReturn
gst_svtp_src_create (GstPushSrc * pushsrc, GstBuffer ** buffer)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (pushsrc);

  return GST_FLOW_OK;
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

  // TODO: Call boolean RTSPService.seek (int positionSecs)
  GST_DEBUG_OBJECT (self, "Seek to %" GST_TIME_FORMAT,
      GST_TIME_ARGS (segment->start));

  return TRUE;
}

static gboolean
gst_svtp_src_start (GstBaseSrc * basesrc)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (basesrc);

  self->discont = TRUE;

  return TRUE;
}

static gboolean
gst_svtp_src_stop (GstBaseSrc * basesrc)
{
  GstSvtpSrc *self;

  self = GST_SVTP_SRC (basesrc);

  self->discont = TRUE;

  return TRUE;
}
