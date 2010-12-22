LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
  gstcapsfilter.c      \
  gstelements.c        \
  gstfakesink.c        \
  gstfakesrc.c         \
  gstfdsink.c          \
  gstfdsrc.c           \
  gstfilesink.c        \
  gstfilesrc.c         \
  gstidentity.c        \
  gstmultiqueue.c      \
  gstqueue.c           \
  gstqueue2.c          \
  gsttee.c             \
  gsttypefindelement.c 
  

LOCAL_SHARED_LIBRARIES :=          \
  gstbase-$(GST_MAJORMINOR)     \
  gstreamer-$(GST_MAJORMINOR)   \
  glib-2.0                      \
  gthread-2.0                   \
  gmodule-2.0                   \
  gobject-2.0

LOCAL_MODULE := gstcoreelements

LOCAL_C_INCLUDES := \
  $(GST_TOP)/android

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
