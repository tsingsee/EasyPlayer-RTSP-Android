package org.easydarwin.easyplayer.util;

import android.content.Context;
import android.preference.PreferenceManager;

public class SPUtil {

    /* ============================ 使用FFmpeg进行视频解码 ============================ */
    private static final String KEY_SW_CODEC = "use-sw-codec";

    public static boolean getswCodec(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_SW_CODEC, false);
    }

    public static void setswCodec(Context context, boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_SW_CODEC, isChecked)
                .apply();
    }

    /* ============================ 开启视频的同时进行录像 ============================ */
    private static final String KEY_AUTO_RECORD = "auto_record";

    public static boolean getAutoRecord(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_AUTO_RECORD, false);
    }

    public static void setAutoRecord(Context context, boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_AUTO_RECORD, isChecked)
                .apply();
    }

    /* ============================ 开启视频的同时进行录像 ============================ */
    private static final String KEY_UDP_MODE = "USE_UDP_MODE";

    public static boolean getUDPMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_UDP_MODE, false);
    }

    public static void setUDPMode(Context context, boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_UDP_MODE, isChecked)
                .apply();
    }

    /* ============================ 自动播放音频 ============================ */
    private static final String KEY_AUTO_AUDIO = "auto_audio";

    public static boolean getAutoAudio(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_AUTO_AUDIO, false);
    }

    public static void setAutoAudio(Context context, boolean isChecked) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_AUTO_AUDIO, isChecked)
                .apply();
    }

}
