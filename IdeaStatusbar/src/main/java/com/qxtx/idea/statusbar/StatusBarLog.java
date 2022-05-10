package com.qxtx.idea.statusbar;

import android.util.Log;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/7/27 14:36
 * <p><b>Description</b></p>
 * //LYX_TAG 2021/7/27 14:37 调试用日志打印，发布正式版本需屏蔽日志打印
 */
public class StatusBarLog {

    private static boolean isDebug = BuildConfig.DEBUG;

    private static String tag = StatusBarLog.class.getSimpleName();

    /**
     * Sets debug.
     *
     * @param enable the enable
     * @param tag    the tag
     */
    public static void setDebug(boolean enable, String tag) {
        isDebug = enable;
        StatusBarLog.tag = tag;
    }

    /**
     * Is debug enable boolean.
     *
     * @return the boolean
     */
    public static boolean isDebugEnable() {
        return isDebug;
    }

    /**
     * D.
     *
     * @param msg the msg
     */
    public static void d(String msg) {
        if (isDebug) {
             Log.d(tag, msg);
        }
    }

    /**
     * .
     *
     * @param msg the msg
     */
    public static void i(String msg) {
        if (isDebug) {
            Log.i(tag, msg);
        }
    }

    /**
     * E.
     *
     * @param msg the msg
     */
    public static void e(String msg) {
        Log.e(tag, msg);
    }

    /**
     * W.
     *
     * @param msg the msg
     */
    public static void w(String msg) {
        Log.w(tag, msg);
    }
}
