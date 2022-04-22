package org.easydarwin.video;

import android.annotation.TargetApi;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import org.easydarwin.audio.AudioCodec;
import org.easydarwin.audio.EasyAACMuxer;
import org.easydarwin.sw.JNIUtil;
import org.easydarwin.util.CodecSpecificDataUtil;
import org.easydarwin.util.TextureLifecycler;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar;
import static org.easydarwin.util.CodecSpecificDataUtil.AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE;
import static org.easydarwin.video.Client.TRANSTYPE_TCP;
import static org.easydarwin.video.EasyMuxer2.VIDEO_TYPE_H264;
import static org.easydarwin.video.EasyMuxer2.VIDEO_TYPE_H265;

/**
 * Created by John on 2016/3/17.
 */
public class EasyPlayerClient implements Client.SourceCallBack {
    private static final long LEAST_FRAME_INTERVAL = 10000l;

    /* 视频编码 */
    public static final int EASY_SDK_VIDEO_CODEC_H264 = 0x1C;       /* H264 */
    public static final int EASY_SDK_VIDEO_CODEC_H265 = 0x48323635; /* H265 */
    public static final int EASY_SDK_VIDEO_CODEC_MJPEG = 0x08;      /* MJPEG */
    public static final int EASY_SDK_VIDEO_CODEC_MPEG4 = 0x0D;      /* MPEG4 */

    /* 音频编码 */
    public static final int EASY_SDK_AUDIO_CODEC_AAC = 0x15002;     /* AAC */
    public static final int EASY_SDK_AUDIO_CODEC_G711U = 0x10006;   /* G711 ulaw */
    public static final int EASY_SDK_AUDIO_CODEC_G711A = 0x10007;   /* G711 alaw */
    public static final int EASY_SDK_AUDIO_CODEC_G726 = 0x1100B;    /* G726 */

    /**
     * 表示视频显示出来了
     */
    public static final int RESULT_VIDEO_DISPLAYED = 01;

    /**
     * 表示视频的解码方式
     */
    public static final String KEY_VIDEO_DECODE_TYPE = "video-decode-type";

    /**
     * 表示视频的尺寸获取到了。具体尺寸见 EXTRA_VIDEO_WIDTH、EXTRA_VIDEO_HEIGHT
     */
    public static final int RESULT_VIDEO_SIZE = 02;

    public static final int RESULT_TIMEOUT = 03;

    public static final int RESULT_EVENT = 04;
    public static final int RESULT_UNSUPPORTED_VIDEO = 05;
    public static final int RESULT_UNSUPPORTED_AUDIO = 06;
    public static final int RESULT_RECORD_BEGIN = 7;
    public static final int RESULT_RECORD_END = 8;

    /**
     * 表示第一帧数据已经收到
     */
    public static final int RESULT_FRAME_RECVED = 9;

    private static final String TAG = EasyPlayerClient.class.getSimpleName();
    /**
     * 表示视频的宽度
     */
    public static final String EXTRA_VIDEO_WIDTH = "extra-video-width";
    /**
     * 表示视频的高度
     */
    public static final String EXTRA_VIDEO_HEIGHT = "extra-video-height";

    private static final int NAL_VPS = 32;
    private static final int NAL_SPS = 33;
    private static final int NAL_PPS = 34;

    private Surface mSurface;
    private final TextureLifecycler lifecycler;
    private volatile Thread mThread, mAudioThread;
    private final ResultReceiver mRR;
    private Client mClient;
    private boolean mAudioEnable = true;
    private volatile long mReceivedDataLength;
    private AudioTrack mAudioTrack;
    private String mRecordingPath;
    private EasyAACMuxer mObject;
    private EasyMuxer2 muxer2;
    private Client.MediaInfo mMediaInfo;
    private short mHeight = 0;
    short mWidth = 0;
    private ByteBuffer mCSD0;
    private ByteBuffer mCSD1;
    private final I420DataCallback i420callback;
    private boolean mMuxerWaitingKeyVideo;

    /**
     * -1 表示暂停中，0表示正常录像中，1表示恢复中。
     */
    private int mRecordingStatus;
    private long muxerPausedMillis = 0L;
    private long mMuxerCuttingMillis = 0L;

//    private RtmpClient mRTMPClient = new RtmpClient();

    public boolean isRecording() {
        return !TextUtils.isEmpty(mRecordingPath);
    }

    private static class FrameInfoQueue extends PriorityQueue<Client.FrameInfo> {
        public static final int CAPACITY = 500;
        public static final int INITIAL_CAPACITY = 300;

        public FrameInfoQueue() {
            super(INITIAL_CAPACITY, new Comparator<Client.FrameInfo>() {
                @Override
                public int compare(Client.FrameInfo frameInfo, Client.FrameInfo t1) {
                    return (int) (frameInfo.stamp - t1.stamp);
                }
            });
        }

        final ReentrantLock lock = new ReentrantLock();
        final Condition notFull = lock.newCondition();
        final Condition notVideo = lock.newCondition();
        final Condition notAudio = lock.newCondition();

