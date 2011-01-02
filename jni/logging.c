#include "logging.h"

void svtp_glib_log (const gchar *log_domain, GLogLevelFlags log_level,
    const gchar *log_message, gpointer user_data) {
  const gchar *domain = log_domain ? log_domain : "";
  if ((log_level & G_LOG_LEVEL_ERROR) != 0) {
    LOGE (log_message ? log_message : "NULL");
  } else if ((log_level & G_LOG_LEVEL_CRITICAL) != 0) {
    LOGE (log_message ? log_message : "NULL");
  } else if ((log_level & G_LOG_LEVEL_WARNING) != 0) {
    LOGW (log_message ? log_message : "NULL");
  } else if ((log_level & G_LOG_LEVEL_MESSAGE) != 0) {
    LOGI (log_message ? log_message : "NULL");
  } else if ((log_level & G_LOG_LEVEL_INFO) != 0) {
    LOGV (log_message ? log_message : "NULL");
  } else if ((log_level & G_LOG_LEVEL_DEBUG) != 0) {
    LOGD (log_message ? log_message : "NULL");
  } else {
    LOGW (log_message ? log_message : "NULL");
  }
}

void svtp_gst_log (GstDebugCategory * category, GstDebugLevel level,
    const gchar * file, const gchar * function, gint line, GObject * object,
    GstDebugMessage * message, gpointer unused) {
  gchar *name;
  gchar *msg;

  if (GST_IS_OBJECT (object) && GST_OBJECT_NAME (object)) {
    name = GST_OBJECT_NAME (object);
  } else {
    name = "";
  }
  msg = g_strdup_printf ("[%s] %s %s", gst_debug_category_get_name (category),
      name, gst_debug_message_get (message));
  switch (level) {
  case GST_LEVEL_ERROR:
    LOGE (msg);
    break;
  case GST_LEVEL_WARNING:
    LOGW (msg);
    break;
  case GST_LEVEL_INFO:
    LOGI (msg);
    break;
  case GST_LEVEL_DEBUG:
    LOGD (msg);
    break;
  case GST_LEVEL_LOG:
    LOGV (msg);
    break;
  default:
    break;

    g_free (msg);
  }
}  
