#ifndef SVTPLAYER_H
#define SVTPLAYER_H

#include <jni.h>

#include <glib.h>

struct svtp_state {
   GMainLoop *loop;
   gint error;
};

JavaVM* svtp_vm;

void svtp_init (JNIEnv *env, jobject thiz, jint logLevel);

jboolean svtp_load_plugin (JNIEnv *env, jobject thiz, jstring pluginPath);

jboolean svtp_run_pipeline (JNIEnv *env, jobject thiz, jstring pipelineSpec);

void svtp_run_rtsp_server (JNIEnv *env, jobject thiz, jstring pipelineSpec);

jintArray svtp_rtsp_server_create (JNIEnv *env, jobject thiz);

void svtp_rtsp_server_register (JNIEnv *env, jobject thiz, jint serverHandle,
    jstring path, jstring pipelineSpec);

void svtp_rtsp_server_remove (JNIEnv *env, jobject thiz, jint serverHandle,
    jstring path);

jint svtp_rtsp_server_cleanup (JNIEnv *env, jobject thiz, jint serverHandle);

void svtp_rtsp_server_free (JNIEnv *env, jobject thiz, jint serverHandle,
    jint sourceHandle);

jint svtp_main_loop_create (JNIEnv *env, jobject thiz);

void svtp_main_loop_run (JNIEnv *env, jobject thiz, jint loopHandle);

void svtp_main_loop_free (JNIEnv *env, jobject thiz, jint loopHandle);

#endif
