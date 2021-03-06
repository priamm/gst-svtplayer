#include <jni.h>
#include <string.h>
#include <unistd.h>

#include <glib.h>
#include <gst/gst.h>
#include <gst/rtsp-server/rtsp-server.h>

#include "gstsvtpsrc.h"
#include "logging.h"
#include "svtplayer.h"

static gboolean bus_call (GstBus *bus, GstMessage *msg, gpointer data);

static gboolean
init_static (GstPlugin *plugin)
{
  return gst_element_register (plugin, "svtpsrc", GST_RANK_PRIMARY,
      GST_TYPE_SVTP_SRC);
}

void
svtp_init (JNIEnv *env, jobject thiz, jint logLevel)
{
  if (!g_thread_supported ()) {
    g_thread_init (NULL);
  }

  g_log_set_handler (NULL, G_LOG_LEVEL_INFO | G_LOG_LEVEL_DEBUG
      | G_LOG_LEVEL_MESSAGE | G_LOG_LEVEL_WARNING | G_LOG_LEVEL_ERROR
      | G_LOG_FLAG_FATAL, svtp_glib_log, NULL);

  if ((GstDebugLevel) logLevel > gst_debug_get_default_threshold ()) {
    gst_debug_set_default_threshold ((GstDebugLevel) logLevel);
  }
  gst_init (NULL, NULL);
  gst_debug_remove_log_function (gst_debug_log_default);
  gst_debug_add_log_function (svtp_gst_log, NULL);

  // TODO: Expose via JNI
  gst_debug_set_threshold_for_name ("mpegtsdemux", GST_LEVEL_NONE);

  if (!gst_element_register (NULL, "svtpsrc", GST_RANK_NONE,
      GST_TYPE_SVTP_SRC)) {
      g_error ("could not register svtpsrc");
  }

  g_debug ("init done");
}

void
svtp_set_log_level (JNIEnv *env, jobject thiz, jstring category, jint logLevel)
{
  const jbyte *cat = (*env)->GetStringUTFChars (env, category, NULL);
  if (cat == NULL) {
    g_error ("category null");
    return;
  }
  gst_debug_set_threshold_for_name (cat, (GstDebugLevel) logLevel);
  (*env)->ReleaseStringUTFChars (env, category, cat);
}

jboolean
svtp_load_plugin (JNIEnv *env, jobject thiz, jstring pluginPath)
{
  GstPlugin *plugin;
  GError *err = NULL;
  jboolean ret;
  const jbyte *path = (*env)->GetStringUTFChars (env, pluginPath, NULL);
  if (path == NULL) {
    g_error ("pluginPath null");
    return JNI_FALSE;
  } else {
    plugin = gst_plugin_load_file (path, &err);
    if (plugin == NULL) {
      g_error ("Loading %s failed: %s", path, err->message);
      g_error_free (err);
      ret = JNI_FALSE;
    } else {
      g_debug ("loaded: %s", gst_plugin_get_description (plugin));
      gst_object_unref (plugin);
      ret = JNI_TRUE;
    }
    (*env)->ReleaseStringUTFChars (env, pluginPath, path);
    return ret;
  }
}

jboolean
svtp_run_pipeline (JNIEnv *env, jobject thiz, jstring pipelineSpec)
{
  jboolean ret;
  const jbyte *spec = (*env)->GetStringUTFChars (env, pipelineSpec, NULL);
  if (spec == NULL) {
    g_error ("pipelineSpec null");
    return JNI_FALSE;
  }

  GstElement *pipeline;
  GstBus *bus;
  struct svtp_state state;
  GstStateChangeReturn sret;
  
  pipeline = NULL;
  ret = JNI_FALSE;

  g_debug ("creating pipeline");
  pipeline = gst_parse_launch (spec, NULL);
  (*env)->ReleaseStringUTFChars (env, pipelineSpec, spec);
  if (pipeline == NULL) {
    g_error ("could not parse/create pipeline");
    return ret;
  }
  state.error = -1;
  state.loop = g_main_loop_new (NULL, FALSE);
  bus = gst_pipeline_get_bus (GST_PIPELINE (pipeline));
  gst_bus_add_watch (bus, bus_call, &state);
  gst_object_unref (bus);

  g_debug ("setting to playing state");
  sret = gst_element_set_state (pipeline, GST_STATE_PLAYING);
  if (sret == GST_STATE_CHANGE_FAILURE) {
    LOGE ("unable to set pipeline to playing state");
    goto cleanup;
  }

  g_debug ("running pipeline");
  g_main_loop_run (state.loop);

  g_debug ("pipeline stopped, state.error=%i", state.error);
  if (state.error == 0) {
    ret = JNI_TRUE;
  }

  gst_element_set_state (pipeline, GST_STATE_NULL);

cleanup:
  gst_object_unref (GST_OBJECT (pipeline));
  g_main_loop_unref (state.loop);

  return ret;   
}

jintArray 
svtp_rtsp_server_create (JNIEnv *env, jobject thiz)
{
  FUNCLOG;

  GstRTSPServer *server;
  jintArray result;
  jint localResult[2];

  result = (*env)->NewIntArray(env, 2);
  if (result == NULL) {
    return NULL; /* out of memory error thrown */
  }

  server = gst_rtsp_server_new ();
  localResult[0] = (jint) server;
  localResult[1] = gst_rtsp_server_attach (server, NULL);

  (*env)->SetIntArrayRegion(env, result, 0, 2, localResult);
  return result;
}

