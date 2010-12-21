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
  libgstbase-$(GST_MAJORMINOR)     \
  libgstreamer-$(GST_MAJORMINOR)   \
  libglib-2.0                      \
  libgthread-2.0                   \
  libgmodule-2.0                   \
  libgobject-2.0

LOCAL_MODULE := gstcoreelements-$(GST_MAJORMINOR)

LOCAL_C_INCLUDES :=

LOCAL_CFLAGS := \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
