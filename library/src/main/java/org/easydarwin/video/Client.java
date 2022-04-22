package org.easydarwin.video;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.SparseArray;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by John on 2016/3/12.
 */
public class Client implements Closeable {

    private static int sKey;
    private static Context mContext;
    private volatile int paused = 0;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static Set<Integer> _channelPause = new HashSet<>();
    private final Runnable closeTask = new Runnable() {
        @Override
        public void run() {
            if (paused > 0) {
                Log.i(TAG, "realPause! close stream");
                closeStream();
                paused = 2;
            }
        }
    };

    private int _channel;
    private String _url;
    private int _type;
    private int _mediaType;
    private String _user;
    private String _pwd;
    private int _sendOption;

    public static final class FrameInfo {
        public int codec;           /* 音视频格式 */

        public int type;            /* 视频帧类型 */
        public byte fps;            /* 视频帧率 */
        public short width;         /* 视频宽 */
        public short height;        /* 视频高 */

        public int reserved1;       /* 保留参数1 */
        public int reserved2;       /* 保留参数2 */

        public int sample_rate;     /* 音频采样率 */
        public int channels;        /* 音频声道数 */
        public int bits_per_sample; /* 音频采样精度 */

        public int length;          /* 音视频帧大小 */
        public long timestamp_usec; /* 时间戳,微妙 */
        public long timestamp_sec;  /* 时间戳 秒 */

        public long stamp;

        public float bitrate;       /* 比特率 */
        public float losspacket;    /* 丢包率 */

        public byte[] buffer;
        public int offset = 0;
        public boolean audio;
    }

    public static final class MediaInfo {
//        Easy_U32 u32VideoCodec;				/*  ”∆µ±‡¬Î¿‡–Õ */
//        Easy_U32 u32VideoFps;				/*  ”∆µ÷°¬  */
//
//        Easy_U32 u32AudioCodec;				/* “Ù∆µ±‡¬Î¿‡–Õ */
//        Easy_U32 u32AudioSamplerate;		/* “Ù∆µ≤…—˘¬  */
//        Easy_U32 u32AudioChannel;			/* “Ù∆µÕ®µ¿ ˝ */
//        Easy_U32 u32AudioBitsPerSample;		/* “Ù∆µ≤…—˘æ´∂» */
//
//        Easy_U32 u32H264SpsLength;			/*  ”∆µsps÷°≥§∂» */
//        Easy_U32 u32H264PpsLength;			/*  ”∆µpps÷°≥§∂» */
//        Easy_U8	 u8H264Sps[128];			/*  ”∆µsps÷°ƒ⁄»› */
//        Easy_U8	 u8H264Pps[36];				/*  ”∆µsps÷°ƒ⁄»› */

        int videoCodec;
        int fps;
        int audioCodec;
        int sample;
        int channel;
        int bitPerSample;
        int spsLen;
        int ppsLen;
        byte[] sps;
        byte[] pps;

        @Override
        public String toString() {
            return "MediaInfo{" +
                    "videoCodec=" + videoCodec +
                    ", fps=" + fps +
                    ", audioCodec=" + audioCodec +
                    ", sample=" + sample +
                    ", channel=" + channel +
                    ", bitPerSample=" + bitPerSample +
                    ", spsLen=" + spsLen +
                    ", ppsLen=" + ppsLen +
                    '}';
        }
    }

    public interface SourceCallBack {
        void onSourceCallBack(int _channelId, int _channelPtr, int _frameType, FrameInfo frameInfo);

        void onMediaInfoCallBack(int _channelId, MediaInfo mi);

        void onEvent(int _channelId, int err, int info);
    }

    public static final int EASY_SDK_VIDEO_FRAME_FLAG = 0x01;
    public static final int EASY_SDK_AUDIO_FRAME_FLAG = 0x02;
    public static final int EASY_SDK_EVENT_FRAME_FLAG = 0x04;
    public static final int EASY_SDK_RTP_FRAME_FLAG = 0x08;        /* RTP帧标志 */
    public static final int EASY_SDK_SDP_FRAME_FLAG = 0x10;        /* SDP帧标志 */
    public static final int EASY_SDK_MEDIA_INFO_FLAG = 0x20;        /* 媒体类型标志*/

