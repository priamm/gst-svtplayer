#include <android/log.h>
#include <jni.h>

#include <string.h>
#include <glib.h>

#define  LOG_TAG    "libsvtplayer"
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

static void
log_handler (const gchar *log_domain, GLogLevelFlags log_level,
    const gchar *log_message, gpointer user_data);

/* Init */

static void
init (JNIEnv* env)
{
  if (!g_thread_supported ()) {
    g_thread_init (NULL);
    LOGD ("g_thread_init called");
  }

  g_log_set_handler (NULL, G_LOG_LEVEL_INFO | G_LOG_LEVEL_DEBUG | 
      G_LOG_LEVEL_MESSAGE | G_LOG_LEVEL_WARNING | G_LOG_LEVEL_ERROR |
      G_LOG_FLAG_FATAL, log_handler, NULL);
  LOGD ("log_handler set");
  g_debug ("g_debug test");
  LOGD ("init done");    
}

/* JNI */

static void
test (JNIEnv* env)
{
  LOGD ("TEST!");    
}

/* glib log handler */
static void
log_handler (const gchar *log_domain, GLogLevelFlags log_level,
    const gchar *log_message, gpointer user_data)
{
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

/* JNI init */
// TODO: Look up NELEM in Android source
#define NUM_NATIVE_METHODS 1
static JNINativeMethod native_methods[] = {
    { "test", "()V", &test }
};

jint JNI_OnLoad (JavaVM* vm, void* reserved)
{
  LOGD ("JNI_OnLoad");

  JNIEnv* env;
  jclass* native_class;
  jint reg_res;

  if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }
  native_class = (*env)->FindClass (env, "foss/jonasl/svtplayer/Native");
  reg_res = (*env)->RegisterNatives (env, native_class, &native_methods[0],
      (jint) NUM_NATIVE_METHODS); 
  LOGD ("RegisterNatives result %i", reg_res);
  return JNI_VERSION_1_6;
}

