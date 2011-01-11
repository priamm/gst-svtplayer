#ifndef __GST_SVTP_SRC_H__
#define __GST_SVTP_SRC_H__

#include <gst/base/gstbasesrc.h>
#include <gst/base/gstpushsrc.h>

G_BEGIN_DECLS

#define GST_TYPE_SVTP_SRC \
  (gst_svtp_src_get_type())
#define GST_SVTP_SRC(obj) \
  (G_TYPE_CHECK_INSTANCE_CAST((obj),GST_TYPE_SVTP_SRC,GstSvtpSrc))
#define GST_SVTP_SRC_CLASS(klass) \
  (G_TYPE_CHECK_CLASS_CAST((klass),GST_TYPE_SVTP_SRC,GstSvtpSrcClass))
#define GST_IS_SVTP_SRC(obj) \
  (G_TYPE_CHECK_INSTANCE_TYPE((obj),GST_TYPE_SVTP_SRC))
#define GST_IS_SVTP_SRC_CLASS(klass) \
  (G_TYPE_CHECK_CLASS_TYPE((klass),GST_TYPE_SVTP_SRC))

typedef struct _GstSvtpSrc      GstSvtpSrc;
typedef struct _GstSvtpSrcClass GstSvtpSrcClass;

struct _GstSvtpSrc
{
  GstPushSrc parent;

  GMutex          *lock;
  GCond           *not_empty;
  GCond           *not_full;
  GCond           *seek_handled;
  GThread         *thread;
  gboolean         started;
  gboolean         unlocked;
  gboolean         discont;
  guchar          *buf;
  gint             buf_len;
  gint             duration;
  gint64           seek_pos;
  gint             id;
};

struct _GstSvtpSrcClass
{
  GstPushSrcClass  parent;
};

GType gst_svtp_src_get_type (void);

G_END_DECLS

#endif /* __GST_SVTP_SRC_H__ */

