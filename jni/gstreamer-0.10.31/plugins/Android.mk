LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_PLUGINS  := $(LOCAL_PATH)

include $(GST_PLUGINS)/elements/Android.mk
