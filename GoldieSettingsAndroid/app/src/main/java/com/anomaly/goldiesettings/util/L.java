package com.anomaly.goldiesettings.util;

import android.util.Log;

import com.anomaly.goldiesettings.BuildConfig;

/**
 * Centralised logcat tag, so we can silence / inspect easily.
 *
 * Usage:
 *   adb logcat -s Convai:V ConvaiAudio:V ConvaiWs:V TalkAvatar:V AndroidRuntime:E
 */
public final class L {
    public static final String TAG = "Convai";
    public static final String TAG_WS = "ConvaiWs";
    public static final String TAG_AUDIO = "ConvaiAudio";
    public static final String TAG_AVATAR = "TalkAvatar";
    public static final String TAG_BRIDGE = "ConvaiBridge";

    private L() {}

    public static void d(String tag, String fmt, Object... args) {
        if (BuildConfig.DEBUG) Log.d(tag, fmtStr(fmt, args));
    }

    public static void i(String tag, String fmt, Object... args) {
        Log.i(tag, fmtStr(fmt, args));
    }

    public static void w(String tag, String fmt, Object... args) {
        Log.w(tag, fmtStr(fmt, args));
    }

    public static void e(String tag, String fmt, Object... args) {
        Log.e(tag, fmtStr(fmt, args));
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
    }

    private static String fmtStr(String fmt, Object... args) {
        if (args == null || args.length == 0) return fmt;
        try {
            return String.format(fmt, args);
        } catch (Throwable t) {
            return fmt + " " + android.text.TextUtils.join(",", args);
        }
    }
}
