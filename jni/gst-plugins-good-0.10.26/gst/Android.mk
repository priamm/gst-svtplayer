LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

GST_PGOOD_GST := $(LOCAL_PATH)

include $(GST_PGOOD_GST)/rtp/Android.mk
include $(GST_PGOOD_GST)/rtpmanager/Android.mk
include $(GST_PGOOD_GST)/udp/Android.mk
