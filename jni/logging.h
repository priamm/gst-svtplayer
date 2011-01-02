#ifndef LOGGING_H
#define LOGGING_H

#include <android/log.h>

#include <glib.h>
#include <gst/gst.h>

#define  LOG_TAG    "libsvtplayer"
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

void svtp_glib_log (const gchar *log_domain, GLogLevelFlags log_level,
    const gchar *log_message, gpointer user_data);

void svtp_gst_log (GstDebugCategory * category, GstDebugLevel level,
    const gchar * file, const gchar * function, gint line, GObject * object,
    GstDebugMessage * message, gpointer unused);
#endif
