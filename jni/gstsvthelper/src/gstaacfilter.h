#ifndef __GST_AACFILTER_H__
#define __GST_AACFILTER_H__

#include <gst/gst.h>

G_BEGIN_DECLS

#define GST_TYPE_AAC_FILTER \
  (gst_aac_filter_get_type())
#define GST_AAC_FILTER(obj) \
  (G_TYPE_CHECK_INSTANCE_CAST((obj),GST_TYPE_AAC_FILTER,GstAacFilter))
#define GST_AAC_FILTER_CLASS(klass) \
  (G_TYPE_CHECK_CLASS_CAST((klass),GST_TYPE_AAC_FILTER,GstAacFilterClass))
#define GST_IS_AAC_FILTER(obj) \
  (G_TYPE_CHECK_INSTANCE_TYPE((obj),GST_TYPE_AAC_FILTER))
#define GST_IS_AAC_FILTER_CLASS(klass) \
  (G_TYPE_CHECK_CLASS_TYPE((klass),GST_TYPE_AAC_FILTER))

typedef struct _GstAacFilter      GstAacFilter;
typedef struct _GstAacFilterClass GstAacFilterClass;

struct _GstAacFilter
{
  GstElement element;

  GstPad   *sinkpad, *srcpad;
  gboolean  raw;
};

struct _GstAacFilterClass 
{
  GstElementClass parent_class;
};

GType gst_aac_filter_get_type (void);

G_END_DECLS

#endif /* __GST_AACFILTER_H__ */