    public static final int EASY_SDK_EVENT_CODEC_ERROR = 0x63657272;    /* ERROR */
    public static final int EASY_SDK_EVENT_CODEC_EXIT = 0x65786974;    /* EXIT */

    public static final int TRANSTYPE_TCP = 1;
    public static final int TRANSTYPE_UDP = 2;
    private static final String TAG = Client.class.getSimpleName();

    static {
        System.loadLibrary("EasyRTSPClient");
    }

    private long mCtx;
    private static final SparseArray<SourceCallBack> sCallbacks = new SparseArray<>();

    Client(Context context) {
        if (context == null) {
            throw new NullPointerException();
        }

        mCtx = init(context, "");
        mContext = context.getApplicationContext();
    }

    int registerCallback(SourceCallBack cb) {
        synchronized (sCallbacks) {
            sCallbacks.put(++sKey, cb);
            return sKey;
        }
    }

    void unrigisterCallback(SourceCallBack cb) {
        synchronized (sCallbacks) {
            int idx = sCallbacks.indexOfValue(cb);
            if (idx != -1) {
                sCallbacks.removeAt(idx);
            }
        }
    }

    public int getLastErrorCode() {
        return getErrorCode(mCtx);
    }

    public int openStream(int channel, String url, int type, int sendOption, int mediaType, String user, String pwd) {
        _channel = channel;
        _url = url;
        _type = type;
        _mediaType = mediaType;
        _user = user;
        _pwd = pwd;
        _sendOption = sendOption;
        return openStream();
    }

    public void closeStream() {
        h.removeCallbacks(closeTask);

        if (mCtx != 0) {
            closeStream(mCtx);
        }
    }

    private static native int getErrorCode(long context);

    private native long init(Context context, String key);

    private native int deInit(long context);

    private int openStream() {
        if (null == _url) {
            throw new NullPointerException();
        }

        if (mCtx == 0) {
            throw new IllegalStateException("初始化失败，KEY不合法");
        }

        return openStream(mCtx, _channel, _url, _type, _mediaType, _user, _pwd, 1000, 0, _sendOption);
    }

    private native int openStream(long context, int channel, String url, int type, int mediaType, String user, String pwd, int reconn, int outRtpPacket, int rtspOption);

//    private native int startRecord(int context, String path);
//
//    private native void stopRecord(int context);

    private native void closeStream(long context);

