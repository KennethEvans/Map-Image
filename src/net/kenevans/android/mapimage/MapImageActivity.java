package net.kenevans.android.mapimage;

import java.io.File;
import java.util.List;

import net.kenevans.android.mapimage.MapCalibration.MapData;
import net.kenevans.android.mapimage.MapCalibration.MapTransform;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

public class MapImageActivity extends Activity implements IConstants,
		LocationListener {
	// private MapImageView mImageView;
	private MapImageView mImageView;
	private LocationManager mLocationManager;
	private String mProvider;
	private boolean mUseLocation = false;
	private MapCalibration mMapCalibration;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
		super.onCreate(savedInstanceState);

		// // Remove title bar (Call before setContentView)
		// this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Remove notification bar (Call before setContentView)
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.main);
		mImageView = (MapImageView) findViewById(R.id.imageview);

		// Create a directory on the SD card if not already there
		setUserDirectory();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_map_image, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
		case R.id.open:
			selectFile();
			return true;
		case R.id.start_location:
			if (mUseLocation) {
				Utils.warnMsg(this, "Location is already started");
				return true;
			}
			mUseLocation = true;
			enableLocation();
			notifyLocationDisabled();
			return true;
		case R.id.stop_location:
			if (!mUseLocation) {
				Utils.warnMsg(this, "Location is already stopped");
				return true;
			}
			disableLocation();
			mUseLocation = false;
			mImageView.setLocation(null, null);
			mImageView.invalidate();
			return true;
		case R.id.current_location:
			Location location = mImageView.getLocation();
			if (location == null) {
				Utils.infoMsg(this, "Current location is not available");
			} else {
				double lon = location.getLongitude();
				double lat = location.getLatitude();
				String info = String.format("longitude=%.6f latitude=%.6f",
						lon, lat);
				Utils.infoMsg(this, info);
			}
			return true;
		case R.id.image_info:
			info();
			return true;
		case R.id.reset:
			reset();
			return true;
		}
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, this.getClass().getSimpleName()
				+ ": onResume: mUseLocation=" + mUseLocation);

		// Restore the state
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		mUseLocation = prefs.getBoolean(PREF_USE_LOCATION, false);
		String fileName = prefs.getString(PREF_FILENAME, null);
		Log.d(TAG, "  fileName=" + fileName);
		if (fileName == null) {
			mImageView.setMapCalibration(null);
			mImageView.setImageBitmap(null);
			mImageView.fitImage();
		} else {
			setNewImage(fileName);
			mImageView.setMapCalibration(null);
		}
		// mImageView.setFitImageMode(MapImageView.IMAGEFITTED
		// | MapImageView.IMAGECENTERED);

		// Location
		enableLocation();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onPause: mUseLocation="
				+ mUseLocation);
		super.onPause();
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putBoolean(PREF_USE_LOCATION, mUseLocation);
		editor.commit();
		disableLocation();
	}

	private void setUserDirectory() {
		try {
			File sdCardRoot = Environment.getExternalStorageDirectory();
			if (sdCardRoot.canWrite()) {
				File dir = new File(sdCardRoot, SD_CARD_MAP_IMAGE_DIRECTORY);
				if (dir.exists() && dir.isFile()) {
					Utils.errMsg(this, "Cannot create directory: " + dir
							+ "\nA file with that name exists.");
					return;
				}
				if (!dir.exists()) {
					Log.d(TAG, this.getClass().getSimpleName()
							+ ": create: dir=" + dir.getPath());
					boolean res = dir.mkdir();
					if (!res) {
						Utils.errMsg(this, "Cannot create directory: " + dir);
						return;
					}
				}
			} else {
				Utils.errMsg(this, "Cannot create directory "
						+ SD_CARD_MAP_IMAGE_DIRECTORY + " on SD card");
				return;
			}
		} catch (Exception ex) {
			Utils.excMsg(this, "Error creating directory "
					+ SD_CARD_MAP_IMAGE_DIRECTORY + " on SD card", ex);
		}
	}

	/**
	 * Get a Bitmap from a file. Static version that can be called by other
	 * Activities.
	 * 
	 * @param context
	 *            The context to use.
	 * @param cDay
	 * @return The Bitmap or null on failure.
	 */
	public static Bitmap getBitmap(Context context, File file) {
		Log.d(TAG, context.getClass().getSimpleName() + ": getBitmap");
		Bitmap bitmap = null;
		try {
			bitmap = BitmapFactory.decodeFile(file.getPath());
			if (bitmap == null) {
				Log.d(TAG, context.getClass().getSimpleName()
						+ ": getBitmap: Bitmap is null");
				Utils.errMsg(context, "Error reading image. Bitmap is null.");
			} else {
				Log.d(TAG, context.getClass().getSimpleName()
						+ ": getBitmap: Got " + file.getPath());
			}
		} catch (Throwable t) {
			// Need Throwable for OutOfMemory
			Log.d(TAG, context.getClass().getSimpleName()
					+ ": Error reading image", t);
			Utils.excMsg(context, "Error reading image", t);
		}
		return bitmap;
	}

	/**
	 * Resets to using the default image.
	 */
	private void reset() {
		if (mImageView != null) {
			mImageView.reset();
		}
	}

	/**
	 * Displays info about the current configuration
	 */
	private void info() {
		if (mImageView == null) {
			return;
		}
		try {
			String info = "";
			// Filename
			SharedPreferences prefs = getPreferences(MODE_PRIVATE);
			String fileName = prefs.getString(PREF_FILENAME, null);
			if (fileName == null) {
				info += "No file name\n";
			} else {
				info += fileName + "\n";
			}
			int dWidth = 0, dHeight = 0;
			boolean hasDrawable = true;
			Drawable drawable = mImageView.getDrawable();
			if (drawable == null) {
				hasDrawable = false;
				info += "No drawable\n";
			} else {
				dWidth = mImageView.getDrawable().getIntrinsicWidth();
				dHeight = drawable.getIntrinsicHeight();
				info += String.format("%d x %d\n", dWidth, dHeight);
			}
			MapCalibration calib = mImageView.getMapCalibration();
			if (calib == null || calib.getTransform() == null) {
				info += "Not calibrated\n";
			} else {
				info += "Calibrated\n";
				List<MapData> dataList = calib.getDataList();
				if (dataList != null) {
					for (MapData data : dataList) {
						info += String.format("  %04d   %04d  %11.6f %11.6f\n",
								data.getX(), data.getY(), +data.getLon(),
								data.getLat());
					}
				}
			}
			if (!mUseLocation) {
				info += "Not using location\n";
			} else {
				Location location = mImageView.getLocation();
				if (location == null) {
					info += "No location available\n";
				} else {
					double lon = location.getLongitude();
					double lat = location.getLatitude();
					int[] locationVals = calib.inverse(lon, lat);
					info += String.format("Location %.6f, %.6f",
							location.getLongitude(), location.getLatitude());
					if (locationVals != null) {
						info += String.format(" @ (%d, %d)\n", locationVals[0],
								locationVals[1]);
						if (hasDrawable) {
							if (locationVals[0] < 0
									|| locationVals[0] >= dWidth
									|| locationVals[1] < 0
									|| locationVals[1] >= dHeight) {
								info += "Not within the image\n";
							}
						}
					} else {
						info += "\n";
					}
				}
			}
			Utils.infoMsg(this, info);
		} catch (Throwable t) {
			Utils.excMsg(this, "Error showing info", t);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		// DEBUG
		Log.d(TAG, this.getClass().getSimpleName()
				+ ".onActivityResult: requestCode=" + requestCode
				+ " resultCode=" + resultCode);
		if (requestCode == DISPLAY_IMAGE && resultCode == RESULT_OK) {
			Bundle extras = intent.getExtras();
			String filePath = extras.getString(OPEN_FILE_PATH);
			// Just set the filePath, setNewImage will be done in onResume
			SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
					.edit();
			editor.putString(PREF_FILENAME, filePath);
			editor.commit();
		}
	}

	/**
	 * Brings up a list of files to open.
	 */
	private void selectFile() {
		Intent i = new Intent(this, ImageFileListActivity.class);
		Log.d(TAG, this.getClass().getSimpleName() + ".selectFile");
		startActivityForResult(i, DISPLAY_IMAGE);
	}

	/**
	 * Sets a new image from the given filename.
	 * 
	 * @param filePath
	 *            The path of the file to open.
	 */
	private void setNewImage(String filePath) {
		if (mImageView == null) {
			return;
		}
		if (filePath == null) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ "setNewImage: File is null");
			Utils.errMsg(this, "File is null");
			return;
		}
		File file = new File(filePath);
		if (!file.exists()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ "setNewImage: File does not exist " + file.getPath());
			Utils.errMsg(this, "File does not exist " + file.getPath());
			return;
		}
		// Remove the old bitmap to allow more memory
		mImageView.setMapCalibration(null);
		mImageView.setImageBitmap(null);
		Bitmap bitmap = getBitmap(this, file);
		if (bitmap != null) {
			// Save the value here
			SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
					.edit();
			editor.putString(PREF_FILENAME, file.getPath());
			editor.commit();
			mImageView.setImageBitmap(bitmap);
			mImageView.fitImage();
			// mImageView.setFitImageMode(MapImageView.IMAGEFITTED
			// | MapImageView.IMAGECENTERED);
			mImageView.forceLayout();
		}

		// See if there is a calibration file
		int i = filePath.lastIndexOf('.');
		String baseName = null;
		if (i > 0) {
			baseName = filePath.substring(0, i + 1);
			String calibFileName = baseName + CALIB_EXT;
			File calibFile = new File(calibFileName);
			Log.d(TAG,
					this.getClass().getSimpleName()
							+ ".setNewImage: calibFile=" + calibFileName
							+ (calibFile.exists() ? " exists" : " not found"));
			if (calibFile.exists()) {
				mMapCalibration = new MapCalibration(this);
				try {
					mMapCalibration.read(calibFile);
				} catch (Exception ex) {
					// Have to use Exception because NumberFormatException might
					// be wrapped in an InvocationTargetException
					Utils.excMsg(this, "Error reading calibration file", ex);
					mMapCalibration = null;
				}
			}
		}
		mImageView.setMapCalibration(mMapCalibration);
	}

	private void notifyLocationDisabled() {
		boolean enabled = mLocationManager.isProviderEnabled(mProvider);
		if (!enabled) {
			Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivity(intent);
		}
	}

	private void enableLocation() {
		if (!mUseLocation) {
			return;
		}
		// Get the location manager
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// Define the criteria how to select the location provider -> use
		// default
		// Criteria criteria = new Criteria();
		// provider = mLocationManager.getBestProvider(criteria, false);
		mProvider = LocationManager.GPS_PROVIDER;
		if (mProvider == null || mLocationManager == null
				|| !mLocationManager.isProviderEnabled(mProvider)) {
			return;
		}
		mLocationManager.requestLocationUpdates(mProvider, 400l, 1f, this);
	}

	private void disableLocation() {
		if (!mUseLocation) {
			return;
		}
		mLocationManager.removeUpdates(this);
		mLocationManager = null;
		mProvider = null;
	}

	// LocationListener

	@Override
	public void onLocationChanged(Location location) {
		// Log.d(TAG, this.getClass().getSimpleName() + ": onLocationChanged: "
		// + location.getLongitude() + ", " + location.getLatitude());

		// DEBUG
		// Toast.makeText(
		// this,
		// String.format("Location %.6f %.6f", location.getLongitude(),
		// location.getLatitude()), Toast.LENGTH_SHORT).show();

		if (mMapCalibration != null && mMapCalibration.getTransform() != null) {
			mImageView.setLocation(location, mMapCalibration);
			mImageView.invalidate();
		} else {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": onLocationChanged: transform is null");
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		if (status == LocationProvider.AVAILABLE) {
		} else {
			mImageView.setLocation(null, mMapCalibration);
			mImageView.invalidate();
		}
	}

	@Override
	public void onProviderEnabled(String provider) {
		// Do nothing
	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onProviderDisabled");
		mImageView.setLocation(null, mMapCalibration);
		mImageView.invalidate();
	}

}