void
svtp_rtsp_server_register (JNIEnv *env, jobject thiz, jint serverHandle,
    jstring path, jstring pipelineSpec)
{
  const jbyte *spec = (*env)->GetStringUTFChars (env, pipelineSpec, NULL);
  if (spec == NULL) {
    g_error ("pipelineSpec null");
    goto cleanup;
  }
  const jbyte *p = (*env)->GetStringUTFChars (env, path, NULL);
  if (p == NULL) {
    g_error ("path null");
    goto cleanup;
  }

  GstRTSPServer *server;
  GstRTSPMediaMapping *mapping;
  GstRTSPMediaFactory *factory;

  server = (GstRTSPServer *) serverHandle;
  mapping = gst_rtsp_server_get_media_mapping (server);
  factory = gst_rtsp_media_factory_new ();
  gst_rtsp_media_factory_set_launch (factory, spec);
  gst_rtsp_media_mapping_add_factory (mapping, p, factory);
  g_object_unref (mapping);

cleanup:
  if (spec) {
    (*env)->ReleaseStringUTFChars (env, pipelineSpec, spec);
  }
  if (p) {
    (*env)->ReleaseStringUTFChars (env, path, p);
  }
}

void
svtp_rtsp_server_remove (JNIEnv *env, jobject thiz, jint serverHandle,
    jstring path)
{
  const jbyte *p = (*env)->GetStringUTFChars (env, path, NULL);
  if (p == NULL) {
    g_error ("path null");
    goto cleanup;
  }

  GstRTSPServer *server;
  GstRTSPMediaMapping *mapping;
  GstRTSPMediaFactory *factory;

  server = (GstRTSPServer *) serverHandle;
  mapping = gst_rtsp_server_get_media_mapping (server);
  gst_rtsp_media_mapping_remove_factory (mapping, p);
  g_object_unref (mapping);

cleanup:
  if (p) {
    (*env)->ReleaseStringUTFChars (env, path, p);
  }
}

jint
svtp_rtsp_server_cleanup (JNIEnv *env, jobject thiz, jint serverHandle)
{
  FUNCLOG;

  GstRTSPServer *server;
  GstRTSPSessionPool *pool;
  jint result;

  result = 0;
  server = (GstRTSPServer *) serverHandle;
  pool = gst_rtsp_server_get_session_pool (server);
  if (pool) {
    gst_rtsp_session_pool_cleanup (pool);
    result = gst_rtsp_session_pool_get_n_sessions (pool);
    g_object_unref (pool);
  }

  return result;
}

void
svtp_rtsp_server_free (JNIEnv *env, jobject thiz, jint serverHandle,
    jint sourceHandle)
{
  FUNCLOG;

  GstRTSPServer *server;

  server = (GstRTSPServer *) serverHandle;

  g_source_remove (sourceHandle);
  g_object_unref (server);
}

jint
svtp_main_loop_create (JNIEnv *env, jobject thiz)
{
  FUNCLOG;

  GMainLoop *loop;

  loop = g_main_loop_new (NULL, FALSE);
  
  return (jint) loop;
}

void
svtp_main_loop_run (JNIEnv *env, jobject thiz, jint loopHandle)
{
  FUNCLOG;

  GMainLoop *loop;

  loop = (GMainLoop *) loopHandle;
  g_main_loop_run (loop);
}

void
svtp_main_loop_free (JNIEnv *env, jobject thiz, jint loopHandle)
{
  FUNCLOG;

  GMainLoop *loop;

  loop = (GMainLoop *) loopHandle;
  g_main_loop_quit (loop);
  g_main_loop_unref (loop);
}

void
svtp_run_rtsp_server (JNIEnv *env, jobject thiz, jstring pipelineSpec)
{
  const jbyte *spec = (*env)->GetStringUTFChars (env, pipelineSpec, NULL);
  if (spec == NULL) {
    g_error ("pipelineSpec null");
    return;
  }

  GMainLoop *loop;
  GstRTSPServer *server;
  GstRTSPMediaMapping *mapping;
  GstRTSPMediaFactory *factory;

  loop = g_main_loop_new (NULL, FALSE);

  /* create a server instance */
  server = gst_rtsp_server_new ();

  /* get the mapping for this server, every server has a default mapper object
   * that be used to map uri mount points to media factories */
  mapping = gst_rtsp_server_get_media_mapping (server);

  /* make a media factory for a test stream. The default media factory can use
   * gst-launch syntax to create pipelines. 
   * any launch line works as long as it contains elements named pay%d. Each
   * element with pay%d names will be a stream */
  factory = gst_rtsp_media_factory_new ();
  gst_rtsp_media_factory_set_launch (factory, spec);

  /* attach the test factory to the /test url */
  gst_rtsp_media_mapping_add_factory (mapping, "/test", factory);

  /* don't need the ref to the mapper anymore */
  g_object_unref (mapping);

  /* attach the server to the default maincontext */
  gst_rtsp_server_attach (server, NULL);

  /* start serving */
  g_main_loop_run (loop);
}

static gboolean
bus_call (GstBus *bus, GstMessage *msg, gpointer data)
{
  struct svtp_state *state = (struct svtp_state *) data;

  switch (GST_MESSAGE_TYPE (msg)) {
    case GST_MESSAGE_EOS:
      g_debug ("bus_call: eos");

      state->error = 0;
      g_main_loop_quit (state->loop);
      break;

    case GST_MESSAGE_ERROR: {
      gchar  *debug;
      GError *error;

      gst_message_parse_error (msg, &error, &debug);      
      LOGE ("bus_call: %s %s", error->message, debug);
      g_error_free (error);
      g_free (debug);

      state->error = 1;
      g_main_loop_quit (state->loop);
      break;
    }
    default:
      break;
  }

  return TRUE;
}

