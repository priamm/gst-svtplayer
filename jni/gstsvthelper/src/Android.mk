LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
  gstsvthelper.c       \
  gsth264filter.c      \
  gstaacfilter.c

LOCAL_SHARED_LIBRARIES :=          \
  libgstreamer-$(GST_MAJORMINOR)   \
  libglib-2.0                      \
  libgthread-2.0                   \
  libgmodule-2.0                   \
  libgobject-2.0

LOCAL_MODULE := gstsvthelper

include $(BUILD_SHARED_LIBRARY)