        @Override
        public int size() {
            lock.lock();
            try {
                return super.size();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void clear() {
            lock.lock();
            try {
                int size = super.size();
                super.clear();
                int k = size;

                for (; k > 0 && lock.hasWaiters(notFull); k--) {
                    notFull.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        public void put(Client.FrameInfo x) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                int size;
                while ((size = super.size()) == CAPACITY) {
                    Log.v(TAG, "queue full:" + CAPACITY);
                    notFull.await();
                }

                offer(x);
//                Log.d(TAG, String.format("queue size : " + size));
                // 这里是乱序的。并非只有空的queue才丢到首位。因此不能做限制 if (size == 0)
                {

                    if (x.audio) {
                        notAudio.signal();
                    } else {
                        notVideo.signal();
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public Client.FrameInfo takeVideoFrame() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    Client.FrameInfo x = peek();

                    if (x == null) {
                        notVideo.await();
                    } else {
                        if (!x.audio) {
                            remove();
                            notFull.signal();
                            notAudio.signal();
                            return x;
                        } else {
                            notVideo.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public Client.FrameInfo takeVideoFrame(long ms) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    Client.FrameInfo x = peek();
                    if (x == null) {
                        if (!notVideo.await(ms, TimeUnit.MILLISECONDS)) return null;
                    } else {
                        if (!x.audio) {
                            remove();
                            notFull.signal();
                            notAudio.signal();
                            return x;
                        } else {
                            notVideo.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public Client.FrameInfo takeAudioFrame() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    Client.FrameInfo x = peek();
                    if (x == null) {
                        notAudio.await();
                    } else {
                        if (x.audio) {
                            remove();
                            notFull.signal();
                            notVideo.signal();
                            return x;
                        } else {
                            notAudio.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private FrameInfoQueue mQueue = new FrameInfoQueue();

    private final Context mContext;

    /**
     * 最新的视频时间戳
     */
    private volatile long mNewestStample;
    private boolean mWaitingKeyFrame;
    private boolean mTimeout;
    private boolean mNotSupportedVideoCB, mNotSupportedAudioCB;

    /**
     * 创建SDK对象
     *
     * @param context 上下文对象
     * @param surface 显示视频用的surface
     */
    public EasyPlayerClient(Context context, Surface surface, ResultReceiver receiver) {
        this(context, surface, receiver, null);
    }

    /**
     * 创建SDK对象
     *
     * @param context 上下文对象
     * @param surface 显示视频用的surface
     */
    public EasyPlayerClient(Context context, Surface surface, ResultReceiver receiver, I420DataCallback callback) {
        mSurface = surface;
        mContext = context;
        mRR = receiver;
        i420callback = callback;
        lifecycler = null;
    }

    public EasyPlayerClient(Context context, final TextureView view, ResultReceiver receiver, I420DataCallback callback) {
        lifecycler = new TextureLifecycler(view);
        mContext = context;
        mRR = receiver;
        i420callback = callback;

        LifecycleObserver observer1 = new LifecycleObserver() {
            @OnLifecycleEvent(value = Lifecycle.Event.ON_DESTROY)
            public void destory() {
                stop();
                mSurface.release();
                mSurface = null;
            }

            @OnLifecycleEvent(value = Lifecycle.Event.ON_CREATE)
            private void create() {
                mSurface = new Surface(view.getSurfaceTexture());
            }
        };

        lifecycler.getLifecycle().addObserver(observer1);

        if (context instanceof LifecycleOwner) {
            LifecycleObserver observer = new LifecycleObserver() {
                @OnLifecycleEvent(value = Lifecycle.Event.ON_DESTROY)
                public void destory() {
                    stop();
                }

                @OnLifecycleEvent(value = Lifecycle.Event.ON_PAUSE)
                private void pause() {
                    EasyPlayerClient.this.pause();
                }

                @OnLifecycleEvent(value = Lifecycle.Event.ON_RESUME)
                private void resume() {
                    EasyPlayerClient.this.resume();
                }
            };

            ((LifecycleOwner) context).getLifecycle().addObserver(observer);
        }
    }

    /**
     * 启动播放
     *
     * @param url
     * @return
     */
    public void play(final String url) {
        if (lifecycler.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
            start(url, TRANSTYPE_TCP, 0, Client.EASY_SDK_VIDEO_FRAME_FLAG | Client.EASY_SDK_AUDIO_FRAME_FLAG, "", "", null);
        } else {
            lifecycler.getLifecycle().addObserver(new LifecycleObserver() {
                @OnLifecycleEvent(value = Lifecycle.Event.ON_CREATE)
                void create() {
                    start(url, TRANSTYPE_TCP, 0, Client.EASY_SDK_VIDEO_FRAME_FLAG | Client.EASY_SDK_AUDIO_FRAME_FLAG, "", "", null);
                }
            });
        }
    }

    /**
     * 启动播放
     *
     * @param url
     * @param type
     * @param sendOption
     * @param mediaType
     * @param user
     * @param pwd
     * @return
     */
    public int start(final String url, int type, int sendOption, int mediaType, String user, String pwd) {
        return start(url, type, sendOption, mediaType, user, pwd, null);
    }

    /**
     * 启动播放
     *
     * @param url
     * @param type
     * @param sendOption
     * @param mediaType
     * @param user
     * @param pwd
     * @return
     */
    public int start(final String url, int type, int sendOption, int mediaType, String user, String pwd, String recordPath) {
        if (url == null) {
            throw new NullPointerException("url is null");
        }
        if (type == 0)
            type = TRANSTYPE_TCP;
        mNewestStample = 0;
        mWaitingKeyFrame = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("waiting_i_frame", true);
        mWidth = mHeight = 0;
        mQueue.clear();
        startCodec();
        startAudio();
        mTimeout = false;
        mNotSupportedVideoCB = mNotSupportedAudioCB = false;
        mReceivedDataLength = 0;
        mClient = new Client(mContext);
        int channel = mClient.registerCallback(this);
        mRecordingPath = recordPath;
        Log.i(TAG, String.format("playing url:\n%s\n", url));
        return mClient.openStream(channel, url, type, sendOption, mediaType, user, pwd);
    }

    public boolean isAudioEnable() {
        return mAudioEnable;
    }

    public void setAudioEnable(boolean enable) {
        mAudioEnable = enable;
        AudioTrack at = mAudioTrack;
        if (at != null) {
            Log.i(TAG, String.format("audio will be %s", enable ? "enabled" : "disabled"));
            synchronized (at) {
                if (!enable) {
                    at.pause();
                    at.flush();
                } else {
                    at.flush();
                    at.play();
                }
            }
        }
    }

    public static interface I420DataCallback {
        public void onI420Data(ByteBuffer buffer);
    }

    public void pause() {
        mQueue.clear();
        if (mClient != null) {
            mClient.pause();
        }
        mQueue.clear();
    }

    public void resume() {
        if (mClient != null) {
            mClient.resume();
        }
    }

    /**
     * 终止播放
     */
    public void stop() {
        Thread t = mThread;
        mThread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        t = mAudioThread;
        mAudioThread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        stopRecord();

        mQueue.clear();
        if (mClient != null) {
            mClient.unrigisterCallback(this);
            mClient.closeStream();
            try {
                mClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mQueue.clear();
        mClient = null;
        mNewestStample = 0;
    }

    public long receivedDataLength() {
        return mReceivedDataLength;
    }

    private void startAudio() {
        mAudioThread = new Thread("AUDIO_CONSUMER") {

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                    Client.FrameInfo frameInfo;
                    long handle = 0;
                    final AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                    AudioManager.OnAudioFocusChangeListener l = new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {
                            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                                AudioTrack audioTrack = mAudioTrack;
                                if (audioTrack != null) {
                                    audioTrack.setStereoVolume(1.0f, 1.0f);
                                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                                        audioTrack.flush();
                                        audioTrack.play();
                                    }
                                }
                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                AudioTrack audioTrack = mAudioTrack;
                                if (audioTrack != null) {
                                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                                        audioTrack.pause();
                                    }
                                }
                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                                AudioTrack audioTrack = mAudioTrack;
                                if (audioTrack != null) {
                                    audioTrack.setStereoVolume(0.5f, 0.5f);
                                }
                            }
                        }
                    };
                    try {
                        int requestCode = am.requestAudioFocus(l, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                        if (requestCode != AUDIOFOCUS_REQUEST_GRANTED) {
                            return;
                        }
                        do {
                            frameInfo = mQueue.takeAudioFrame();
                            if (mMediaInfo != null) break;
                        } while (true);
                        final Thread t = Thread.currentThread();

                        if (mAudioTrack == null) {
                            int sampleRateInHz = (int) (mMediaInfo.sample * 1.001);
                            int channelConfig = mMediaInfo.channel == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                            int bfSize = AudioTrack.getMinBufferSize(mMediaInfo.sample, channelConfig, audioFormat) * 8;
                            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig, audioFormat, bfSize, AudioTrack.MODE_STREAM);
                        }
                        mAudioTrack.play();
                        handle = AudioCodec.create(frameInfo.codec, frameInfo.sample_rate, frameInfo.channels, frameInfo.bits_per_sample);

                        Log.w(TAG, String.format("POST VIDEO_DISPLAYED IN AUDIO THREAD!!!"));
                        ResultReceiver rr = mRR;
                        if (rr != null) rr.send(RESULT_VIDEO_DISPLAYED, null);

                        // 半秒钟的数据缓存
                        byte[] mBufferReuse = new byte[16000];
                        int[] outLen = new int[1];
                        while (mAudioThread != null) {
                            if (frameInfo == null) {
                                frameInfo = mQueue.takeAudioFrame();
                            }
                            if (frameInfo.codec == EASY_SDK_AUDIO_CODEC_AAC && false) {
                                pumpAACSample(frameInfo);
                            }
                            outLen[0] = mBufferReuse.length;
                            long ms = SystemClock.currentThreadTimeMillis();
                            int nRet = AudioCodec.decode(handle, frameInfo.buffer, 0, frameInfo.length, mBufferReuse, outLen);
                            if (nRet == 0) {
//                                if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC )
                                {
//                                    save2path(mBufferReuse, 0, outLen[0],"/sdcard/111.pcm", true);
                                    pumpPCMSample(mBufferReuse, outLen[0], frameInfo.stamp);
                                }
                                if (mAudioEnable)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        mAudioTrack.write(mBufferReuse, 0, outLen[0], AudioTrack.WRITE_NON_BLOCKING);
                                    } else {
                                        mAudioTrack.write(mBufferReuse, 0, outLen[0]);
                                    }

                            }
                            frameInfo = null;
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        am.abandonAudioFocus(l);
                        if (handle != 0) {
                            AudioCodec.close(handle);
                        }
                        AudioTrack track = mAudioTrack;
                        if (track != null) {
                            synchronized (track) {
                                mAudioTrack = null;
                                track.release();
                            }
                        }
                    }
                }
            }
        };

        mAudioThread.start();
    }

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

    private static int getXPS(byte[] data, int offset, int length, byte[] dataOut, int[] outLen, int type) {
        int i;
        int pos0;
        int pos1;
        pos0 = -1;
        length = Math.min(length, data.length);
        for (i = offset; i < length - 4; i++) {
            if ((0 == data[i]) && (0 == data[i + 1]) && (1 == data[i + 2]) && (type == (0x0F & data[i + 3]))) {
                pos0 = i;
                break;
            }
        }
        if (-1 == pos0) {
            return -1;
        }
        if (pos0 > 0 && data[pos0 - 1] == 0) { // 0 0 0 1
            pos0 = pos0 - 1;
        }
        pos1 = -1;
        for (i = pos0 + 4; i < length - 4; i++) {
            if ((0 == data[i]) && (0 == data[i + 1]) && (1 == data[i + 2])) {
                pos1 = i;
                break;
            }
        }
        if (-1 == pos1 || pos1 == 0) {
            return -2;
        }
        if (data[pos1 - 1] == 0) {
            pos1 -= 1;
        }
        if (pos1 - pos0 > outLen[0]) {
            return -3; // 输入缓冲区太小
        }
        dataOut[0] = 0;
        System.arraycopy(data, pos0, dataOut, 0, pos1 - pos0);
        // memcpy(pXPS+1, pES+pos0, pos1-pos0);
        // *pMaxXPSLen = pos1-pos0+1;
        outLen[0] = pos1 - pos0;
        return pos1;
    }

    private static byte[] getvps_sps_pps(byte[] data, int offset, int length) {
        int i = 0;
        int vps = -1, sps = -1, pps = -1;
        length = Math.min(length, data.length);
        do {
            if (vps == -1) {
                for (i = offset; i < length - 4; i++) {
                    if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                        byte nal_spec = data[i + 3];
                        int nal_type = (nal_spec >> 1) & 0x03f;
                        if (nal_type == NAL_VPS) {
                            // vps found.
                            if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                                vps = i - 1;
                            } else {                      // start with 00 00 01
                                vps = i;
                            }
                            break;
                        }
                    }
                }
            }
            if (sps == -1) {
                for (i = vps; i < length - 4; i++) {
                    if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                        byte nal_spec = data[i + 3];
                        int nal_type = (nal_spec >> 1) & 0x03f;
                        if (nal_type == NAL_SPS) {
                            // vps found.
                            if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                                sps = i - 1;
                            } else {                      // start with 00 00 01
                                sps = i;
                            }
                            break;
                        }
                    }
                }
            }
            if (pps == -1) {
                for (i = sps; i < length - 4; i++) {
                    if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                        byte nal_spec = data[i + 3];
                        int nal_type = (nal_spec >> 1) & 0x03f;
                        if (nal_type == NAL_PPS) {
                            // vps found.
                            if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                                pps = i - 1;
                            } else {                    // start with 00 00 01
                                pps = i;
                            }
                            break;
                        }
                    }
                }
            }
        } while (vps == -1 || sps == -1 || pps == -1);
        if (vps == -1 || sps == -1 || pps == -1) {// 没有获取成功。
            return null;
        }
        // 计算csd buffer的长度。即从vps的开始到pps的结束的一段数据
        int begin = vps;
        int end = -1;
        for (i = pps + 4; i < length - 4; i++) {
            if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                    end = i - 1;
                } else {                    // start with 00 00 01
                    end = i;
                }
                break;
            }
        }
        if (end == -1 || end < begin) {
            return null;
        }
        // 拷贝并返回
        byte[] buf = new byte[end - begin];
        System.arraycopy(data, begin, buf, 0, buf.length);
        return buf;
    }

    private static boolean codecMatch(String mimeType, MediaCodecInfo codecInfo) {
        String[] types = codecInfo.getSupportedTypes();
        for (String type : types) {
            if (type.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    private static String codecName() {
        ArrayList<String> array = new ArrayList<>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i1 = 0; i1 < numCodecs; i1++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i1);
            if (codecInfo.isEncoder()) {
                continue;
            }

            if (codecMatch("video/avc", codecInfo)) {
                String name = codecInfo.getName();
                Log.d("DECODER", String.format("decoder:%s", name));
                array.add(name);
            }
        }
//        if (array.remove("OMX.qcom.video.decoder.avc")) {
//            array.add("OMX.qcom.video.decoder.avc");
//        }
//        if (array.remove("OMX.amlogic.avc.decoder.awesome")) {
//            array.add("OMX.amlogic.avc.decoder.awesome");
//        }
        if (array.isEmpty()) {
            return "";
        }
        return array.get(0);
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private void startCodec() {
        mThread = new Thread("VIDEO_CONSUMER") {

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                MediaCodec mCodec = null;
                int mColorFormat = 0;
                VideoCodec.VideoDecoderLite mDecoder = null, displayer = null;

                try {
                    boolean pushBlankBuffersOnStop = true;

                    int index = 0;
                    // previous
                    long previousStampUs = 0l;
                    long lastFrameStampUs = 0l;
                    long differ = 0;
                    int realWidth = mWidth;
                    int realHeight = mHeight;
                    int sliceHeight = realHeight;

                    int frameWidth = 0;
                    int frameHeight = 0;
//
//                    long decodeBegin = 0;
//                    long current = 0;

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                    Client.FrameInfo initFrameInfo = null;
                    Client.FrameInfo frameInfo = null;

                    while (mThread != null) {
                        if (mCodec == null && mDecoder == null) {
                            if (frameInfo == null) {
                                frameInfo = mQueue.takeVideoFrame();
                            }

                            initFrameInfo = frameInfo;

                            try {
                                if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("use-sw-codec", false)) {
                                    throw new IllegalStateException("user set sw codec");
                                }

                                final String mime = frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264 ? "video/avc" : "video/hevc";
                                MediaFormat format = MediaFormat.createVideoFormat(mime, mWidth, mHeight);
                                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
                                format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, pushBlankBuffersOnStop ? 1 : 0);
                                // 指定解码后的帧格式
                                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUV420Flexible);

                                if (mCSD0 != null) {
                                    format.setByteBuffer("csd-0", mCSD0);
                                } else {
                                    throw new InvalidParameterException("csd-0 is invalid.");
                                }

                                if (mCSD1 != null) {
                                    format.setByteBuffer("csd-1", mCSD1);
                                } else {
                                    if (frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264)
                                        throw new InvalidParameterException("csd-1 is invalid.");
                                }

                                MediaCodecInfo ci = selectCodec(mime);
                                mColorFormat = CodecSpecificDataUtil.selectColorFormat(ci, mime);

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    MediaCodecInfo.CodecCapabilities capabilities = ci.getCapabilitiesForType(mime);
                                    MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
                                    boolean supported = videoCapabilities.isSizeSupported(mWidth, mHeight);
                                    Log.i(TAG, "media codec " + ci.getName() + (supported ? "support" : "not support") + mWidth + "*" + mHeight);
                                    if (!supported) {
                                        boolean b1 = videoCapabilities.getSupportedWidths().contains(mWidth + 0);
                                        boolean b2 = videoCapabilities.getSupportedHeights().contains(mHeight + 0);
                                        supported |= b1 && b2;
                                        if (supported) {
                                            Log.w(TAG, ".......................................................................");
                                        } else {
                                            throw new IllegalStateException("media codec " + ci.getName() + (supported ? "support" : "not support") + mWidth + "*" + mHeight);
                                        }
                                    }
                                }
                                Log.i(TAG, String.format("config codec:%s", format));

                                MediaCodec codec = MediaCodec.createByCodecName(ci.getName());
                                codec.configure(format, i420callback != null ? null : mSurface, null, 0);
                                codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                                codec.start();

                                mCodec = codec;
                                if (i420callback != null) {
                                    final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
                                    decoder.create(mSurface, frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264);
                                    displayer = decoder;
                                }
                            } catch (Throwable e) {
                                if (mCodec != null) {
                                    mCodec.release();
                                }
                                mCodec = null;

                                if (displayer != null) {
                                    displayer.close();
                                    Log.i(TAG, "AAAA 958 displayer.close()");
                                }

                                displayer = null;

                                Log.e(TAG, String.format("init codec error due to %s", e.getMessage()));
                                e.printStackTrace();

                                final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
                                decoder.create(mSurface, frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264);
                                mDecoder = decoder;
                            }

//                            previewTickUs = mTexture.getTimestamp();
//                            differ = previewTickUs - frameInfo.stamp;
//                            index = mCodec.dequeueInputBuffer(0);
//                            if (index >= 0) {
//                                ByteBuffer buffer = mCodec.getInputBuffers()[index];
//                                buffer.clear();
//                                mCSD0.clear();
//                                mCSD1.clear();
//                                buffer.put(mCSD0.array(), 0, mCSD0.remaining());
//                                buffer.put(mCSD1.array(), 0, mCSD1.remaining());
//                                mCodec.queueInputBuffer(index, 0, buffer.position(), 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
//                            }
                        } else {
                            frameInfo = mQueue.takeVideoFrame(5);
                        }

                        if (frameInfo != null) {
                            Log.d(TAG, "video " + frameInfo.stamp + " take[" + (frameInfo.stamp - lastFrameStampUs) + "]");
                            if (frameHeight != 0 && frameWidth != 0) {
                                if (frameInfo.width != 0 && frameInfo.height != 0) {
                                    if (frameInfo.width != frameWidth || frameInfo.height != frameHeight) {
                                        frameHeight = frameInfo.height;
                                        frameWidth = frameInfo.width;
                                        stopRecord();
                                        if (mCodec != null) {
                                            mCodec.release();
                                            mCodec = null;
                                            continue;
                                        }
                                    }
                                }
                            }
                            frameHeight = frameInfo.height;
                            frameWidth = frameInfo.width;
                            pumpVideoSample(frameInfo);
                            lastFrameStampUs = frameInfo.stamp;
                        }

                        do {
                            if (mDecoder != null) {
                                if (frameInfo != null) {
                                    long decodeBegin = SystemClock.elapsedRealtime();
                                    int[] size = new int[2];

//                                    mDecoder.decodeFrame(frameInfo, size);
                                    ByteBuffer buf = mDecoder.decodeFrameYUV(frameInfo, size);

                                    if (i420callback != null && buf != null) {
                                        i420callback.onI420Data(buf);
                                    }

                                    if (buf != null) {
                                        mDecoder.releaseBuffer(buf);
                                        Log.i(TAG, "AAAA 1022 releaseBuffer ");
                                    }

                                    long decodeSpend = SystemClock.elapsedRealtime() - decodeBegin;

                                    boolean firstFrame = previousStampUs == 0l;
                                    if (firstFrame) {
                                        Log.i(TAG, String.format("POST VIDEO_DISPLAYED!!!"));
                                        ResultReceiver rr = mRR;
                                        if (rr != null) {
                                            Bundle data = new Bundle();
                                            data.putInt(KEY_VIDEO_DECODE_TYPE, 0);
                                            rr.send(RESULT_VIDEO_DISPLAYED, data);
                                        }
                                    }

                                    //Log.d(TAG, String.format("timestamp=%d diff=%d",current, current - previousStampUs ));

                                    if (previousStampUs != 0l) {
                                        long sleepTime = frameInfo.stamp - previousStampUs - decodeSpend * 1000;
                                        if (sleepTime > 100000) {
                                            Log.w(TAG, "sleep time.too long:" + sleepTime);
                                            sleepTime = 100000;
                                        }
                                        if (sleepTime > 0) {
                                            sleepTime %= 100000;
                                            long cache = mNewestStample - frameInfo.stamp;
                                            sleepTime = fixSleepTime(sleepTime, cache, 50000);
                                            if (sleepTime > 0) {
                                                Thread.sleep(sleepTime / 1000);
                                            }
                                            Log.d(TAG, "cache:" + cache);
                                        }
                                    }
                                    previousStampUs = frameInfo.stamp;
                                }
                            } else {
                                try {
                                    do {
                                        if (frameInfo != null) {
                                            byte[] pBuf = frameInfo.buffer;
                                            index = mCodec.dequeueInputBuffer(10);
                                            if (false)
                                                throw new IllegalStateException("fake state");
                                            if (index >= 0) {
                                                ByteBuffer buffer = mCodec.getInputBuffers()[index];
                                                buffer.clear();
                                                if (pBuf.length > buffer.remaining()) {
                                                    mCodec.queueInputBuffer(index, 0, 0, frameInfo.stamp, 0);
                                                } else {
                                                    buffer.put(pBuf, frameInfo.offset, frameInfo.length);
                                                    mCodec.queueInputBuffer(index, 0, buffer.position(), frameInfo.stamp + differ, 0);
                                                }
                                                frameInfo = null;
                                            }
                                        }
                                        index = mCodec.dequeueOutputBuffer(info, 10); //
                                        switch (index) {
                                            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                                Log.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                                                break;
                                            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                                MediaFormat mf = mCodec.getOutputFormat();
                                                Log.i(TAG, "INFO_OUTPUT_FORMAT_CHANGED ：" + mf);

                                                int width = mf.getInteger(MediaFormat.KEY_WIDTH);
                                                if (mf.containsKey("crop-left") && mf.containsKey("crop-right")) {
                                                    width = mf.getInteger("crop-right") + 1 - mf.getInteger("crop-left");
                                                }

                                                int height = mf.getInteger(MediaFormat.KEY_HEIGHT);
                                                if (mf.containsKey("crop-top") && mf.containsKey("crop-bottom")) {
                                                    height = mf.getInteger("crop-bottom") + 1 - mf.getInteger("crop-top");
                                                }

                                                realWidth = width;
                                                realHeight = height;

                                                if (mf.containsKey(MediaFormat.KEY_SLICE_HEIGHT)) {
                                                    sliceHeight = mf.getInteger(MediaFormat.KEY_SLICE_HEIGHT);
                                                } else {
                                                    sliceHeight = realHeight;
                                                }
                                                break;
                                            case MediaCodec.INFO_TRY_AGAIN_LATER:
                                                // 输出为空
                                                break;
                                            default:
                                                // 输出队列不为空
                                                // -1表示为第一帧数据
                                                long newSleepUs = -1;
                                                boolean firstTime = previousStampUs == 0l;
                                                if (!firstTime) {
                                                    long sleepUs = (info.presentationTimeUs - previousStampUs);
                                                    if (sleepUs > 100000) {
                                                        // 时间戳异常，可能服务器丢帧了。
                                                        Log.w(TAG, "sleep time.too long:" + sleepUs);
                                                        sleepUs = 100000;
                                                    } else if (sleepUs < 0) {
                                                        Log.w(TAG, "sleep time.too short:" + sleepUs);
                                                        sleepUs = 0;
                                                    }

                                                    {
                                                        long cache = mNewestStample - lastFrameStampUs;
                                                        newSleepUs = fixSleepTime(sleepUs, cache, 100000);
                                                        // Log.d(TAG, String.format("sleepUs:%d,newSleepUs:%d,Cache:%d", sleepUs, newSleepUs, cache));
                                                    }
                                                }

                                                //previousStampUs = info.presentationTimeUs;
                                                ByteBuffer outputBuffer;
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                    outputBuffer = mCodec.getOutputBuffer(index);
                                                } else {
                                                    outputBuffer = mCodec.getOutputBuffers()[index];
                                                }

                                                if (i420callback != null && outputBuffer != null) {
                                                    if (sliceHeight != realHeight) {
                                                        ByteBuffer tmp = ByteBuffer.allocateDirect(realWidth * realHeight * 3 / 2);
                                                        outputBuffer.clear();
                                                        outputBuffer.limit(realWidth * realHeight);
                                                        tmp.put(outputBuffer);

                                                        outputBuffer.clear();
                                                        outputBuffer.position(realWidth * sliceHeight);
                                                        outputBuffer.limit((realWidth * sliceHeight + realWidth * realHeight / 4));
                                                        tmp.put(outputBuffer);

                                                        outputBuffer.clear();
                                                        outputBuffer.position(realWidth * sliceHeight + realWidth * realHeight / 4);
                                                        outputBuffer.limit((realWidth * sliceHeight + realWidth * realHeight / 4 + realWidth * realHeight / 4));
                                                        tmp.put(outputBuffer);

                                                        tmp.clear();
                                                        outputBuffer = tmp;
                                                    }

                                                    if (mColorFormat == COLOR_FormatYUV420SemiPlanar
                                                            || mColorFormat == COLOR_FormatYUV420PackedSemiPlanar
                                                            || mColorFormat == COLOR_TI_FormatYUV420PackedSemiPlanar) {

                                                        byte[] in = new byte[realWidth * realHeight * 3 / 2];
                                                        outputBuffer.clear();
                                                        outputBuffer.get(in);

                                                        // yuvuv_to_yuv
                                                        JNIUtil.yuvConvert(in, realWidth, realHeight, 4);
//                                                        // 旋转90或180或270度
//                                                        yuvRotate(in, 0, realWidth, realHeight, 90);

                                                        ByteBuffer tmp = ByteBuffer.allocateDirect(realWidth * realHeight * 3 / 2);
                                                        tmp.clear();
                                                        tmp.put(in);

                                                        i420callback.onI420Data(tmp);

                                                        // 旋转90或270度，则宽高需要互换
                                                        displayer.decoder_decodeBuffer(tmp, realWidth, realHeight);
                                                    }
                                                }

                                                //previewStampUs = info.presentationTimeUs;
                                                if (false && Build.VERSION.SDK_INT >= 21) {
                                                    Log.d(TAG, String.format("releaseoutputbuffer:%d,stampUs:%d", index, previousStampUs));
                                                    mCodec.releaseOutputBuffer(index, previousStampUs);
                                                } else {
                                                    if (newSleepUs < 0) {
                                                        newSleepUs = 0;
                                                    }
//                                            Log.d(TAG,String.format("sleep:%d", newSleepUs/1000));
                                                    Thread.sleep(newSleepUs / 1000);
                                                    mCodec.releaseOutputBuffer(index, i420callback == null);
                                                }

                                                if (firstTime) {
                                                    Log.i(TAG, String.format("POST VIDEO_DISPLAYED!!!"));
                                                    ResultReceiver rr = mRR;
                                                    if (rr != null) {
                                                        Bundle data = new Bundle();
                                                        data.putInt(KEY_VIDEO_DECODE_TYPE, 1);
                                                        rr.send(RESULT_VIDEO_DISPLAYED, data);
                                                    }
                                                }
                                                previousStampUs = info.presentationTimeUs;
                                        }

                                    }
                                    while (frameInfo != null || index < MediaCodec.INFO_TRY_AGAIN_LATER);
                                } catch (IllegalStateException ex) {
                                    // mediacodec error...

                                    ex.printStackTrace();

                                    Log.e(TAG, String.format("init codec error due to %s", ex.getMessage()));

                                    if (mCodec != null)
                                        mCodec.release();
                                    mCodec = null;

                                    if (displayer != null) {
                                        displayer.close();
                                        Log.i(TAG, "AAAA 1217 displayer.close()");
                                    }
                                    displayer = null;

                                    final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
                                    decoder.create(mSurface, initFrameInfo.codec == EASY_SDK_VIDEO_CODEC_H264);
                                    mDecoder = decoder;
                                    continue;
                                }

                            }
                            break;
                        } while (true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (mCodec != null) {
//                        mCodec.stop();
                        mCodec.release();
                    }

                    if (mDecoder != null) {
                        mDecoder.close();
                        Log.i(TAG, "AAAA 1238 mDecoder.close();");
                    }

                    if (displayer != null) {
                        displayer.close();
                        Log.i(TAG, "AAAA 1243 displayer.close();");
                    }
                }
            }
        };

