LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
  gstsvtpsrc.c         \
  gstsvthelper.c       \
  gsth264filter.c      \
  gstaacfilter.c

LOCAL_SHARED_LIBRARIES :=       \
  gstreamer-$(GST_MAJORMINOR)   \
  gstbase-$(GST_MAJORMINOR)     \
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstsvthelper

LOCAL_CFLAGS := \
    -DANDROID=1

include $(BUILD_SHARED_LIBRARY)
