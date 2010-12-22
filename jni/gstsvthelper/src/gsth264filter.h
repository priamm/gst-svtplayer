#ifndef __GST_H264FILTER_H__
#define __GST_H264FILTER_H__

#include <gst/gst.h>

G_BEGIN_DECLS

#define GST_TYPE_H264_FILTER \
  (gst_h264_filter_get_type())
#define GST_H264_FILTER(obj) \
  (G_TYPE_CHECK_INSTANCE_CAST((obj),GST_TYPE_H264_FILTER,GstH264Filter))
#define GST_H264_FILTER_CLASS(klass) \
  (G_TYPE_CHECK_CLASS_CAST((klass),GST_TYPE_H264_FILTER,GstH264FilterClass))
#define GST_IS_H264_FILTER(obj) \
  (G_TYPE_CHECK_INSTANCE_TYPE((obj),GST_TYPE_H264_FILTER))
#define GST_IS_H264_FILTER_CLASS(klass) \
  (G_TYPE_CHECK_CLASS_TYPE((klass),GST_TYPE_H264_FILTER))

typedef struct _GstH264Filter      GstH264Filter;
typedef struct _GstH264FilterClass GstH264FilterClass;

struct _GstH264Filter
{
  GstElement element;

  GstPad   *sinkpad, *srcpad;
  GstBufferList *list;
  GstBufferListIterator *inserter;
  gboolean  caps_ok;
};

struct _GstH264FilterClass 
{
  GstElementClass parent_class;
};

GType gst_h264_filter_get_type (void);

G_END_DECLS

#endif /* __GST_H264FILTER_H__ */
