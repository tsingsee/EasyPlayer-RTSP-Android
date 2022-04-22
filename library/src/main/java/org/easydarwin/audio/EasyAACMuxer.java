package org.easydarwin.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import org.easydarwin.video.EasyMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 对EasyMuxer的扩展。支持对PCM格式的音频打包。
 */
public class EasyAACMuxer extends EasyMuxer {
    MediaCodec mMediaCodec;
    String TAG = "EasyAACMuxer";

    protected MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    protected ByteBuffer[] mBuffers = null;

    private MediaFormat mAudioFormat;

    public EasyAACMuxer(String path, boolean hasAudio, long durationMillis) {
        super(path, hasAudio, durationMillis);
    }

    @Override
    public synchronized void addTrack(MediaFormat format, boolean isVideo) {
        super.addTrack(format, isVideo);
        if (!isVideo){
            mAudioFormat = format;
        }
    }

    public synchronized void pumpPCMStream(byte []pcm, int length, long timeUs) throws IOException {

        if (mMediaCodec == null) {// 启动AAC编码器。这里用MediaCodec来编码
            if (mAudioFormat == null) return;
            mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            Log.i(TAG, String.valueOf(mAudioFormat));
            mAudioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 16000);
//            mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 320);

            mMediaCodec.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
            mBuffers = mMediaCodec.getOutputBuffers();
        }
        int index = 0;
        // 将pcm编码成AAC
        do {
            index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 1000);
            if (index >= 0) {
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    continue;
                }
                if (mBufferInfo.presentationTimeUs == 0){
                    continue;
                }
                if (VERBOSE) Log.d(TAG,String.format("dequeueOutputBuffer data length:%d,tmUS:%d", mBufferInfo.size, mBufferInfo.presentationTimeUs));
                ByteBuffer outputBuffer = mBuffers[index];
                // ok,编码成功了。将AAC数据写入muxer.
                pumpStream(outputBuffer, mBufferInfo, false);
                mMediaCodec.releaseOutputBuffer(index, false);
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mBuffers = mMediaCodec.getOutputBuffers();
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.v(TAG, "output format changed...");
                MediaFormat newFormat = mMediaCodec.getOutputFormat();
                Log.v(TAG, "output format changed..." + newFormat);
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.v(TAG, "No buffer available...");
            } else {
                Log.e(TAG, "Message: " + index);
            }
        } while (index >= 0 && !Thread.currentThread().isInterrupted());

        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        do {
            index = mMediaCodec.dequeueInputBuffer(1000);
            if (index >= 0) {
                inputBuffers[index].clear();
                inputBuffers[index].put(pcm, 0, length);
                if (VERBOSE) Log.d(TAG,String.format("queueInputBuffer pcm data length:%d,tmUS:%d", length, timeUs));
                mMediaCodec.queueInputBuffer(index, 0, length, timeUs, 0);
            }
        }
        while (!Thread.currentThread().isInterrupted() && index < 0);
    }

    @Override
    public synchronized void release() {
        if (mMediaCodec != null) mMediaCodec.release();
        mMediaCodec = null;
        super.release();
    }
}
