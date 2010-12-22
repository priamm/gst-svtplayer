#include <gst/gst.h>
#include <string.h>

#include "gstsvthelper.h"
#include "gstaacfilter.h"

#define GST_CAT_DEFAULT gst_svthelper_debug

static GstStaticPadTemplate sink_template = GST_STATIC_PAD_TEMPLATE ("sink",
    GST_PAD_SINK,
    GST_PAD_ALWAYS,
    GST_STATIC_CAPS ("audio/mpeg, "
        "mpegversion = (int) 4, "
        "stream-format = (string) { raw, adts };"));

static GstStaticPadTemplate src_template = GST_STATIC_PAD_TEMPLATE ("src",
    GST_PAD_SRC,
    GST_PAD_ALWAYS,
    GST_STATIC_CAPS ("audio/mpeg, "
        "mpegversion = (int) 4, "
        "stream-format = (string) raw;"));

GST_BOILERPLATE (GstAacFilter, gst_aac_filter, GstElement, GST_TYPE_ELEMENT);

static void gst_aac_filter_finalize (GObject * obj);
static gboolean gst_aac_filter_set_caps (GstPad * pad, GstCaps * caps);
static GstFlowReturn gst_aac_filter_chain (GstPad * pad, GstBuffer * buf);


static void
gst_aac_filter_base_init (gpointer gclass)
{
  GstElementClass *element_class = GST_ELEMENT_CLASS (gclass);

  gst_element_class_set_details_simple(element_class,
    "aacfilter",
    "AAC raw converter and caps filter",
    "Audio/Filter",
    "Jonas Larsson <jonas@hallerud.se>");

  gst_element_class_add_pad_template (element_class,
      gst_static_pad_template_get (&src_template));
  gst_element_class_add_pad_template (element_class,
      gst_static_pad_template_get (&sink_template));
}

static void
gst_aac_filter_class_init (GstAacFilterClass * klass)
{
  GObjectClass *gobject_class;

  gobject_class = (GObjectClass *) klass;
  gobject_class->finalize = GST_DEBUG_FUNCPTR (gst_aac_filter_finalize);
}

static void
gst_aac_filter_init (GstAacFilter * self, GstAacFilterClass * klass)
{
  self->raw = TRUE;
  self->sinkpad = gst_pad_new_from_static_template (&sink_template, "sink");
  gst_pad_set_setcaps_function (self->sinkpad,
      GST_DEBUG_FUNCPTR (gst_aac_filter_set_caps));
  gst_pad_set_chain_function (self->sinkpad,
      GST_DEBUG_FUNCPTR (gst_aac_filter_chain));

  self->srcpad = gst_pad_new_from_static_template (&src_template, "src");

  gst_element_add_pad (GST_ELEMENT (self), self->sinkpad);
  gst_element_add_pad (GST_ELEMENT (self), self->srcpad);
}

static void
gst_aac_filter_finalize (GObject * obj)
{
  GstAacFilter *self;

  self = GST_AAC_FILTER (obj);

  G_OBJECT_CLASS (parent_class)->finalize (obj);
}

static gboolean
gst_aac_filter_set_caps (GstPad * pad, GstCaps * caps)
{
  GstAacFilter *self;
  GstCaps *src_caps;
  GstStructure *s;
  const gchar *stream_format;
  gboolean ret;
  self = GST_AAC_FILTER (GST_PAD_PARENT (pad));

  src_caps = gst_caps_copy (caps);
  s = gst_caps_get_structure (src_caps, 0);
  
  stream_format = gst_structure_get_string (s, "stream-format");

  if (strcmp (stream_format, "raw") == 0) {
    self->raw = TRUE;
    GST_INFO_OBJECT (self, "got raw");
    ret = gst_pad_set_caps (self->srcpad, caps);
  } else if (strcmp (stream_format, "adts") == 0) {
    self->raw = FALSE;
    GST_INFO_OBJECT (self, "got adts");
    src_caps = gst_caps_copy (caps);
    s = gst_caps_get_structure (src_caps, 0);
    gst_structure_set (s, "stream-format", G_TYPE_STRING, "raw", NULL);
    gst_pad_use_fixed_caps (self->srcpad);
    ret = gst_pad_set_caps (self->srcpad, src_caps);
    gst_caps_unref (src_caps);
  } else {
    ret = FALSE;
  }

  return ret;
}

