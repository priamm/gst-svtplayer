LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
  gstaacparse.c        \
  gstbaseparse.c       \
  android/plugin.c

LOCAL_SHARED_LIBRARIES :=          \
  libgstbase-$(GST_MAJORMINOR)     \
  libgstreamer-$(GST_MAJORMINOR)   \
  libglib-2.0                      \
  libgthread-2.0                   \
  libgmodule-2.0                   \
  libgobject-2.0

LOCAL_MODULE := gstaudioparsersbad

LOCAL_C_INCLUDES := \
  $(GST_PBAD_TOP)/android

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
