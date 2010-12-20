LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES += glib-2.0 gthread-2.0
LOCAL_MODULE    := svtplayer
LOCAL_SRC_FILES := svtplayer.c

include $(BUILD_SHARED_LIBRARY)

GLIB_TOP := $(LOCAL_PATH)/glib-2.26.1
GST_TOP  := $(LOCAL_PATH)/gstreamer-0.10.31

include $(GLIB_TOP)/Android.mk
include $(GST_TOP)/Android.mk
