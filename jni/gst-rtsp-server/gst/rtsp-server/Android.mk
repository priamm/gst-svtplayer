LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=         \
  rtsp-params.c            \
  rtsp-sdp.c               \
  rtsp-media.c             \
  rtsp-media-factory.c     \
  rtsp-media-factory-uri.c \
  rtsp-media-mapping.c     \
  rtsp-session.c           \
  rtsp-session-pool.c      \
  rtsp-client.c            \
  rtsp-server.c

LOCAL_SHARED_LIBRARIES :=       \
  gstbase-$(GST_MAJORMINOR)     \
  gstreamer-$(GST_MAJORMINOR)   \
  gstrtp-$(GST_MAJORMINOR)      \
  gstrtsp-$(GST_MAJORMINOR)     \
  gstsdp-$(GST_MAJORMINOR)      \
  gstapp-$(GST_MAJORMINOR)      \
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstrtspserver-$(GST_MAJORMINOR)

LOCAL_EXPORT_C_INCLUDES := $(GST_RTSPSERVER_TOP)

LOCAL_C_INCLUDES :=

LOCAL_CFLAGS :=  

include $(BUILD_SHARED_LIBRARY)
