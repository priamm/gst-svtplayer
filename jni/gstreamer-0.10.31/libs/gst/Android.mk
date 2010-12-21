LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_LIBS  := $(LOCAL_PATH)

include $(GST_LIBS)/base/Android.mk
include $(GST_LIBS)/controller/Android.mk
include $(GST_LIBS)/dataprotocol/Android.mk
include $(GST_LIBS)/net/Android.mk
