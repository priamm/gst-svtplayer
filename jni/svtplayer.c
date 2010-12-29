#include <android/log.h>
#include <jni.h>

#include <string.h>
#include <unistd.h>

#include <glib.h>
#include <gst/gst.h>

#define  LOG_TAG    "libsvtplayer"
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

static void
glib_log(const gchar *log_domain, GLogLevelFlags log_level,
    const gchar *log_message, gpointer user_data);

static void
gst_log(GstDebugCategory * category, GstDebugLevel level, const gchar * file,
    const gchar * function, gint line, GObject * object,
    GstDebugMessage * message, gpointer unused);

/* Init */

static void init(JNIEnv* env, jobject thiz, jstring dataPath) {
  if (!g_thread_supported ()) {
    g_thread_init (NULL);
    LOGD ("g_thread_init called");
  }

  g_log_set_handler (NULL, G_LOG_LEVEL_INFO | G_LOG_LEVEL_DEBUG
      | G_LOG_LEVEL_MESSAGE | G_LOG_LEVEL_WARNING | G_LOG_LEVEL_ERROR
      | G_LOG_FLAG_FATAL, glib_log, NULL);
  g_debug ("log_handler set, calling gst_init");

  gst_debug_set_default_threshold (GST_LEVEL_INFO);
  gst_init (NULL, NULL);
  gst_debug_remove_log_function (gst_debug_log_default);
  gst_debug_add_log_function (gst_log, NULL);

  if (gst_debug_is_active ()) {
    g_debug ("debugging active");
  } else {
    g_debug ("debugging activated");
    gst_debug_set_active (TRUE);
  }
  g_debug ("gst_init called, loading plugins");

  GstPlugin *plugin;
  GError *err = NULL;

  const jbyte *dir = (*env)->GetStringUTFChars (env, dataPath, NULL);
  if (dir == NULL) {
    g_error ("No dataPath");
  } else {
    gchar *file = g_build_filename (dir, "libgstcoreelements.so", NULL);
    plugin = gst_plugin_load_file (file, &err);
    if (plugin == NULL) {
      g_error ("Loading %s failed: %s", file, err->message);
      g_error_free (err);
    } else {
      g_debug ("%s loaded: %s", file, gst_plugin_get_name (plugin));
    }

    g_free (file);
    (*env)->ReleaseStringUTFChars (env, dataPath, dir);
  }

  LOGD ("init done");
}

/* JNI */

static void test(JNIEnv* env) {
  GstElement *pipeline;
  int i;
  gboolean ret;
  GstStateChangeReturn sret;

  g_debug ("creating pipeline");
  pipeline = gst_parse_launch (
      "fakesrc sizetype=2 ! filesink location=/mnt/sdcard/tmp.bin", NULL);
  g_assert (pipeline != NULL);

  g_debug ("setting to playing state");
  sret = gst_element_set_state (pipeline, GST_STATE_PLAYING);
  g_assert (sret != GST_STATE_CHANGE_FAILURE);

  g_debug ("sleeping");
  for (i = 0; i < 100; i++) {
    usleep (50000);
  }

  g_debug ("stopping and unreffing pipeline");
  sret = gst_element_set_state (pipeline, GST_STATE_NULL);
  g_assert (sret != GST_STATE_CHANGE_FAILURE);
  gst_object_unref (GST_OBJECT (pipeline));

}

/* glib and gst log handlers */
static void glib_log(const gchar *log_domain, GLogLevelFlags log_level,
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

static void gst_log(GstDebugCategory * category, GstDebugLevel level,
    const gchar * file, const gchar * function, gint line, GObject * object,
    GstDebugMessage * message, gpointer unused) {
  LOGD (gst_debug_message_get (message));
}

/* JNI init */
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
static JNINativeMethod native_methods[] = { { "test", "()V", &test }, {
    "init_native", "(Ljava/lang/String;)V", &init } };

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  LOGD ("JNI_OnLoad");

  JNIEnv* env;
  jclass* native_class;
  jint reg_res;

  if ((*vm)->GetEnv (vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  native_class = (*env)->FindClass (env, "foss/jonasl/svtplayer/Native");
  reg_res = (*env)->RegisterNatives (env, native_class, &native_methods[0],
      (jint) NELEM (native_methods));
  LOGD ("RegisterNatives result %i", reg_res);
  LOGD ("Path is %s", getcwd(NULL, 0));
  return JNI_VERSION_1_6;
}

