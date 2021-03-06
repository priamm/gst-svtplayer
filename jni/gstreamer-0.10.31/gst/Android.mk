LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES :=        \
    gst.c                 \
    gstobject.c           \
    gstbin.c              \
    gstbuffer.c           \
    gstbufferlist.c       \
    gstbus.c              \
    gstcaps.c             \
    gstchildproxy.c       \
    gstclock.c            \
    gstdatetime.c         \
    gstdebugutils.c       \
    gstelement.c          \
    gstelementfactory.c   \
    gsterror.c            \
    gstevent.c            \
    gstfilter.c           \
    gstformat.c           \
    gstghostpad.c         \
    gstindex.c            \
    gstindexfactory.c     \
    gstinfo.c             \
    gstinterface.c        \
    gstiterator.c         \
    gstmessage.c          \
    gstminiobject.c       \
    gstpad.c              \
    gstpadtemplate.c      \
    gstparamspecs.c       \
    gstpipeline.c         \
    gstplugin.c           \
    gstpluginfeature.c    \
    gstpluginloader.c     \
    gstpoll.c             \
    gstpreset.c           \
    gstquark.c            \
    gstquery.c            \
    gstregistry.c         \
    gstregistrychunks.c   \
    gstsegment.c          \
    gststructure.c        \
    gstsystemclock.c      \
    gsttaglist.c          \
    gsttagsetter.c        \
    gsttask.c             \
    gsttaskpool.c         \
    gsttypefind.c         \
    gsttypefindfactory.c  \
    gsturi.c              \
    gstutils.c            \
    gstvalue.c            \
    gstparse.c            \
    ./android/gst/gstenumtypes.c        \
    ./android/gst/gstmarshal.c          \
    ./android/gst/parse/grammar.tab.c   \
    ./android/gst/parse/lex._gst_parse_yy.c

LOCAL_STATIC_LIBRARIES :=  \
    glib-2.0               \
    gthread-2.0            \
    gmodule-2.0            \
    gobject-2.0

LOCAL_MODULE := gstreamer-$(GST_MAJORMINOR)
LOCAL_EXPORT_C_INCLUDES := $(GST_TOP) $(GST_TOP)/gst $(GST_TOP)/gst/android

LOCAL_C_INCLUDES :=                 \
    $(GST_TOP)/android              \
    $(GST_TOP)                      \
    $(GST_TOP)/gst                  \
    $(LOCAL_PATH)/android           \
    $(LOCAL_PATH)/android/gst       \
    $(LOCAL_PATH)/android/gst/parse \
    $(LOCAL_PATH)/parse

LOCAL_CFLAGS := \
    -D_GNU_SOURCE                                 \
    -DG_LOG_DOMAIN=g_log_domain_gstreamer         \
    -DGST_MAJORMINOR=\""$(GST_MAJORMINOR)"\"      \
    -DGST_DISABLE_DEPRECATED                      \
    -DHAVE_CONFIG_H            

include $(BUILD_SHARED_LIBRARY)
