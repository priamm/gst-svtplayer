dnl pkg-config-based checks for GStreamer modules and dependency modules

dnl generic:
dnl AG_GST_PKG_CHECK_MODULES([PREFIX], [WHICH], [REQUIRED])
dnl sets HAVE_[$PREFIX], [$PREFIX]_*
dnl AG_GST_CHECK_MODULES([PREFIX], [MODULE], [MINVER], [NAME], [REQUIRED])
dnl sets HAVE_[$PREFIX], [$PREFIX]_*

dnl specific:
dnl AG_GST_CHECK_GST([MAJMIN], [MINVER], [REQUIRED])
dnl   also sets/ACSUBSTs GST_TOOLS_DIR and GST_PLUGINS_DIR
dnl AG_GST_CHECK_GST_BASE([MAJMIN], [MINVER], [REQUIRED])
dnl AG_GST_CHECK_GST_GDP([MAJMIN], [MINVER], [REQUIRED])
dnl AG_GST_CHECK_GST_CONTROLLER([MAJMIN], [MINVER], [REQUIRED])
dnl AG_GST_CHECK_GST_CHECK([MAJMIN], [MINVER], [REQUIRED])
dnl AG_GST_CHECK_GST_PLUGINS_BASE([MAJMIN], [MINVER], [REQUIRED])
dnl   also sets/ACSUBSTs GSTPB_PLUGINS_DIR

AC_DEFUN([AG_GST_PKG_CHECK_MODULES],
[
  which="[$2]"
  dnl not required by default, since we use this mostly for plugin deps
  required=ifelse([$3], , "no", [$3])

  PKG_CHECK_MODULES([$1], $which,
    [
      HAVE_[$1]="yes"
    ],
    [
      HAVE_[$1]="no"
      AC_MSG_RESULT(no)
      if test "x$required" = "xyes"; then
        AC_MSG_ERROR($[$1]_PKG_ERRORS)
      else
        AC_MSG_NOTICE($[$1]_PKG_ERRORS)
      fi
    ])

  dnl AC_SUBST of CFLAGS and LIBS was not done before automake 1.7
  dnl It gets done automatically in automake >= 1.7, which we now require
]))

AC_DEFUN([AG_GST_CHECK_MODULES],
[
  module=[$2]
  minver=[$3]
  name="[$4]"
  required=ifelse([$5], , "yes", [$5]) dnl required by default

  PKG_CHECK_MODULES([$1], $module >= $minver,
    [
      HAVE_[$1]="yes"
    ],
    [
      HAVE_[$1]="no"
      AC_MSG_RESULT(no)
      AC_MSG_NOTICE($[$1]_PKG_ERRORS)
      if test "x$required" = "xyes"; then
        AC_MSG_ERROR([no $module >= $minver ($name) found])
      else
        AC_MSG_NOTICE([no $module >= $minver ($name) found])
      fi
    ])

  dnl AC_SUBST of CFLAGS and LIBS was not done before automake 1.7
  dnl It gets done automatically in automake >= 1.7, which we now require
]))

AC_DEFUN([AG_GST_CHECK_GST],
[
  AG_GST_CHECK_MODULES(GST, gstreamer-[$1], [$2], [GStreamer], [$3])
  dnl allow setting before calling this macro to override
  if test -z $GST_TOOLS_DIR; then
    GST_TOOLS_DIR=`$PKG_CONFIG --variable=toolsdir gstreamer-[$1]`
    if test -z $GST_TOOLS_DIR; then
      AC_MSG_ERROR(
        [no tools dir set in GStreamer pkg-config file, core upgrade needed.])
    fi
  fi
  AC_MSG_NOTICE([using GStreamer tools in $GST_TOOLS_DIR])
  AC_SUBST(GST_TOOLS_DIR)

  dnl check for where core plug-ins got installed
  dnl this is used for unit tests
  dnl allow setting before calling this macro to override
  if test -z $GST_PLUGINS_DIR; then
    GST_PLUGINS_DIR=`$PKG_CONFIG --variable=pluginsdir gstreamer-[$1]`
    if test -z $GST_PLUGINS_DIR; then
      AC_MSG_ERROR(
        [no pluginsdir set in GStreamer pkg-config file, core upgrade needed.])
    fi
  fi
  AC_MSG_NOTICE([using GStreamer plug-ins in $GST_PLUGINS_DIR])
  AC_SUBST(GST_PLUGINS_DIR)
])

AC_DEFUN([AG_GST_CHECK_GST_BASE],
[
  AG_GST_CHECK_MODULES(GST_BASE, gstreamer-base-[$1], [$2],
    [GStreamer Base Libraries], [$3])
])

AC_DEFUN([AG_GST_CHECK_GST_GDP],
[
  AG_GST_CHECK_MODULES(GST_GDP, gstreamer-dataprotocol-[$1], [$2],
    [GStreamer Data Protocol Library], [$3])
])

AC_DEFUN([AG_GST_CHECK_GST_CONTROLLER],
[
  AG_GST_CHECK_MODULES(GST_CONTROLLER, gstreamer-controller-[$1], [$2],
    [GStreamer Controller Library], [$3])
])

AC_DEFUN([AG_GST_CHECK_GST_CHECK],
[
  AG_GST_CHECK_MODULES(GST_CHECK, gstreamer-check-[$1], [$2],
    [GStreamer Check unittest Library], [$3])
])

