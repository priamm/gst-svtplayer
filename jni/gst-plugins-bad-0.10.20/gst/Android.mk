LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_PBAD_GST := $(LOCAL_PATH)

include $(GST_PBAD_GST)/audioparsers/Android.mk
include $(GST_PBAD_GST)/h264parse/Android.mk
include $(GST_PBAD_GST)/mpegdemux/Android.mk
include $(GST_PBAD_GST)/qtmux/Android.mk