        mThread.start();
    }

    private static final long fixSleepTime(long sleepTimeUs, long totalTimestampDifferUs, long delayUs) {
        if (totalTimestampDifferUs < 0l) {
            Log.w(TAG, String.format("totalTimestampDifferUs is:%d, this should not be happen.", totalTimestampDifferUs));
            totalTimestampDifferUs = 0;
        }

        double dValue = ((double) (delayUs - totalTimestampDifferUs)) / 1000000d;
        double radio = Math.exp(dValue);
        double r = sleepTimeUs * radio + 0.5f;
        Log.i(TAG, String.format("%d,%d,%d->%d", sleepTimeUs, totalTimestampDifferUs, delayUs, (int) r));
        return (long) r;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public synchronized void startRecord(String path) {
        if (mMediaInfo == null || mWidth == 0 || mHeight == 0 || mCSD0 == null)
            return;

        mRecordingPath = path;
        EasyMuxer2 muxer2 = new EasyMuxer2();
        mMuxerCuttingMillis = 0l;
        mRecordingStatus = 0;
        muxerPausedMillis = 0;
        ByteBuffer csd1 = this.mCSD1;
        if (csd1 == null) csd1 = ByteBuffer.allocate(0);
        byte[] extra = new byte[mCSD0.capacity() + csd1.capacity()];
        mCSD0.clear();
        csd1.clear();
        mCSD0.get(extra, 0, mCSD0.capacity());
        csd1.get(extra, mCSD0.capacity(), csd1.capacity());

        int r = muxer2.create(path, mMediaInfo.videoCodec == EASY_SDK_VIDEO_CODEC_H265 ? VIDEO_TYPE_H265 : VIDEO_TYPE_H264, mWidth, mHeight, extra, mMediaInfo.sample, mMediaInfo.channel);
        if (r != 0) {
            Log.w(TAG, "create muxer2:" + r);
            return;
        }

        mMuxerWaitingKeyVideo = true;
        this.muxer2 = muxer2;

        ResultReceiver rr = mRR;
        if (rr != null) {
            rr.send(RESULT_RECORD_BEGIN, null);
        }
    }

    public synchronized void pauseRecord() {
        if (mRecordingStatus != -1) {
            mRecordingStatus = -1;
            muxerPausedMillis = SystemClock.elapsedRealtime();
        }
    }

    public synchronized void resumeRecord() {
        if (mRecordingStatus == -1) {
            mMuxerWaitingKeyVideo = true;
            mRecordingStatus = 1;
        }
    }

    private static int getSampleIndex(int sample) {
        for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; i++) {
            if (sample == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
                return i;
            }
        }
        return -1;
    }

