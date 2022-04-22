package org.easydarwin.easyplayer.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Environment;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.util.FileUtil;
import org.easydarwin.easyplayer.views.OverlayCanvasView;
import org.easydarwin.sw.JNIUtil;
import org.easydarwin.video.Client;
import org.easydarwin.video.EasyPlayerClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by apple on 2017/12/30.
 */
public class YUVExportFragment extends PlayFragment implements EasyPlayerClient.I420DataCallback {

    OverlayCanvasView canvas;
    boolean recordPaused = false;

    public static YUVExportFragment newInstance(String url, int type, ResultReceiver rr) {
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, url);
        args.putInt(ARG_TRANSPORT_MODE, type);
        args.putParcelable(ARG_PARAM3, rr);

        YUVExportFragment fragment = new YUVExportFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_play_overlay_canvas, container, false);
        cover = view.findViewById(R.id.surface_cover);
        canvas = view.findViewById(R.id.overlay_canvas);

        view.findViewById(R.id.start_or_stop_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int permissionCheck = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            1);
                    return;
                }

                if (mStreamRender == null) {
                    Toast.makeText(getActivity(), "未开始播放,录像失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (mStreamRender.isRecording()) {
                    mStreamRender.stopRecord();

                    Toast.makeText(getActivity(), "停止录像，路径：/sdcard/test.mp4", Toast.LENGTH_SHORT).show();
                } else {
                    mStreamRender.startRecord("/sdcard/test.mp4");

                    Toast.makeText(getActivity(), "开始录像，路径：/sdcard/test.mp4", Toast.LENGTH_SHORT).show();
                }
            }
        });

        view.findViewById(R.id.pause_or_resume_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStreamRender == null) {
                    Toast.makeText(getActivity(), "未开始录像1", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!mStreamRender.isRecording()) {
                    Toast.makeText(getActivity(), "未开始录像2", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (recordPaused)
                    mStreamRender.resumeRecord();
                else
                    mStreamRender.pauseRecord();

                recordPaused = !recordPaused;

                if (recordPaused) {
                    Toast.makeText(getActivity(), "录像暂停", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "录像恢复", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return view;
    }

    @Override
    protected void startRending(SurfaceTexture surface) {
        mStreamRender = new EasyPlayerClient(getContext(), new Surface(surface), mResultReceiver, this);

        boolean autoRecord = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("auto_record", false);

        File f = new File(FileUtil.getMoviePath(mUrl));
        f.mkdirs();

        try {
            mStreamRender.start(mUrl,
                    mType,
                    sendOption,
                    Client.EASY_SDK_VIDEO_FRAME_FLAG | Client.EASY_SDK_AUDIO_FRAME_FLAG,
                    "",
                    "",
                    autoRecord ? FileUtil.getMovieName(mUrl).getPath() : null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        sendResult(RESULT_REND_START, null);
    }

    @Override
    public void onMatrixChanged(Matrix matrix, RectF rect) {
        super.onMatrixChanged(matrix, rect);

        if (canvas != null) {
            canvas.setTransMatrix(matrix);
        }
    }

    public void toggleDraw() {
        canvas.toggleDrawable();
    }

    /**
     * 这个buffer对象在回调结束之后会变无效.所以不可以把它保存下来用.如果需要保存,必须要创建新buffer,并拷贝数据.
     *
     * @param buffer
     */
    @Override
    public void onI420Data(final ByteBuffer buffer) {
        Log.i(TAG, "I420 data length :" + buffer.capacity());

//        writeToFile("/sdcard/tmp.yuv", buffer);

//        if (i++ == 10) {
//            i = 0;
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    saveImage(buffer);
//                }
//            }).start();
//        }
    }

//    int i = 0;
//    private String path = Environment.getExternalStorageDirectory() + "/EasyPlayerRTSP";
//
//    private void writeToFile(String path, ByteBuffer buffer) {
//        try {
//            FileOutputStream fos = new FileOutputStream(path, true);
//            byte[] in = new byte[buffer.capacity()];
//            buffer.clear();
//            buffer.get(in);
//            fos.write(in);
//            fos.close();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    private void saveImage(ByteBuffer buffer) {
//        byte[] in = new byte[buffer.capacity()];
//        buffer.clear();
//        buffer.get(in);
//
//        byte[] dst = new byte[buffer.capacity()];
//        JNIUtil.ConvertFromI420(in, dst, mWidth, mHeight, 2);
//
//        FileOutputStream outStream;
//        try {
//            YuvImage yuvimage = new YuvImage(dst, ImageFormat.NV21, mWidth, mHeight, null);
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            yuvimage.compressToJpeg(new Rect(0, 0, mWidth, mHeight), 80, baos);
//
//            outStream = new FileOutputStream(String.format(path + "/%d.jpg", System.currentTimeMillis()));
//            outStream.write(baos.toByteArray());
//            outStream.flush();
//            outStream.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//
//        }
//    }
}
