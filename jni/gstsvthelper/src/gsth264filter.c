#include <gst/gst.h>
#include <string.h>

#include "gstsvthelper.h"
#include "gsth264filter.h"

#define GST_CAT_DEFAULT gst_svthelper_debug

static GstStaticPadTemplate sink_template = GST_STATIC_PAD_TEMPLATE ("sink",
    GST_PAD_SINK,
    GST_PAD_ALWAYS,
    GST_STATIC_CAPS ("video/x-h264;"));

static GstStaticPadTemplate src_template = GST_STATIC_PAD_TEMPLATE ("src",
    GST_PAD_SRC,
    GST_PAD_ALWAYS,
    GST_STATIC_CAPS ("video/x-h264;"));

GST_BOILERPLATE (GstH264Filter, gst_h264_filter, GstElement, GST_TYPE_ELEMENT);

static void gst_h264_filter_finalize (GObject * obj);
static gboolean gst_h264_filter_set_caps (GstPad * pad, GstCaps * caps);
static GstFlowReturn gst_h264_filter_chain (GstPad * pad, GstBuffer * buf);
static GstStateChangeReturn gst_h264_filter_change_state (GstElement * element,
    GstStateChange transition);

static void
gst_h264_filter_base_init (gpointer gclass)
{
  GstElementClass *element_class = GST_ELEMENT_CLASS (gclass);

  gst_element_class_set_details_simple(element_class,
    "h264filter",
    "H264 filter",
    "Video/Filter",
    "Jonas Larsson <jonas@hallerud.se>");

  gst_element_class_add_pad_template (element_class,
      gst_static_pad_template_get (&src_template));
  gst_element_class_add_pad_template (element_class,
      gst_static_pad_template_get (&sink_template));
}

static void
gst_h264_filter_class_init (GstH264FilterClass * klass)
{
  GObjectClass *gobject_class;
  GstElementClass *gstelement_class;

  gobject_class = (GObjectClass *) klass;
  gobject_class->finalize = GST_DEBUG_FUNCPTR (gst_h264_filter_finalize);

  gstelement_class = (GstElementClass *) klass;
  gstelement_class->change_state = gst_h264_filter_change_state;
}

static void
gst_h264_filter_init (GstH264Filter * self, GstH264FilterClass * klass)
{
  self->caps_ok = FALSE;
  self->list = NULL;
  self->inserter = NULL;
  
  self->sinkpad = gst_pad_new_from_static_template (&sink_template, "sink");
  gst_pad_set_setcaps_function (self->sinkpad,
      GST_DEBUG_FUNCPTR (gst_h264_filter_set_caps));
  gst_pad_set_chain_function (self->sinkpad,
      GST_DEBUG_FUNCPTR (gst_h264_filter_chain));

  self->srcpad = gst_pad_new_from_static_template (&src_template, "src");

  gst_element_add_pad (GST_ELEMENT (self), self->sinkpad);
  gst_element_add_pad (GST_ELEMENT (self), self->srcpad);
}

static void
gst_h264_filter_finalize (GObject * obj)
{
  GstH264Filter *self;

  self = GST_H264_FILTER (obj);

  if (self->inserter != NULL) {
    gst_buffer_list_iterator_free (self->inserter);
    self->inserter = NULL;
  }

  if (self->list != NULL) {
    gst_buffer_list_unref (self->list);
    self->list = NULL;
  }

  G_OBJECT_CLASS (parent_class)->finalize (obj);
}

static GstStateChangeReturn
gst_h264_filter_change_state (GstElement * element, GstStateChange transition)
{
  GstH264Filter *self;

  self = GST_H264_FILTER (element);

  switch (transition) {
    case GST_STATE_CHANGE_PAUSED_TO_PLAYING:
      self->last_time = GST_CLOCK_TIME_NONE;
      break;
    default:
      break;
  }

  return GST_ELEMENT_CLASS (parent_class)->change_state (element, transition);
}
  
