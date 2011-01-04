#include <jni.h>
#include <string.h>
#include <unistd.h>

#include <glib.h>
#include <gst/gst.h>

#include "logging.h"
#include "svtplayer.h"

static gboolean bus_call (GstBus *bus, GstMessage *msg, gpointer data);

void
svtp_init (JNIEnv *env, jobject thiz, jint logLevel)
{
  if (!g_thread_supported ()) {
    g_thread_init (NULL);
  }

  g_log_set_handler (NULL, G_LOG_LEVEL_INFO | G_LOG_LEVEL_DEBUG
      | G_LOG_LEVEL_MESSAGE | G_LOG_LEVEL_WARNING | G_LOG_LEVEL_ERROR
      | G_LOG_FLAG_FATAL, svtp_glib_log, NULL);

  gst_debug_set_default_threshold ((GstDebugLevel) logLevel);
  gst_init (NULL, NULL);
  gst_debug_remove_log_function (gst_debug_log_default);
  gst_debug_add_log_function (svtp_gst_log, NULL);

  if (!gst_debug_is_active ()) {
    gst_debug_set_active (TRUE);
  }

  g_debug ("init done");
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
    g_error ("unable to set pipeline to playing state");
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

static gboolean
bus_call (GstBus *bus, GstMessage *msg, gpointer data)
{
  struct svtp_state *state = (struct svtp_state *) data;

  switch (GST_MESSAGE_TYPE (msg)) {
    case GST_MESSAGE_EOS:
      g_debug ("bus_call eos");

      state->error = 0;
      g_main_loop_quit (state->loop);
      break;

    case GST_MESSAGE_ERROR: {
      gchar  *debug;
      GError *error;

      gst_message_parse_error (msg, &error, &debug);      
      g_error ("%s %s", error->message, debug);
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

