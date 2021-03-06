LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=    \
  gstqtmux.c          \
  gstqtmoovrecover.c  \
  gstqtmuxplugin.c    \
  atoms.c             \
  atomsrecovery.c     \
  descriptors.c       \
  properties.c        \
  gstqtmuxmap.c

LOCAL_SHARED_LIBRARIES :=       \
  gstbase-$(GST_MAJORMINOR)     \
  gstreamer-$(GST_MAJORMINOR)   \
  gsttag-$(GST_MAJORMINOR)      \
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstqtmux

LOCAL_C_INCLUDES := \
  $(GST_PBAD_TOP)/android

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
