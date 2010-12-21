LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=     \
    gstcontroller.c    \
    gstcontrolsource.c \
    gsthelper.c        \
    gstinterpolation.c \
    gstinterpolationcontrolsource.c \
    gstlfocontrolsource.c \
    lib.c

LOCAL_SHARED_LIBRARIES :=       \
    gstreamer-$(GST_MAJORMINOR) \
    glib-2.0                    \
    gthread-2.0                 \
    gmodule-2.0                 \
    gobject-2.0

LOCAL_MODULE := gstcontroller-$(GST_MAJORMINOR)
LOCAL_EXPORT_C_INCLUDES := $(GST_TOP)/libs

LOCAL_CFLAGS := \
    -I$(GST_TOP)/libs

LOCAL_CFLAGS += \
  -DHAVE_CONFIG_H      

include $(BUILD_SHARED_LIBRARY)
