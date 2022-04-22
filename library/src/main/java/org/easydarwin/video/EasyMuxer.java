package org.easydarwin.video;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

/**
 * Created by John on 2017/1/10.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class EasyMuxer {

    public static final boolean VERBOSE = true;
    private static final String TAG = EasyMuxer.class.getSimpleName();
    private final String mFilePath;
    private boolean hasAudio;
    private MediaMuxer mMuxer;
    private final long durationMillis;
    private int index = 0;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private long mBeginMillis = 0L;
    private MediaFormat mVideoFormat;
    private MediaFormat mAudioFormat;


    private long video_stample = 0;
    private long audio_stample = 0;

    public EasyMuxer(String path, boolean hasAudio, long durationMillis) {
        if (TextUtils.isEmpty(path)){
            throw new InvalidParameterException("path should not be empty!");
        }
        if (path.toLowerCase().endsWith(".mp4")){
            path = path.substring(0, path.toLowerCase().lastIndexOf(".mp4"));
        }
        mFilePath = path;
        this.hasAudio = hasAudio;
        this.durationMillis = durationMillis;
        Object mux = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mux = new MediaMuxer(path + "-" + index++ + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mMuxer = (MediaMuxer) mux;
        }
    }
    public synchronized void addTrack(MediaFormat format, boolean isVideo) {
        // now that we have the Magic Goodies, start the muxer
        if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1)
            throw new RuntimeException("already add all tracks");
        int track = mMuxer.addTrack(format);
        if (VERBOSE)
            Log.i(TAG, String.format("addTrack %s result %d", isVideo ? "video" : "audio", track));
        if (isVideo) {
            mVideoFormat = format;
            mVideoTrackIndex = track;
            if (mAudioTrackIndex != -1 || !hasAudio) {
                if (VERBOSE)
                    Log.i(TAG, "both audio and video added,and muxer is started");
                mMuxer.start();
            }
        } else {
            mAudioFormat = format;
            mAudioTrackIndex = track;
            if (mVideoTrackIndex != -1) {
                mMuxer.start();
            }
        }
    }

    public synchronized void pumpStream(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, boolean isVideo)  {
        if (mMuxer == null) Log.w(TAG,"muxer is null!");
        if (mVideoTrackIndex == -1) {
            Log.i(TAG, String.format("pumpStream [%s] but muxer is not start.ignore..", isVideo ? "video" : "audio"));
            return;
        }
        if (mAudioTrackIndex == -1 && hasAudio) {
            Log.i(TAG, String.format("pumpStream [%s] but muxer is not start.ignore..", isVideo ? "video" : "audio"));
            return;
        }
        if (isVideo && mBeginMillis == 0L){   // 首帧需要是关键帧
            if ((bufferInfo.flags & BUFFER_FLAG_KEY_FRAME) == 0){
                Log.i(TAG, String.format("pumpStream [%s] but key frame not GOTTEN.ignore..", isVideo ? "video" : "audio"));
                return;
            }
        }
        if (!isVideo && mBeginMillis == 0L){
            Log.i(TAG, String.format("pumpStream [%s] but video frame not GOTTEN.ignore..", isVideo ? "video" : "audio"));
            return;
        }
        if (isVideo && mBeginMillis == 0L){
            mBeginMillis = SystemClock.elapsedRealtime();
        }
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
        } else if (bufferInfo.size != 0) {
            if (isVideo && mVideoTrackIndex == -1) {
                throw new InvalidParameterException("muxer hasn't started");
            }

            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
            if (VERBOSE)
                Log.d(TAG, String.format("sent %s [" + bufferInfo.size + "] with timestamp:[%d] to muxer", isVideo ? "video" : "audio", bufferInfo.presentationTimeUs / 1000));

            if (isVideo){
                if (video_stample != 0){
                    if (bufferInfo.presentationTimeUs - video_stample <= 0){
                        Log.w(TAG,"video timestample goback, ignore!");
                        return;
                    }
                    video_stample = bufferInfo.presentationTimeUs;
                }else{
                    video_stample = bufferInfo.presentationTimeUs;
                }
            }else {
                if (audio_stample != 0){
                    if (bufferInfo.presentationTimeUs - audio_stample <= 0){
                        Log.w(TAG,"audio timestample goback, ignore!");
                        return;
                    }
                    audio_stample = bufferInfo.presentationTimeUs;
                }else{
                    audio_stample = bufferInfo.presentationTimeUs;
                }
            }
            mMuxer.writeSampleData(isVideo ? mVideoTrackIndex : mAudioTrackIndex, outputBuffer, bufferInfo);
        }

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            if (VERBOSE)
                Log.i(TAG, "BUFFER_FLAG_END_OF_STREAM received");
        }

        if (SystemClock.elapsedRealtime() - mBeginMillis >= durationMillis && isVideo && ((bufferInfo.flags & BUFFER_FLAG_KEY_FRAME) != 0)) {
            if (VERBOSE)
                Log.i(TAG, String.format("record file reach expiration.create new file:" + index));

            try {
                mMuxer.stop();
                mMuxer.release();
            }catch (Exception e){
                e.printStackTrace();
            }
            mMuxer = null;
            mVideoTrackIndex = mAudioTrackIndex = -1;
            try {
                mMuxer = new MediaMuxer(mFilePath + "-" + index++ + ".mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                addTrack(mVideoFormat, true);
                if (mAudioFormat != null) {
                    addTrack(mAudioFormat, false);
                }
                mBeginMillis = 0L;
                pumpStream(outputBuffer, bufferInfo, isVideo);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public synchronized void release() {
        if (mMuxer != null) {
            if (mVideoTrackIndex != -1 && (mAudioTrackIndex != -1 || !hasAudio)) {
                if (VERBOSE)
                    Log.i(TAG, String.format("muxer is started. now it will be stoped."));
                try {
                    mMuxer.stop();
                    mMuxer.release();
                } catch (IllegalStateException ex) {
                    ex.printStackTrace();
                }
            }
            mMuxer = null;
        }
        mBeginMillis = 0L;
    }
}
