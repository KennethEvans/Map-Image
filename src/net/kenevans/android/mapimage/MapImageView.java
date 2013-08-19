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
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
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
	Matrix matrix = new Matrix();
	Matrix savedMatrix = new Matrix();

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
	int fitImageMode = IMAGEFITTED | IMAGECENTERED;
	// We can be in one of these 3 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	int mode = NONE;

	// Remember some things for zooming
	private PointF start = new PointF();
	private PointF mid = new PointF();
	private float oldDist = 1f;

	Context context;

	/**
	 * Use this constructor when calling from code.
	 * 
	 * @param context
	 */
	public MapImageView(Context context) {
		super(context);
		super.setClickable(true);
		this.context = context;
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
		this.context = context;
		init();
	}

	/**
	 * Does the additional setup in the constructor.
	 */
	private void init() {
		setImageMatrix(matrix);
		setScaleType(ScaleType.MATRIX);

		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// Handle touch events here...
				switch (event.getAction() & MotionEvent.ACTION_MASK) {
				case MotionEvent.ACTION_DOWN:
					savedMatrix.set(matrix);
					start.set(event.getX(), event.getY());
					Log.d(TAG, "mode=DRAG");
					mode = DRAG;
					break;
				case MotionEvent.ACTION_POINTER_DOWN:
					oldDist = spacing(event);
					Log.d(TAG, "oldDist=" + oldDist);
					if (oldDist > 10f) {
						savedMatrix.set(matrix);
						midPoint(mid, event);
						mode = ZOOM;
						Log.d(TAG, "mode=ZOOM");
					}
					break;
				case MotionEvent.ACTION_UP:
					int xDiff = (int) Math.abs(event.getX() - start.x);
					int yDiff = (int) Math.abs(event.getY() - start.y);
					if (xDiff < 8 && yDiff < 8) {
						performClick();
					}
				case MotionEvent.ACTION_POINTER_UP:
					mode = NONE;
					Log.d(TAG, "mode=NONE");
					break;
				case MotionEvent.ACTION_MOVE:
					if (mode == DRAG) {
						matrix.set(savedMatrix);
						matrix.postTranslate(event.getX() - start.x,
								event.getY() - start.y);
					} else if (mode == ZOOM) {
						float newDist = spacing(event);
						Log.d(TAG, "newDist=" + newDist);
						if (newDist > 10f) {
							matrix.set(savedMatrix);
							float scale = newDist / oldDist;
							matrix.postScale(scale, scale, mid.x, mid.y);
						}
					}
					break;
				}
				setImageMatrix(matrix);
				return true; // indicate event was handled
			}
		});
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onLayout:");
		super.onLayout(changed, left, top, right, bottom);
		// Layout should be done
		// Fit the image if specified
		if (fitImageMode != 0) {
			fitImage();
			fitImageMode = IMAGEUNMODIFIED;
		}
	}

	/**
	 * Sets the image to fit inside the view and centers it, depending on the
	 * value of fitImageMode. super.onMeasure must have been called first.
	 */
	public void fitImage() {
		Log.d(TAG, this.getClass().getSimpleName()
				+ ": fitImage: fitImageMode=" + fitImageMode);
		Drawable drawable = getDrawable();
		if (drawable == null) {
			return;
		}
		matrix.reset();
		int dWidth = drawable.getIntrinsicWidth();
		int dHeight = drawable.getIntrinsicHeight();
		// These should have been defined, but getWidth and getHeight may not
		// have been
		int vWidth = getMeasuredWidth();
		int vHeight = getMeasuredHeight();
		Log.d(TAG, this.getClass().getSimpleName() + ": fitImage: drawable: "
				+ dWidth + "," + dHeight + " view: " + vWidth + "," + vHeight);
		if (vHeight == 0 || vWidth == 0) {
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

			savedMatrix.set(matrix);
			matrix.set(savedMatrix);
			matrix.postScale(scale, scale, 0, 0);
			setImageMatrix(matrix);
		}

		// Center the image
		if ((fitImageMode & IMAGECENTERED) > 0) {
			float redundantYSpace = (float) vHeight - (scale * (float) dHeight);
			float redundantXSpace = (float) vWidth - (scale * (float) dWidth);

			redundantYSpace /= (float) 2;
			redundantXSpace /= (float) 2;

			savedMatrix.set(matrix);
			matrix.set(savedMatrix);
			matrix.postTranslate(redundantXSpace, redundantYSpace);
			setImageMatrix(matrix);
		}
	}

	// // TODO This doesn't seem to work as the width and height may not have
	// been
	// // determined when it is logical to call it
	// public void setImage(Bitmap bm, int displayWidth, int displayHeight) {
	// super.setImageBitmap(bm);
	//
	// // Fit to screen.
	// float scale;
	// if ((displayHeight / bm.getHeight()) >= (displayWidth / bm.getWidth())) {
	// scale = (float) displayWidth / (float) bm.getWidth();
	// } else {
	// scale = (float) displayHeight / (float) bm.getHeight();
	// }
	//
	// savedMatrix.set(matrix);
	// matrix.set(savedMatrix);
	// matrix.postScale(scale, scale, mid.x, mid.y);
	// setImageMatrix(matrix);
	//
	// // Center the image
	// float redundantYSpace = (float) displayHeight
	// - (scale * (float) bm.getHeight());
	// float redundantXSpace = (float) displayWidth
	// - (scale * (float) bm.getWidth());
	//
	// redundantYSpace /= (float) 2;
	// redundantXSpace /= (float) 2;
	//
	// savedMatrix.set(matrix);
	// matrix.set(savedMatrix);
	// matrix.postTranslate(redundantXSpace, redundantYSpace);
	// setImageMatrix(matrix);
	// }

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
					action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
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

}
