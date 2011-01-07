#include "logging.h"
#include "svtplayer.h"

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

static JNINativeMethod native_methods[] = {
  { "initNative", "(I)V", &svtp_init},
  { "loadPlugin", "(Ljava/lang/String;)Z", &svtp_load_plugin},
  { "runPipeline", "(Ljava/lang/String;)Z", &svtp_run_pipeline},
  { "rtspServerCreate", "()[I", &svtp_rtsp_server_create},
  { "rtspServerRegister", "(ILjava/lang/String;Ljava/lang/String;)V",
      &svtp_rtsp_server_register},
  { "rtspServerRemove", "(ILjava/lang/String;)V", &svtp_rtsp_server_remove},
  { "rtspServerFree", "(II)V", &svtp_rtsp_server_free},
  { "mainLoopCreate", "()I", &svtp_main_loop_create},
  { "mainLoopRun", "(I)V", &svtp_main_loop_run},
  { "mainLoopFree", "(I)V", &svtp_main_loop_free}
};

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
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
  return JNI_VERSION_1_6;
}
