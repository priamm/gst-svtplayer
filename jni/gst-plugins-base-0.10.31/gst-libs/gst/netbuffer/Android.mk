LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=        \
    gstnetbuffer.c

LOCAL_SHARED_LIBRARIES :=         \
    gstreamer-$(GST_MAJORMINOR)   \
    gstbase-$(GST_MAJORMINOR)     \
    glib-2.0                      \
    gthread-2.0                   \
    gmodule-2.0                   \
    gobject-2.0

LOCAL_MODULE := gstnetbuffer-$(GST_MAJORMINOR)
LOCAL_EXPORT_C_INCLUDES := $(GST_PBASE_TOP)/gst-libs

LOCAL_C_INCLUDES :=         \
   $(GST_PBASE_TOP)/android \
   $(GST_PBASE_TOP)/gst-libs

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
