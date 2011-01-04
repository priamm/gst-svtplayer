LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
	gstrtpbin.c          \
	gstrtpjitterbuffer.c \
	gstrtpmanager.c      \
	gstrtpptdemux.c      \
	gstrtpsession.c      \
	gstrtpssrcdemux.c    \
	rtpjitterbuffer.c    \
	rtpsession.c         \
	rtpsource.c          \
	rtpstats.c           \
	gstrtpbin-marshal.c

LOCAL_SHARED_LIBRARIES :=       \
  gstbase-$(GST_MAJORMINOR)     \
  gstreamer-$(GST_MAJORMINOR)   \
  gstnetbuffer-$(GST_MAJORMINOR)\
  gstrtp-$(GST_MAJORMINOR)      \
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstrtpmanager

LOCAL_C_INCLUDES := \
  $(GST_PGOOD_TOP)/android

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