static inline guint
gst_aac_filter_get_adts_header_len (const guchar * data)
{
  return (data[1] & 0x80) == 0x80 ? 7 : 9;
}

static inline gint
gst_aac_filter_get_adts_header_sr_idx (const guchar * data)
{
  return (data[2] & 0x3c) >> 2;
}

static inline gint
gst_aac_filter_get_adts_header_channels (const guchar * data)
{
  return ((data[2] & 0x01) << 2) | ((data[3] & 0xc0) >> 6);
}

static inline gint
gst_aac_filter_get_adts_header_profile (const guchar * data)
{
  return ((data[2] & 0xc0) >> 6) + 1;
}

static GstFlowReturn
gst_aac_filter_chain (GstPad * pad, GstBuffer * buf)
{
  GstAacFilter *self;
  GstBuffer *raw_buf;
  guint raw_len;
  GstFlowReturn ret;

  self = GST_AAC_FILTER (GST_PAD_PARENT (pad));

  if (G_UNLIKELY (self->raw)) {
    GST_LOG_OBJECT (self, "pushing raw buffer, size %i", GST_BUFFER_SIZE (buf));
    ret = gst_pad_push (self->srcpad, buf);
  } else {
    GstCaps *src_caps;
    GstStructure *s;

    src_caps = GST_PAD_CAPS (self->srcpad);
    s = gst_caps_get_structure (src_caps, 0);
    if (G_UNLIKELY (!gst_structure_has_field (s, "codec_data"))) {
      GstBuffer *codec_data;
      guchar *cdata;
      guint sr_idx;
      gint channels;
      gint profile;
      
      GST_DEBUG_OBJECT (self, "no codec data, constructing");
      codec_data = gst_buffer_new_and_alloc(2);
      cdata = GST_BUFFER_DATA(codec_data);

      sr_idx = gst_aac_filter_get_adts_header_sr_idx (GST_BUFFER_DATA (buf));
      channels = gst_aac_filter_get_adts_header_channels (GST_BUFFER_DATA (buf));
      profile = gst_aac_filter_get_adts_header_profile (GST_BUFFER_DATA (buf));

      cdata[0] = ((profile & 0x1F) << 3) | ((sr_idx & 0xE) >> 1);
      cdata[1] = ((sr_idx & 0x1) << 7) | ((channels & 0xF) << 3);
      GST_DEBUG_OBJECT (self, "setting new codec_data");
      gst_caps_set_simple (src_caps, "codec_data", GST_TYPE_BUFFER, codec_data,
            NULL);
      gst_pad_set_caps (self->srcpad, src_caps);
      gst_buffer_unref (codec_data);
    }
    raw_len = gst_aac_filter_get_adts_header_len (GST_BUFFER_DATA (buf));
    raw_buf = gst_buffer_create_sub (buf, raw_len,
        GST_BUFFER_SIZE (buf) - raw_len);
    GST_BUFFER_TIMESTAMP (raw_buf) = GST_BUFFER_TIMESTAMP (buf);
    GST_BUFFER_DURATION (raw_buf) = GST_BUFFER_DURATION (buf);
    GST_BUFFER_OFFSET (raw_buf) = GST_BUFFER_OFFSET (buf);
    gst_buffer_set_caps (raw_buf, src_caps);
    GST_LOG_OBJECT (self, "pushing raw converted buffer %i -> %i",
        GST_BUFFER_SIZE (buf), GST_BUFFER_SIZE (raw_buf));
    ret = gst_pad_push (self->srcpad, raw_buf);
  }
  
  if (G_LIKELY (ret == GST_FLOW_OK)) {
    GST_LOG_OBJECT (self, "buffer pushed ok");
  } else {
    GST_WARNING_OBJECT (self, "buffer push failed");
  }

  return ret;
}

