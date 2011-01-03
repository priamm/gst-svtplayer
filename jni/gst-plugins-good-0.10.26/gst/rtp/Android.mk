LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=   \
#	fnv1hash.c \
	gstrtp.c \
	gstrtpchannels.c \
	gstrtpdepay.c \
	gstrtph264pay.c \
	gstrtpmp4apay.c

LOCAL_SHARED_LIBRARIES :=       \
  gstbase-$(GST_MAJORMINOR)     \
  gstreamer-$(GST_MAJORMINOR)   \
  gstrtp-$(GST_MAJORMINOR)      \
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstrtp

LOCAL_C_INCLUDES := \
  $(GST_PGOOD_TOP)/android

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
