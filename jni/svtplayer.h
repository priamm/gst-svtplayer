#ifndef SVTPLAYER_H
#define SVTPLAYER_H

#include <jni.h>

#include <glib.h>

struct svtp_state {
   GMainLoop *loop;
   gint error;
};

void svtp_init (JNIEnv *env, jobject thiz, jint logLevel);

jboolean svtp_load_plugin (JNIEnv *env, jobject thiz, jstring pluginPath);

jboolean svtp_run_pipeline (JNIEnv *env, jobject thiz, jstring pipelineSpec);

#endif
