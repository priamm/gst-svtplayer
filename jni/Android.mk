LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES += glib-2.0 gthread-2.0
LOCAL_MODULE    := svtplayer
LOCAL_SRC_FILES := svtplayer.c

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

GLIB_TOP := $(LOCAL_PATH)/glib

include $(GLIB_TOP)/Android.mk
