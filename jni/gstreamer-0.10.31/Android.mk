LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_MAJORMINOR := 0.10

include $(GST_TOP)/gst/Android.mk
include $(GST_TOP)/libs/Android.mk
include $(GST_TOP)/plugins/Android.mk
