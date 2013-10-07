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
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * Based on an example at:
 * 
 * @link 
 *       http://stackoverflow.com/questions/2537238/how-can-i-get-zoom-functionality
 *       -for-images
 * 
 */
public class MapImageView extends ImageView implements IConstants {
	// These matrices will be used to move and zoom image
	private Matrix mMatrix = new Matrix();
	private Matrix mSavedMatrix = new Matrix();

	private GestureDetector mGestureListener;
	private Bitmap mGPSCursor;

	private Location mLocation;
	private MapCalibration mMapCalibration;

	/** Flag to indicating no fitting or centering. */
	public static final int IMAGEUNMODIFIED = 0x00;
	/** Flag to indicating centering the image. */
	public static final int IMAGECENTERED = 0x01;
	/** Flag to fitting the image. */
	public static final int IMAGEFITTED = 0x10;

	/**
	 * Determines how the image is to be modified to the view when onMeasure is
	 * next called. Value is one of (IMAGEUNMODIFIED, IMAGECENTERED,
	 * IMAGEFITTED). It only takes place when onMeasure is called.
	 */
	private int fitImageMode = IMAGEFITTED | IMAGECENTERED;
	// We can be in one of these 3 states
	private static final int NONE = 0;
	private static final int DRAG = 1;
	private static final int ZOOM = 2;
	private int mode = NONE;

	// Remember some things for zooming
	private PointF start = new PointF();
	private PointF mid = new PointF();
	private float oldDist = 1f;

	private Context mContext;

	/**
	 * Use this constructor when calling from code.
	 * 
	 * @param mContext
	 */
	public MapImageView(Context context) {
		super(context);
		super.setClickable(true);
		this.mContext = context;
		init();
	}

