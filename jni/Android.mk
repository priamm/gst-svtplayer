LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_MAJORMINOR := 0.10

LOCAL_SHARED_LIBRARIES += glib-2.0 gthread-2.0 gobject-2.0 gstreamer-$(GST_MAJORMINOR)
LOCAL_MODULE    := svtplayer
LOCAL_SRC_FILES := svtplayer.c
LOCAL_CFLAGS    := -Werror
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)

GLIB_TOP := $(LOCAL_PATH)/glib-2.26.1
GST_TOP  := $(LOCAL_PATH)/gstreamer-0.10.31

include $(GLIB_TOP)/Android.mk
include $(GST_TOP)/Android.mk
