LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_MAJORMINOR := 0.10

LOCAL_SHARED_LIBRARIES :=         \
  glib-2.0                        \
  gthread-2.0                     \
  gobject-2.0                     \
  gstreamer-$(GST_MAJORMINOR)     \
  gstbase-$(GST_MAJORMINOR)       \
  gstrtp-$(GST_MAJORMINOR)        \
  gstrtsp-$(GST_MAJORMINOR)       \
  gstrtspserver-$(GST_MAJORMINOR) \
  gstsdp-$(GST_MAJORMINOR)        \
  gstapp-$(GST_MAJORMINOR)

LOCAL_MODULE    := svtplayer
LOCAL_SRC_FILES := svtplayer.c logging.c jni.c gstsvtpsrc.c
LOCAL_CFLAGS    := -Werror
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)

GLIB_TOP            := $(LOCAL_PATH)/glib-2.26.1
GST_TOP             := $(LOCAL_PATH)/gstreamer-0.10.31
GST_PBASE_TOP       := $(LOCAL_PATH)/gst-plugins-base-0.10.31
GST_PGOOD_TOP       := $(LOCAL_PATH)/gst-plugins-good-0.10.26
GST_PBAD_TOP        := $(LOCAL_PATH)/gst-plugins-bad-0.10.20
GST_RTSPSERVER_TOP  := $(LOCAL_PATH)/gst-rtsp-server
GST_SVTH_TOP        := $(LOCAL_PATH)/gstsvthelper

include $(GLIB_TOP)/Android.mk
include $(GST_TOP)/Android.mk
include $(GST_PBASE_TOP)/Android.mk
include $(GST_PGOOD_TOP)/Android.mk
include $(GST_PBAD_TOP)/Android.mk
include $(GST_RTSPSERVER_TOP)/Android.mk
include $(GST_SVTH_TOP)/Android.mk
