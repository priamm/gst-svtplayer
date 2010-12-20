LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(GST_TOP)/gst/Android.mk
#include $(GST_TOP)/libs/gst/base/Android.mk
#include $(GST_TOP)/libs/gst/controller/Android.mk
#include $(GST_TOP)/libs/gst/dataprotocol/Android.mk
#include $(GST_TOP)/libs/gst/net/Android.mk
#include $(GST_TOP)/plugins/elements/Android.mk
#include $(GST_TOP)/plugins/indexers/Android.mk
#include $(GST_TOP)/tools/Android.mk

