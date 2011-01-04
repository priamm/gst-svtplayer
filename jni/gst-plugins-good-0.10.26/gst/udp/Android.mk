LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=   \
	gstudp-enumtypes.c \
	gstudp-marshal.c   \
	gstudp.c           \
	gstudpsrc.c        \
	gstudpsink.c       \
	gstmultiudpsink.c  \
	gstdynudpsink.c    \
	gstudpnetutils.c

LOCAL_SHARED_LIBRARIES :=       \
  gstbase-$(GST_MAJORMINOR)     \
  gstreamer-$(GST_MAJORMINOR)   \
  gstnetbuffer-$(GST_MAJORMINOR)\
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstudp

LOCAL_C_INCLUDES := \
  $(GST_PGOOD_TOP)/android

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