static gboolean
gst_h264_filter_set_caps (GstPad * pad, GstCaps * caps)
{
  GstH264Filter *self;
  GstStructure *s;
  gboolean width, height, codec_data, accepted;

  self = GST_H264_FILTER (GST_PAD_PARENT (pad));
  s = gst_caps_get_structure (caps, 0);

  width = gst_structure_has_field (s, "width");
  height = gst_structure_has_field (s, "height");
  codec_data = gst_structure_has_field (s, "codec_data");

  self->caps_ok = width && height && codec_data;
  accepted = gst_pad_set_caps (self->srcpad, caps);

  if (width) {
    GST_INFO_OBJECT (self, "caps has width");
  } else {
    GST_INFO_OBJECT (self, "caps has no width");
  }

  if (height) {
    GST_INFO_OBJECT (self, "caps has height");
  } else {
    GST_INFO_OBJECT (self, "caps has no height");
  }

  if (codec_data) {
    GST_INFO_OBJECT (self, "caps has codec_data");
  } else {
    GST_INFO_OBJECT (self, "caps has no codec_data");
  }

  if (self->caps_ok) {
    GST_INFO_OBJECT (self, "caps is ok");
  } else {
    GST_INFO_OBJECT (self, "caps is not ok");
  }

  if (accepted) {
    GST_INFO_OBJECT (self, "caps accepted by set_caps");
  } else {
    GST_INFO_OBJECT (self, "caps rejected by set_caps");
  }

  return TRUE;
}


static GstFlowReturn
gst_h264_filter_chain (GstPad * pad, GstBuffer * buf)
{
  GstH264Filter *self;
  GstFlowReturn ret;

  self = GST_H264_FILTER (GST_PAD_PARENT (pad));

  // TODO: Property
  if (G_UNLIKELY (self->last_time != GST_CLOCK_TIME_NONE && 
      GST_BUFFER_TIMESTAMP (buf) < self->last_time)) {
    GST_INFO_OBJECT (self, "dropping buffer, %" GST_TIME_FORMAT " < %"
        GST_TIME_FORMAT, GST_TIME_ARGS (GST_BUFFER_TIMESTAMP (buf)),
        GST_TIME_ARGS (self->last_time));
    return GST_FLOW_OK;
  }

  self->last_time = GST_BUFFER_TIMESTAMP (buf);

  if (G_UNLIKELY (!self->caps_ok)) {
    if (G_UNLIKELY (self->list == NULL)) {
      GST_DEBUG_OBJECT (self, "creating buffer list");
      self->list = gst_buffer_list_new ();
      self->inserter = gst_buffer_list_iterate (self->list);
      gst_buffer_list_iterator_add_group (self->inserter);
    }
  
    gst_buffer_list_iterator_add (self->inserter, buf);
    GST_DEBUG_OBJECT (self, "caps_ok false, saving buffer (%i)",
      GST_BUFFER_SIZE (buf));
    ret = GST_FLOW_OK;
  } else {
    if (G_UNLIKELY (self->list != NULL)) {
      GST_DEBUG_OBJECT (self, "processing saved buffers");
      GstBufferListIterator *it;
      GstBuffer *saved_buf;
      guint cnt;

      it = gst_buffer_list_iterate (self->list);
      cnt = 0;
      while (gst_buffer_list_iterator_next_group (it)) {
        while ((saved_buf = gst_buffer_list_iterator_next (it)) != NULL) {
          cnt += 1;
          GST_DEBUG_OBJECT (self, "pushing saved buffer (%i)",
            GST_BUFFER_SIZE (saved_buf));
          gst_buffer_set_caps (saved_buf, GST_PAD_CAPS (self->srcpad));
          /* push takes our ref, but list needs it so add one */
          gst_buffer_ref (saved_buf);
          ret = gst_pad_push (self->srcpad, saved_buf);
          if (G_LIKELY (ret == GST_FLOW_OK)) {
            GST_DEBUG_OBJECT (self, "saved buffer pushed ok");
          } else {
            GST_WARNING_OBJECT (self, "saved buffer push failed");
          }
        }
      }
      gst_buffer_list_iterator_free (it);
      gst_buffer_list_iterator_free (self->inserter);
      gst_buffer_list_unref (self->list);
      self->inserter = NULL;
      self->list = NULL;
      GST_DEBUG_OBJECT (self, "%u saved buffers processed", cnt);
    }

    GST_LOG_OBJECT (self, "setting caps");
    gst_buffer_set_caps (buf, GST_PAD_CAPS (self->srcpad));
    GST_LOG_OBJECT (self, "pushing buffer");
    ret = gst_pad_push (self->srcpad, buf);
    if (G_LIKELY (ret == GST_FLOW_OK)) {
      GST_LOG_OBJECT (self, "buffer pushed ok");
    } else {
      GST_LOG_OBJECT (self, "buffer push failed");
    }
  }

  return ret;
}

