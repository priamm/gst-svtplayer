#include <gst/gst.h>

#include "gstsvthelper.h"
#include "gstaacfilter.h"
#include "gsth264filter.h"

/* Debug categories */
GST_DEBUG_CATEGORY (gst_svthelper_debug);

/* Register plugin */
static gboolean
plugin_init (GstPlugin * plugin)
{
  if (!gst_element_register (plugin, "aacfilter", GST_RANK_NONE,
      GST_TYPE_AAC_FILTER)) {
    return FALSE;
  }

  if (!gst_element_register (plugin, "h264filter", GST_RANK_NONE,
      GST_TYPE_H264_FILTER)) {
    return FALSE;
  }

  GST_DEBUG_CATEGORY_INIT (gst_svthelper_debug, "svthelper", 0,
      "SVT play helpers");

  return TRUE;
}

/* Define plugin */
GST_PLUGIN_DEFINE (
    GST_VERSION_MAJOR,
    GST_VERSION_MINOR,
    NAME,
    DESCRIPTION,
    plugin_init,
    VERSION,
    LICENCE,
    PACKAGE,
    ORIGIN
)