AC_DEFUN([AG_GST_CHECK_GST_PLUGINS_BASE],
[
  AG_GST_CHECK_MODULES(GST_PLUGINS_BASE, gstreamer-plugins-base-[$1], [$2],
    [GStreamer Base Plugins], [$3])

  dnl check for where base plugins got installed
  dnl this is used for unit tests
  dnl allow setting before calling this macro to override
  if test -z $GSTPB_PLUGINS_DIR; then
    GSTPB_PLUGINS_DIR=`$PKG_CONFIG --variable=pluginsdir gstreamer-plugins-base-[$1]`
    if test -z $GSTPB_PLUGINS_DIR; then
      AC_MSG_ERROR(
        [no pluginsdir set in GStreamer Base Plugins pkg-config file])
    fi
  fi
  AC_MSG_NOTICE([using GStreamer Base Plugins in $GSTPB_PLUGINS_DIR])
  AC_SUBST(GSTPB_PLUGINS_DIR)
])

AC_DEFUN([AG_GST_CHECK_GST_PLUGINS_GOOD],
[
  AG_GST_CHECK_MODULES(GST_PLUGINS_GOOD, gstreamer-plugins-good-[$1], [$2],
    [GStreamer Good Plugins], [$3])

  dnl check for where good plugins got installed
  dnl this is used for unit tests
  dnl allow setting before calling this macro to override
  if test -z $GST_PLUGINS_GOOD_DIR; then
    GST_PLUGINS_GOOD_DIR=`$PKG_CONFIG --variable=pluginsdir gstreamer-plugins-good-[$1]`
    if test -z $GST_PLUGINS_GOOD_DIR; then
      AC_MSG_ERROR([no pluginsdir set in GStreamer Good Plugins pkg-config file])
    fi
  fi
  AC_MSG_NOTICE([using GStreamer Good Plugins in $GST_PLUGINS_GOOD_DIR])
  GST_PLUGINS_GOOD_DIR="$GST_PLUGINS_GOOD_DIR/gst:$GST_PLUGINS_GOOD_DIR/sys:$GST_PLUGINS_GOOD_DIR/ext"
  AC_SUBST(GST_PLUGINS_GOOD_DIR)
])

AC_DEFUN([AG_GST_CHECK_GST_PLUGINS_UGLY],
[
  AG_GST_CHECK_MODULES(GST_PLUGINS_UGLY, gstreamer-plugins-ugly-[$1], [$2],
    [GStreamer Ugly Plugins], [$3])

  dnl check for where ugly plugins got installed
  dnl this is used for unit tests
  dnl allow setting before calling this macro to override
  if test -z $GST_PLUGINS_UGLY_DIR; then
    GST_PLUGINS_UGLY_DIR=`$PKG_CONFIG --variable=pluginsdir gstreamer-plugins-ugly-[$1]`
    if test -z $GST_PLUGINS_UGLY_DIR; then
      AC_MSG_ERROR([no pluginsdir set in GStreamer Ugly Plugins pkg-config file])
    fi
  fi
  AC_MSG_NOTICE([using GStreamer Ugly Plugins in $GST_PLUGINS_UGLY_DIR])
  GST_PLUGINS_UGLY_DIR="$GST_PLUGINS_UGLY_DIR/gst:$GST_PLUGINS_UGLY_DIR/sys:$GST_PLUGINS_UGLY_DIR/ext"
  AC_SUBST(GST_PLUGINS_UGLY_DIR)
])

AC_DEFUN([AG_GST_CHECK_GST_PLUGINS_BAD],
[
  AG_GST_CHECK_MODULES(GST_PLUGINS_BAD, gstreamer-plugins-bad-[$1], [$2],
    [GStreamer Bad Plugins], [$3])

  dnl check for where bad plugins got installed
  dnl this is used for unit tests
  dnl allow setting before calling this macro to override
  if test -z $GST_PLUGINS_BAD_DIR; then
    GST_PLUGINS_BAD_DIR=`$PKG_CONFIG --variable=pluginsdir gstreamer-plugins-bad-[$1]`
    if test -z $GST_PLUGINS_BAD_DIR; then
      AC_MSG_ERROR([no pluginsdir set in GStreamer Bad Plugins pkg-config file])
    fi
  fi
  AC_MSG_NOTICE([using GStreamer Bad Plugins in $GST_PLUGINS_BAD_DIR])
  GST_PLUGINS_BAD_DIR="$GST_PLUGINS_BAD_DIR/gst:$GST_PLUGINS_BAD_DIR/sys:$GST_PLUGINS_BAD_DIR/ext"
  AC_SUBST(GST_PLUGINS_BAD_DIR)
])

AC_DEFUN([AG_GST_CHECK_GST_PLUGINS_FFMPEG],
[
  AG_GST_CHECK_MODULES(GST_PLUGINS_FFMPEG, gstreamer-plugins-ffmpeg-[$1], [$2],
    [GStreamer FFmpeg Plugins], [$3])

  dnl check for where ffmpeg plugins got installed
  dnl this is used for unit tests
  dnl allow setting before calling this macro to override
  if test -z $GST_PLUGINS_FFMPEG_DIR; then
    GST_PLUGINS_FFMPEG_DIR=`$PKG_CONFIG --variable=pluginsdir gstreamer-plugins-ffmpeg-[$1]`
    if test -z $GST_PLUGINS_FFMPEG_DIR; then
      AC_MSG_ERROR([no pluginsdir set in GStreamer FFmpeg Plugins pkg-config file])
    fi
  fi
  GST_PLUGINS_FFMPEG_DIR="$GST_PLUGINS_FFMPEG_DIR/ext/ffmpeg"
  AC_MSG_NOTICE([using GStreamer FFmpeg Plugins in $GST_PLUGINS_FFMPEG_DIR])
  AC_SUBST(GST_PLUGINS_FFMPEG_DIR)
])
