LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := gthread-impl.c         
LOCAL_SHARED_LIBRARIES := glib-2.0
LOCAL_MODULE:= gthread-2.0

LOCAL_EXPORT_C_INCLUDES := $(GLIB_TOP) $(GLIB_TOP)/glib $(GLIB_TOP)/gthread

LOCAL_C_INCLUDES := \
	  $(GLIB_TOP)/android

LOCAL_CFLAGS += \
    -DG_LOG_DOMAIN=\"GThread\"      \
    -D_POSIX4_DRAFT_SOURCE          \
    -D_POSIX4A_DRAFT10_SOURCE       \
    -U_OSF_SOURCE                   \
    -DG_DISABLE_DEPRECATED 

include $(BUILD_SHARED_LIBRARY)