    private static void save2path(byte[] buffer, int offset, int length, String path, boolean append) {
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(path, append);
            fos.write(buffer, offset, length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void onSourceCallBack(int _channelId, int _channelPtr, int _frameType, byte[] pBuf, byte[] frameBuffer) {
        if (BuildConfig.MEDIA_DEBUG) {

            int permissionCheck = ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                // frameType + size + buffer
                if (_frameType != 0) {
                    ByteBuffer bf = ByteBuffer.allocate(5);
                    bf.put((byte) _frameType);
                    if (_frameType == EASY_SDK_MEDIA_INFO_FLAG) {
                        bf.putInt(pBuf.length);
                        save2path(bf.array(), 0, 5, "/sdcard/media_degbu.data", true);
                        save2path(pBuf, 0, pBuf.length, "/sdcard/media_degbu.data", true);
                    } else {
                        bf.putInt(frameBuffer.length);
                        save2path(bf.array(), 0, 5, "/sdcard/media_degbu.data", true);
                        save2path(frameBuffer, 0, frameBuffer.length, "/sdcard/media_degbu.data", true);
                    }
                }
            }
        }

        final SourceCallBack callBack;
        synchronized (sCallbacks) {
            callBack = sCallbacks.get(_channelId);
        }

        if (_frameType == 0) {
            if (callBack != null) {
                callBack.onSourceCallBack(_channelId, _channelPtr, _frameType, null);
            }
            return;
        }

        if (_frameType == EASY_SDK_MEDIA_INFO_FLAG) {
            if (callBack != null) {
                MediaInfo mi = new MediaInfo();

                ByteBuffer buffer = ByteBuffer.wrap(pBuf);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                mi.videoCodec = buffer.getInt();
                mi.fps = buffer.getInt();
                mi.audioCodec = buffer.getInt();
                mi.sample = buffer.getInt();
                mi.channel = buffer.getInt();
                mi.bitPerSample = buffer.getInt();
                mi.spsLen = buffer.getInt();
                mi.ppsLen = buffer.getInt();
                mi.sps = new byte[128];
                mi.pps = new byte[36];

                buffer.get(mi.sps);
                buffer.get(mi.pps);
//                    int videoCodec;int fps;
//                    int audioCodec;int sample;int channel;int bitPerSample;
//                    int spsLen;
//                    int ppsLen;
//                    byte[]sps;
//                    byte[]pps;

                callBack.onMediaInfoCallBack(_channelId, mi);
            }
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(frameBuffer);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        FrameInfo fi = new FrameInfo();
        fi.codec = buffer.getInt();
        fi.type = buffer.getInt();
        fi.fps = buffer.get();
        buffer.get();
        fi.width = buffer.getShort();
        fi.height = buffer.getShort();
        buffer.getInt();
        buffer.getInt();
        buffer.getShort();
        fi.sample_rate = buffer.getInt();
        fi.channels = buffer.getInt();
        fi.bits_per_sample = buffer.getInt();
        fi.length = buffer.getInt();
        fi.timestamp_usec = buffer.getInt();
        fi.timestamp_sec = buffer.getInt();

        long sec = fi.timestamp_sec < 0 ? Integer.MAX_VALUE - Integer.MIN_VALUE + 1 + fi.timestamp_sec : fi.timestamp_sec;
        long usec = fi.timestamp_usec < 0 ? Integer.MAX_VALUE - Integer.MIN_VALUE + 1 + fi.timestamp_usec : fi.timestamp_usec;
        fi.stamp = sec * 1000000 + usec;

//        long differ = fi.stamp - mPreviewStamp;
//        Log.d(TAG, String.format("%s:%d,%d,%d, %d", EASY_SDK_VIDEO_FRAME_FLAG == _frameType ? "视频" : "音频", fi.stamp, fi.timestamp_sec, fi.timestamp_usec, differ));
        fi.buffer = pBuf;

        boolean paused = false;
        synchronized (_channelPause) {
            paused = _channelPause.contains(_channelId);
        }

        if (callBack != null) {
            if (paused) {
                Log.i(TAG, "channel_" + _channelId + " is paused!");
            }
            callBack.onSourceCallBack(_channelId, _channelPtr, _frameType, fi);
        }
    }

    private static void onEvent(int channel, int err, int state) {
        // state：1 Connecting, 2 连接错误, 3 连接线程退出
        // err的含义：http请求的返回码（200，400，401等等）
        Log.e(TAG, String.format("__RTSPClientCallBack onEvent: err=%d, state=%d", err, state));

        synchronized (sCallbacks) {
            final SourceCallBack callBack = sCallbacks.get(channel);
            if (callBack != null) {
                callBack.onEvent(channel, err, state);
            }
        }
    }

    public void pause() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalThreadStateException("please call pause in Main thread!");
        }

        synchronized (_channelPause) {
            _channelPause.add(_channel);
        }

        paused = 1;
        Log.i(TAG, "pause:=" + 1);
        h.postDelayed(closeTask, 10000);
    }

    public void resume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalThreadStateException("call resume in Main thread!");
        }

        synchronized (_channelPause) {
            _channelPause.remove(_channel);
        }

        h.removeCallbacks(closeTask);

        if (paused == 2) {
            Log.i(TAG, "resume:=" + 0);
            openStream();
        }

        Log.i(TAG, "resume:=" + 0);
        paused = 0;
    }

    @Override
    public void close() throws IOException {
        h.removeCallbacks(closeTask);
        _channelPause.remove(_channel);

        if (mCtx == 0)
            throw new IOException("not opened or already closed");

        deInit(mCtx);
        mCtx = 0;
    }
}
