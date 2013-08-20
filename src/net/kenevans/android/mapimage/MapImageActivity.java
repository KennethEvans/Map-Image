package net.kenevans.android.mapimage;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

		// Get the location manager
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// Define the criteria how to select the location provider -> use
		// default
		// Criteria criteria = new Criteria();
		// provider = mLocationManager.getBestProvider(criteria, false);
		mProvider = LocationManager.GPS_PROVIDER;

		// Location location = mLocationManager.getLastKnownLocation(mProvider);
		// if (location != null) {
		// Toast.makeText(
		// this,
		// "Initial Location " + location.getLatitude() + ","
		// + location.getLatitude(), Toast.LENGTH_SHORT)
		// .show();
		// }
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
			notifyLocationDisabled();
			mUseLocation = true;
			enableLocation();
			return true;
		case R.id.stop_location:
			if (!mUseLocation) {
				Utils.warnMsg(this, "Location is already stopped");
				return true;
			}
			disableLocation();
			mUseLocation = false;
			return true;
		case R.id.test:
			setNewImage();
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
		Log.d(TAG, this.getClass().getSimpleName() + ": onResume:");

		// Restore the state
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		String fileName = prefs.getString(PREF_FILENAME, null);
		Log.d(TAG, "  fileName=" + fileName);
		if (fileName == null) {
			mImageView.setImageResource(R.drawable.test);
		} else {
			setNewImage(fileName);
		}
		// mImageView.setFitImageMode(MapImageView.IMAGEFITTED
		// | MapImageView.IMAGECENTERED);

		// Location
		enableLocation();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, this.getClass().getSimpleName() + ": onPause:");
		super.onPause();
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
		} catch (Exception ex) {
			Log.d(TAG, context.getClass().getSimpleName()
					+ ": Error reading image", ex);
			Utils.excMsg(context, "Error reading image", ex);
		}
		return bitmap;
	}

	/**
	 * Resets to using the default image.
	 */
	private void reset() {
		SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
		editor.putString(PREF_FILENAME, null);
		editor.commit();
		mImageView.setImageResource(R.drawable.test);
		// mImageView.setFitImageMode(MapImageView.IMAGEFITTED
		// | MapImageView.IMAGECENTERED);
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
			setNewImage(filePath);
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
				mMapCalibration = new MapCalibration();
				try {
					mMapCalibration.read(calibFile);
				} catch (Exception ex) {
					// Have to use Exception because NUmberFormatException might
					// be wrapped in an InvocationTargetException
					Utils.excMsg(this, "Cannot read calibration file", ex);
					mMapCalibration = null;
				}
			}
		}
	}

	/**
	 * Sets a new image.
	 */
	private void setNewImage() {
		if (mImageView == null) {
			return;
		}
		File sdCardRoot = Environment.getExternalStorageDirectory();
		File dir = new File(sdCardRoot, DEBUG_DIRNAME);
		File file = new File(dir, DEBUG_FILENAME);
		if (!file.exists()) {
			Log.d(TAG, this.getClass().getSimpleName()
					+ ": File does not exist " + file.getPath());
			Utils.errMsg(this, "File does not exist " + file.getPath());
			return;
		}
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
	}

	// LocationListener

	@Override
	public void onLocationChanged(Location location) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onLocationChanged: "
				+ location.getLongitude() + ", " + location.getLatitude());
		Toast.makeText(
				this,
				String.format("Location %.6f %.6f", location.getLongitude(),
						location.getLatitude()), Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.d(TAG, this.getClass().getSimpleName() + ": onProviderDisabled");
		Toast.makeText(this, "Disabled provider " + provider,
				Toast.LENGTH_SHORT).show();
	}

}
