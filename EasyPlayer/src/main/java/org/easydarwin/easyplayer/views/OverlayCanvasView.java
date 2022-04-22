package org.easydarwin.easyplayer.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 *
 */
public class OverlayCanvasView extends View {

    private RectF mArcRec;
    private Paint mPaint;
    private Matrix mDrawMatrix;
    private Path mPath;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mBitmapPaint;
    private OnTouchListener listener;
    private boolean enableDrawing = true;

    public OverlayCanvasView(Context context) {
        super(context);
        init(null, 0);
    }

    public OverlayCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public OverlayCanvasView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        setBackgroundColor(Color.TRANSPARENT);
        // Set up a default TextPaint object
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(0xFFFF0000);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(20);

        mPath = new Path();

        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        listener = new OnTouchListener() {
            RectF dstRect = new RectF();

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x1 = event.getX();
                float y1 = event.getY();

                float x = x1;
                float y = y1;

//                float cx0 = getWidth() * 0.5f;
//                float cy0 = getHeight() * 0.5f;
//
//                dstRect.set(0,0, getWidth(), getHeight());
//                mDrawMatrix.mapRect(dstRect);
//                float cx1 = dstRect.width() * 0.5f;
//                float cy1 = dstRect.height() * 0.5f;
//
//                x1 -= cx1;
//                y1 -= cy1;
//
////                y -= cy;
////                x -= cx;
//
//                Matrix m = new Matrix();
//                mDrawMatrix.invert(m);
//
//                float[] src = {x1, y1};
//                m.mapVectors(src);
//                x = src[0];
//                y = src[1];
//                x += cx0;
//                y += cy0;

//                x - cx,y-cy;  在当前点.

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touch_start(x, y);
                        invalidate();
                        break;
                    case MotionEvent.ACTION_MOVE:

                        touch_move(x, y);
                        invalidate();
                        break;
                    case MotionEvent.ACTION_UP:
                        touch_up();
                        invalidate();
                        break;
                }
                return true;
            }
        };
        setOnTouchListener(listener);
    }

    public void setTransMatrix(Matrix matrix) {
        mDrawMatrix = matrix;
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        float l = paddingLeft;
        float r = getWidth() - paddingRight;

        int h = getHeight() - paddingTop - paddingBottom;

        mArcRec = new RectF(l, paddingTop, r, r - l);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int saveCount = canvas.getSaveCount();
        canvas.save();

        if (mDrawMatrix != null) {
            if (mDrawMatrix != null) {
//                canvas.concat(mDrawMatrix);
            }
        }

        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mPath, mPaint);

        canvas.restoreToCount(saveCount);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        mCanvas = new Canvas(mBitmap);
    }

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private void touch_start(float x, float y) {
        //showDialog();
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }

    private void touch_up() {
        mPath.lineTo(mX, mY);
        // commit the path to our offscreen
        mCanvas.drawPath(mPath, mPaint);
        // kill this so we don't double draw
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        mPath.reset();
    }

    public void toggleDrawable() {
        enableDrawing = !enableDrawing;
        setOnTouchListener(enableDrawing?listener:null);
        if (enableDrawing){
            clean();
        }
    }

    public void clean() {
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        mCanvas.drawColor(Color.TRANSPARENT);
        invalidate();
    }
}
