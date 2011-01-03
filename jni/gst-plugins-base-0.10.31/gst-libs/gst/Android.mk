LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_PBASE_GSTLIBS := $(LOCAL_PATH)

include $(GST_PBASE_GSTLIBS)/tag/Android.mk
include $(GST_PBASE_GSTLIBS)/rtp/Android.mk
include $(GST_PBASE_GSTLIBS)/rtsp/Android.mk