	/**
	 * Use this constructor when inflating from resources.
	 * 
	 * @see android.view.View#View(android.content.Context,
	 *      android.util.AttributeSet)
	 */
	public MapImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		super.setClickable(true);
		this.mContext = context;
		init();
	}

	/**
	 * Does the additional setup in the constructor.
	 */
	private void init() {
		setImageMatrix(mMatrix);
		setScaleType(ScaleType.MATRIX);

		mGestureListener = new GestureDetector(mContext,
				new MapImageGestureListener());

		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent ev) {
				// Handle touch events here...
				switch (ev.getAction() & MotionEvent.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:
					mSavedMatrix.set(mMatrix);
					start.set(ev.getX(), ev.getY());
					// Log.d(TAG, "mode=DRAG");
					mode = DRAG;
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					oldDist = spacing(ev);
					// Log.d(TAG, "oldDist=" + oldDist);
					if (oldDist > 10f) {
						mSavedMatrix.set(mMatrix);
						midPoint(mid, ev);
						mode = ZOOM;
						// Log.d(TAG, "mode=ZOOM");
					}
					break;
				case MotionEvent.ACTION_UP:
					int xDiff = (int) Math.abs(ev.getX() - start.x);
					int yDiff = (int) Math.abs(ev.getY() - start.y);
					if (xDiff < 8 && yDiff < 8) {
						performClick();
					}
				case MotionEvent.ACTION_POINTER_UP:
					mode = NONE;
					// Log.d(TAG, "mode=NONE");
					break;
				case MotionEvent.ACTION_MOVE:
					if (mode == DRAG) {
						mMatrix.set(mSavedMatrix);
						mMatrix.postTranslate(ev.getX() - start.x, ev.getY()
								- start.y);
					} else if (mode == ZOOM) {
						float newDist = spacing(ev);
						// Log.d(TAG, "newDist=" + newDist);
						if (newDist > 10f) {
							mMatrix.set(mSavedMatrix);
							float scale = newDist / oldDist;
							mMatrix.postScale(scale, scale, mid.x, mid.y);
						}
					}
					break;
				}
				setImageMatrix(mMatrix);

				// Next send it to the GestureDetector
				// (Otherwise would return true)
				return mGestureListener.onTouchEvent(ev);
			}
		});

		// Create the cursor bitmap
		int size = 15;
		mGPSCursor = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setColor(0xFFFF0000);
		Canvas canvas = new Canvas(mGPSCursor);
		canvas.drawCircle(size / 2, size / 2, size / 2, paint);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		// Log.d(TAG, this.getClass().getSimpleName() + ": onLayout:");
		super.onLayout(changed, left, top, right, bottom);
		// Layout should be done
		// Fit the image if specified
		if (fitImageMode != IMAGEUNMODIFIED) {
			fitImage();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// Log.d(TAG, this.getClass().getSimpleName() + ": onDraw:");
		// Log.d(TAG,
		// "  mLocation="
		// + (mLocation == null ? "null" : "non-null")
		// + "  mMapCalibration="
		// + (mMapCalibration == null ? "null" : "non-null")
		// + "  transform="
		// + (mMapCalibration != null
		// && mMapCalibration.getTransform() != null ? "non-null"
		// : "null"));
		super.onDraw(canvas);
		if (mLocation == null || mMapCalibration == null
				|| mMapCalibration.getTransform() == null) {
			// Log.d(TAG, "  Bad location or calibration");
			return;
		}

		// Draw location cursor
		double lon = mLocation.getLongitude();
		double lat = mLocation.getLatitude();
		int[] locationVals = mMapCalibration.inverse(lon, lat);
		if (locationVals == null) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ "  locationVals  is null");
			return;
		}
		// Log.d(TAG, String.format("  locationVals=%d %d", locationVals[0],
		// locationVals[1]));
		Matrix matrix = mMatrix;
		float[] values = new float[9];
		matrix.getValues(values);
		// Log.d(TAG, String.format("    %10.3f %10.3f %10.3f", values[0],
		// values[1], values[2]));
		// Log.d(TAG, String.format("    %10.3f %10.3f %10.3f", values[3],
		// values[4], values[5]));
		// Log.d(TAG, String.format("    %10.3f %10.3f %10.3f", values[6],
		// values[7], values[8]));
		float x0 = values[2];
		float y0 = values[5];
		float scalex = values[0];
		float scaley = values[4];
		int x = locationVals[0];
		int y = locationVals[1];
		float x1 = x0 + x * scalex;
		float y1 = y0 + y * scaley;
		// Log.d(TAG, String.format("  drawing %.6f %.6f -> %d %d at %.3f %.3f",
		// lon, lat, x, y, x1, y1));
		canvas.drawBitmap(mGPSCursor, x1, y1, null);
	}

	/**
	 * Sets the image to fit inside the view and centers it, depending on the
	 * value of fitImageMode. super.onMeasure must have been called first.
	 */
	public void fitImage() {
		// Log.d(TAG, this.getClass().getSimpleName()
		// + ": fitImage: fitImageMode=" + fitImageMode);
		Drawable drawable = getDrawable();
		if (drawable == null) {
			return;
		}
		mMatrix.reset();
		int dWidth = drawable.getIntrinsicWidth();
		int dHeight = drawable.getIntrinsicHeight();
		// These should have been defined, but getWidth and getHeight may not
		// have been
		int vWidth = getMeasuredWidth();
		int vHeight = getMeasuredHeight();
		// Log.d(TAG, this.getClass().getSimpleName() + ": fitImage: drawable: "
		// + dWidth + "," + dHeight + " view: " + vWidth + "," + vHeight);
		if (vHeight == 0 || vWidth == 0 || dWidth == 0 || dHeight == 0) {
			return;
		}

		// Fit to view
		float scale = 1;
		if ((fitImageMode & IMAGEFITTED) > 0) {
			if ((vHeight / dHeight) >= (vWidth / dWidth)) {
				scale = (float) vWidth / (float) dWidth;
			} else {
				scale = (float) vHeight / (float) dHeight;
			}

			mSavedMatrix.set(mMatrix);
			mMatrix.set(mSavedMatrix);
			mMatrix.postScale(scale, scale, 0, 0);
			setImageMatrix(mMatrix);
		}

		// Center the image
		if ((fitImageMode & IMAGECENTERED) > 0) {
			float redundantYSpace = (float) vHeight - (scale * (float) dHeight);
			float redundantXSpace = (float) vWidth - (scale * (float) dWidth);

			redundantYSpace /= (float) 2;
			redundantXSpace /= (float) 2;

			mSavedMatrix.set(mMatrix);
			mMatrix.set(mSavedMatrix);
			mMatrix.postTranslate(redundantXSpace, redundantYSpace);
			setImageMatrix(mMatrix);
		}
	}

	/**
	 * Resets the view to the starting value.
	 */
	public void reset() {
		mMatrix.reset();
		fitImage();
		forceLayout();
	}

	/** Determines the space between the first two fingers */
	private float spacing(MotionEvent event) {
		// ...
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		// Formerly used FloatMath
		return (float) Math.sqrt(x * x + y * y);
	}

	/** Calculates the mid point of the first two fingers */
	private void midPoint(PointF point, MotionEvent event) {
		// ...
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}

	/**
	 * Shows an event in the LogCat view, for debugging.
	 * 
	 * @param event
	 */
	public static void dumpEvent(MotionEvent event) {
		String names[] = { "DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE",
				"POINTER_DOWN", "POINTER_UP", "7?", "8?", "9?" };
		StringBuilder sb = new StringBuilder();
		int action = event.getAction();
		int actionCode = action & MotionEvent.ACTION_MASK;
		sb.append("event ACTION_").append(names[actionCode]);
		if (actionCode == MotionEvent.ACTION_POINTER_DOWN
				|| actionCode == MotionEvent.ACTION_POINTER_UP) {
			sb.append("(pid ").append(
					action >> MotionEvent.ACTION_POINTER_INDEX_SHIFT);
			sb.append(")");
		}
		sb.append("[");
		for (int i = 0; i < event.getPointerCount(); i++) {
			sb.append("#").append(i);
			// May be a problem with API 1.6 or less (Donut, Cupcake)
			sb.append("(pid ").append(event.getPointerId(i));
			sb.append(")=").append((int) event.getX(i));
			sb.append(",").append((int) event.getY(i));
			if (i + 1 < event.getPointerCount())
				sb.append(";");
		}
		sb.append("]");
		Log.d(TAG, sb.toString());
	}

	/**
	 * Gets the value of fitImageMode.
	 * 
	 * @see #fitImageMode
	 * @return The value of fitImageMode.
	 */
	public int getFitImageMode() {
		return fitImageMode;
	}

	/**
	 * Sets the value of fitImageMode. The value will be reset to
	 * IMAGEUNMODIFIED after onMeasure is next called.
	 * 
	 * @param fitImageMode
	 * @see #fitImageMode
	 */
	public void setFitImageMode(int fitImageMode) {
		this.fitImageMode = fitImageMode;
	}

	public void setLocation(Location location, MapCalibration mapCalibration) {
		this.mLocation = location;
		this.mMapCalibration = mapCalibration;
	}

	public Location getLocation() {
		return mLocation;
	}

	public void setMapCalibration(MapCalibration mapCalibration) {
		this.mMapCalibration = mapCalibration;
	}

	public MapCalibration getMapCalibration() {
		return mMapCalibration;
	}

	/**
	 * Gesture detector. Based on an example at<br>
	 * <br>
	 * http://www.codeshogun.com/blog/2009
	 * /04/16/how-to-implement-swipe-action-in-android/
	 */
	class MapImageGestureListener extends SimpleOnGestureListener {
		@Override
		public boolean onDoubleTapEvent(MotionEvent ev) {
			// We only want ACTION_UP actions
			int action = ev.getAction() & MotionEvent.ACTION_MASK;
			if (action != MotionEvent.ACTION_UP) {
				return false;
			}
			reset();
			return true;
		}
	}

}