    private void pumpAACSample(Client.FrameInfo frameInfo) {
        EasyMuxer muxer = mObject;
        if (muxer == null) return;
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        bi.offset = frameInfo.offset;
        bi.size = frameInfo.length;
        ByteBuffer buffer = ByteBuffer.wrap(frameInfo.buffer, bi.offset, bi.size);
        bi.presentationTimeUs = frameInfo.stamp;

        try {
            if (!frameInfo.audio) {
                throw new IllegalArgumentException("frame should be audio!");
            }
            if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC) {
                throw new IllegalArgumentException("audio codec should be aac!");
            }
            bi.offset += 7;
            bi.size -= 7;
            muxer.pumpStream(buffer, bi, false);
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
        }
    }

    private synchronized void pumpPCMSample(byte[] pcm, int length, long stampUS) {
        EasyMuxer2 muxer2 = this.muxer2;
        if (muxer2 == null)
            return;

        if (mRecordingStatus < 0)
            return;

        if (mMuxerWaitingKeyVideo) {
            Log.i(TAG, "writeFrame ignore due to no key frame!");
            return;
        }

        long timeStampMillis = stampUS / 1000;
        timeStampMillis -= mMuxerCuttingMillis;
        timeStampMillis = Math.max(0, timeStampMillis);
        int r = muxer2.writeFrame(EasyMuxer2.AVMEDIA_TYPE_AUDIO, pcm, 0, length, timeStampMillis);
        Log.i(TAG, "writeFrame audio ret:" + r);
    }


    private synchronized void pumpVideoSample(Client.FrameInfo frameInfo) {
        EasyMuxer2 muxer2 = this.muxer2;
        if (muxer2 == null) return;
        if (mRecordingStatus < 0) return;
        if (mMuxerWaitingKeyVideo) {
            if (frameInfo.type == 1) {
                mMuxerWaitingKeyVideo = false;
                if (mRecordingStatus == 1) {
                    mMuxerCuttingMillis += SystemClock.elapsedRealtime() - muxerPausedMillis;
                    mRecordingStatus = 0;
                }
            }
        }
        if (mMuxerWaitingKeyVideo) {
            Log.i(TAG, "writeFrame ignore due to no key frame!");
            return;
        }
        if (frameInfo.type == 1) {
//            frameInfo.offset = 60;
//            frameInfo.length -= 60;
        }
        long timeStampMillis = frameInfo.stamp / 1000;
        timeStampMillis -= mMuxerCuttingMillis;
        timeStampMillis = Math.max(0, timeStampMillis);
        int r = muxer2.writeFrame(EasyMuxer2.AVMEDIA_TYPE_VIDEO, frameInfo.buffer, frameInfo.offset, frameInfo.length, timeStampMillis);
        Log.i(TAG, "writeFrame video ret:" + r);
    }


    public synchronized void stopRecord() {
        mRecordingPath = null;
        mMuxerCuttingMillis = 0l;
        mRecordingStatus = 0;
        muxerPausedMillis = 0;
        EasyMuxer2 muxer2 = this.muxer2;
        if (muxer2 == null) return;
        this.muxer2 = null;
        muxer2.close();
        mObject = null;
        ResultReceiver rr = mRR;
        if (rr != null) {
            rr.send(RESULT_RECORD_END, null);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onSourceCallBack(int _channelId, int _channelPtr, int _frameType, Client.FrameInfo frameInfo) {
//        long begin = SystemClock.elapsedRealtime();
        try {
            onRTSPSourceCallBack1(_channelId, _channelPtr, _frameType, frameInfo);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
//            Log.d(TAG, String.format("onRTSPSourceCallBack %d", SystemClock.elapsedRealtime() - begin));
        }
    }

    public void onRTSPSourceCallBack1(int _channelId, int _channelPtr, int _frameType, Client.FrameInfo frameInfo) {
        Thread.currentThread().setName("PRODUCER_THREAD");
        if (frameInfo != null) {
            mReceivedDataLength += frameInfo.length;
        }
        if (_frameType == Client.EASY_SDK_VIDEO_FRAME_FLAG) {
            //Log.d(TAG,String.format("receive video frame"));
            if (frameInfo.codec != EASY_SDK_VIDEO_CODEC_H264 && frameInfo.codec != EASY_SDK_VIDEO_CODEC_H265) {
                ResultReceiver rr = mRR;
                if (!mNotSupportedVideoCB && rr != null) {
                    mNotSupportedVideoCB = true;
                    rr.send(RESULT_UNSUPPORTED_VIDEO, null);
                }
                return;
            }
//            save2path(frameInfo.buffer, 0, frameInfo.length, "/sdcard/264.h264", true);
            if (frameInfo.width == 0 || frameInfo.height == 0) {
                return;
            }

            if (frameInfo.length >= 4) {
                if (frameInfo.buffer[0] == 0 && frameInfo.buffer[1] == 0 && frameInfo.buffer[2] == 0 && frameInfo.buffer[3] == 1) {
                    if (frameInfo.length >= 8) {
                        if (frameInfo.buffer[4] == 0 && frameInfo.buffer[5] == 0 && frameInfo.buffer[6] == 0 && frameInfo.buffer[7] == 1) {
                            frameInfo.offset += 4;
                            frameInfo.length -= 4;
                        }
                    }
                }
            }

//            int offset = frameInfo.offset;
//            byte nal_unit_type = (byte) (frameInfo.buffer[offset + 4] & (byte) 0x1F);
//            if (nal_unit_type == 7 || nal_unit_type == 5) {
//                Log.i(TAG,String.format("recv I frame"));
//            }

            if (frameInfo.type == 1) {
                Log.i(TAG, String.format("recv I frame"));
            }

//            boolean firstFrame = mNewestStample == 0;
            mNewestStample = frameInfo.stamp;
            frameInfo.audio = false;
            if (mWaitingKeyFrame) {

                ResultReceiver rr = mRR;
                Bundle bundle = new Bundle();
                bundle.putInt(EXTRA_VIDEO_WIDTH, frameInfo.width);
                bundle.putInt(EXTRA_VIDEO_HEIGHT, frameInfo.height);
                mWidth = frameInfo.width;
                mHeight = frameInfo.height;
                Log.i(TAG, String.format("RESULT_VIDEO_SIZE:%d*%d", frameInfo.width, frameInfo.height));
                if (rr != null) rr.send(RESULT_VIDEO_SIZE, bundle);


                Log.i(TAG, String.format("width:%d,height:%d", mWidth, mHeight));

                if (frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264) {
                    byte[] dataOut = new byte[128];
                    int[] outLen = new int[]{128};
                    int result = getXPS(frameInfo.buffer, 0, 256, dataOut, outLen, 7);
                    if (result >= 0) {
                        ByteBuffer csd0 = ByteBuffer.allocate(outLen[0]);
                        csd0.put(dataOut, 0, outLen[0]);
                        csd0.clear();
                        mCSD0 = csd0;
                        Log.i(TAG, String.format("CSD-0 searched"));
                    }
                    outLen[0] = 128;
                    result = getXPS(frameInfo.buffer, 0, 256, dataOut, outLen, 8);
                    if (result >= 0) {
                        ByteBuffer csd1 = ByteBuffer.allocate(outLen[0]);
                        csd1.put(dataOut, 0, outLen[0]);
                        csd1.clear();
                        mCSD1 = csd1;
                        Log.i(TAG, String.format("CSD-1 searched"));
                    }
                    if (false) {
                        int off = (result - frameInfo.offset);
                        frameInfo.offset += off;
                        frameInfo.length -= off;
                    }
                } else {
                    byte[] spsPps = getvps_sps_pps(frameInfo.buffer, 0, 256);
                    if (spsPps != null) {
                        mCSD0 = ByteBuffer.wrap(spsPps);
                    }
                }

                if (frameInfo.type != 1) {
                    Log.w(TAG, String.format("discard p frame."));
                    return;
                }
                mWaitingKeyFrame = false;
                synchronized (this) {
                    if (!TextUtils.isEmpty(mRecordingPath) && mObject == null) {
                        startRecord(mRecordingPath);
                    }
                }
            } else {
                int width = frameInfo.width;
                int height = frameInfo.height;
                if (width != 0 && height != 0)
                    if (width != mWidth || height != mHeight) {
                        // resolution change...
                        ResultReceiver rr = mRR;
                        Bundle bundle = new Bundle();
                        bundle.putInt(EXTRA_VIDEO_WIDTH, frameInfo.width);
                        bundle.putInt(EXTRA_VIDEO_HEIGHT, frameInfo.height);
                        mWidth = frameInfo.width;
                        mHeight = frameInfo.height;
                        Log.i(TAG, String.format("RESULT_VIDEO_SIZE:%d*%d", frameInfo.width, frameInfo.height));
                        if (rr != null) rr.send(RESULT_VIDEO_SIZE, bundle);
                    }
            }
//            Log.d(TAG, String.format("queue size :%d", mQueue.size()));
            try {
                mQueue.put(frameInfo);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (_frameType == Client.EASY_SDK_AUDIO_FRAME_FLAG) {
            mNewestStample = frameInfo.stamp;
            frameInfo.audio = true;
            if (true) {
                if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC &&
                        frameInfo.codec != EASY_SDK_AUDIO_CODEC_G711A &&
                        frameInfo.codec != EASY_SDK_AUDIO_CODEC_G711U &&
                        frameInfo.codec != EASY_SDK_AUDIO_CODEC_G726) {
                    ResultReceiver rr = mRR;
                    if (!mNotSupportedAudioCB && rr != null) {
                        mNotSupportedAudioCB = true;
                        if (rr != null) {
                            rr.send(RESULT_UNSUPPORTED_AUDIO, null);
                        }
                    }
                    return;
                }

            }
            Log.d(TAG, String.format("queue size :%d", mQueue.size()));
            try {
                mQueue.put(frameInfo);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (_frameType == 0) {
            // time out...
            if (!mTimeout) {
                mTimeout = true;

                ResultReceiver rr = mRR;
                if (rr != null) rr.send(RESULT_TIMEOUT, null);
            }
        } else if (_frameType == Client.EASY_SDK_EVENT_FRAME_FLAG) {
            ResultReceiver rr = mRR;
            Bundle resultData = new Bundle();
            resultData.putString("event-msg", new String(frameInfo.buffer));
            if (rr != null) rr.send(RESULT_EVENT, null);
        }
    }

    @Override
    public void onMediaInfoCallBack(int _channelId, Client.MediaInfo mi) {
        mMediaInfo = mi;
        Log.i(TAG, String.format("MediaInfo fetchd\n%s", mi));
    }

    @Override
    public void onEvent(int channel, int err, int info) {
        ResultReceiver rr = mRR;
        Bundle resultData = new Bundle();
        /*
            int state = 0;
        int err = EasyRTSP_GetErrCode(fRTSPHandle);
		// EasyRTSPClient开始进行连接，建立EasyRTSPClient连接线程
		if (NULL == _pBuf && NULL == _frameInfo)
		{
			LOGD("Recv Event: Connecting...");
			state = 1;
		}

		// EasyPlayerClient RTSPClient连接错误，错误码通过EasyRTSP_GetErrCode()接口获取，比如404
		else if (NULL != _frameInfo && _frameInfo->codec == EASY_SDK_EVENT_CODEC_ERROR)
		{
			LOGD("Recv Event: Error:%d ...\n", err);
			state = 2;
		}

		// EasyRTSPClient连接线程退出，此时上层应该停止相关调用，复位连接按钮等状态
		else if (NULL != _frameInfo && _frameInfo->codec == EASY_SDK_EVENT_CODEC_EXIT)
		{
			LOGD("Recv Event: Exit,Error:%d ...", err);
			state = 3;
		}

        * */
        switch (info) {
            case 1:
                resultData.putString("event-msg", "连接中...");
                break;
            case 2:
                resultData.putInt("errorcode", err);
                resultData.putString("event-msg", String.format("错误：%d", err));
                break;
            case 3:
                resultData.putInt("errorcode", err);
                resultData.putString("event-msg", String.format("线程退出。%d", err));
                break;
        }
        if (rr != null) rr.send(RESULT_EVENT, resultData);
    }

    /**
     * 旋转YUV格式数据
     *
     * @param src    YUV数据
     * @param format 0，420P；1，420SP
     * @param width  宽度
     * @param height 高度
     * @param degree 旋转度数
     */
    private static void yuvRotate(byte[] src, int format, int width, int height, int degree) {
        int offset = 0;
        if (format == 0) {
            JNIUtil.rotateMatrix(src, offset, width, height, degree);
            offset += (width * height);
            JNIUtil.rotateMatrix(src, offset, width / 2, height / 2, degree);
            offset += width * height / 4;
            JNIUtil.rotateMatrix(src, offset, width / 2, height / 2, degree);
        } else if (format == 1) {
            JNIUtil.rotateMatrix(src, offset, width, height, degree);
            offset += width * height;
            JNIUtil.rotateShortMatrix(src, offset, width / 2, height / 2, degree);
        }
    }
}