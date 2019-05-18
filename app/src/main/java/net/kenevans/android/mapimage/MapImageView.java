//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.mapimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

/**
 * Based on an example at:
 *
 * @link http://stackoverflow
 * .com/questions/2537238/how-can-i-get-zoom-functionality
 * -for-images
 */
public class MapImageView extends SubsamplingScaleImageView implements
        IConstants {
    private Bitmap mLocationCursor;
    private PointF mLocationPoint;
    private Paint mPaint;

    /**
     * Use this constructor when calling from code.
     *
     * @param context The contexxt to use.
     */
    public MapImageView(Context context) {
        super(context);
        super.setClickable(true);
        init();
    }

    /**
     * Use this constructor when inflating from resources.
     *
     * @see android.view.View#View(android.content.Context,
     * android.util.AttributeSet)
     */
    public MapImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setClickable(true);
        init();
    }

    /**
     * Does the additional setup in the constructor.
     */
    private void init() {
//        float density = getResources().getDisplayMetrics().densityDpi;
        mLocationCursor = BitmapFactory.decodeResource(this.getResources(), R
                .drawable.location);
    }

    public void setLocation(PointF locationPoint) {
        this.mLocationPoint = locationPoint;
        if (mLocationCursor == null) {
            init();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        Log.d(TAG, this.getClass().getSimpleName()
//                + ": onDraw:" + " mLocationPoint=" + mLocationPoint.x + ","
// + mLocationPoint.y);
        super.onDraw(canvas);
        // Don't draw cursor before image is ready so it doesn't move around
        // during setup.
        if (!isReady()) {
            return;
        }
        if (mPaint == null) {
            mPaint = new Paint();
        }
        mPaint.setAntiAlias(true);
        if (mLocationPoint != null && mLocationCursor != null) {
            PointF vPoint = sourceToViewCoord(mLocationPoint);
            float vX = vPoint.x - (mLocationCursor.getWidth() / 2);
            float vY = vPoint.y - mLocationCursor.getHeight() / 2;
//            Log.d(TAG, "vPoint=" + mLocationPoint.x + "," + mLocationPoint.y +
//                    " v=" + vX + "," + vY);
//            Log.d(TAG, "mLocationCursor="
//                    + mLocationCursor.getWidth() + "," + mLocationCursor
// .getHeight());
//            Log.d(TAG, "mLocationCursor="
//                    + mLocationCursor.getScaledWidth(canvas) + ","
//                    + mLocationCursor.getScaledHeight(canvas));
            canvas.drawBitmap(mLocationCursor, vX, vY, mPaint);
        }
    }

}

