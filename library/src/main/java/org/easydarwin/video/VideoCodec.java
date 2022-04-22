package org.easydarwin.video;

import java.nio.ByteBuffer;

/**
 * Created by John on 2017/1/5.
 */
public class VideoCodec {

    static {
        System.loadLibrary("proffmpeg");
        System.loadLibrary("VideoCodecer");
    }

    public static final int DECODER_H264 = 0;
    public static final int DECODER_H265 = 1;

    private native long create(Object surface, int codec);

    private native void close(long handle);

    protected long mHandle;

    private native int decode(long handle, byte[] in, int offset, int length, int[] size);

    private native ByteBuffer decodeYUV(long handle, byte[] in, int offset, int length, int[] size);

    private native void releaseYUV(ByteBuffer buffer);

    private native void decodeYUV2(long handle, ByteBuffer buffer, int width, int height);

    public int decoder_create(Object surface, int codec) {
        mHandle = create(surface, codec);
        if (mHandle != 0) {
            return 0;
        }

        return -1;
    }

    public int decoder_decode(byte[] in, int offset, int length, int[] size) {
        int result = decode(mHandle, in, offset, length, size);
        return result;
    }

    public ByteBuffer decoder_decodeYUV(byte[] in, int offset, int length, int[] size) {
        ByteBuffer buffer = decodeYUV(mHandle, in, offset, length, size);
        return buffer;
    }

    public void decoder_releaseBuffer(ByteBuffer buffer) {
        releaseYUV(buffer);
    }

    public void decoder_decodeBuffer(ByteBuffer buffer, int width, int height) {
        decodeYUV2(mHandle, buffer, width, height);
    }

    public void decoder_close() {
        if (mHandle == 0) {
            return;
        }

        close(mHandle);
        mHandle = 0;
    }

    public static class VideoDecoderLite extends VideoCodec {

        private int[] mSize;
        private Object surface;

        public void create(Object surface, boolean h264) {
//            if (surface == null) {
//                throw new NullPointerException("surface is null!");
//            }
            this.surface = surface;
            decoder_create(surface, h264 ? 0 : 1);
            mSize = new int[2];
        }

        public void close() {
            decoder_close();
        }

        protected int decodeFrame(Client.FrameInfo aFrame, int[] size) {
            int nRet = 0;
            nRet = decoder_decode(aFrame.buffer, aFrame.offset, aFrame.length, size);
            return nRet;
        }

        protected ByteBuffer decodeFrameYUV(Client.FrameInfo aFrame, int[] size) {
            return decoder_decodeYUV(aFrame.buffer, aFrame.offset, aFrame.length, size);
        }

        protected void releaseBuffer(ByteBuffer buffer) {
            decoder_releaseBuffer(buffer);
        }
    }
}
