package net.kenevans.android.mapimage;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.davemorrissey.labs.subscaleview.ImageSource;

import net.kenevans.android.mapimage.MapCalibration.MapData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MapImageActivity extends Activity implements IConstants,
        LocationListener {
    private MapImageView mImageView;
    private Location mLocation;
    private LocationManager mLocationManager;
    private String mProvider;
    private boolean mUseLocation = false;
    private MapCalibration mMapCalibration;
    private CharSequence[] mUpdateIntervals;
    private int mUpdateInterval = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);

        // Create update intervals here so getText is available
        mUpdateIntervals = new CharSequence[]{
                getText(R.string.update_fastest),
                getText(R.string.update_fast), getText(R.string.update_slow),};

        // // Remove title bar (Call before setContentView)
        // this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Remove notification bar (Call before setContentView)
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);
        mImageView = (MapImageView) findViewById(R.id.imageview);
        mImageView.setMinimumDpi(MIN_DPI);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.open:
                selectFile();
                return true;
            case R.id.open_image_location:
                openImageForLocation();
                return true;
            case R.id.start_location:
                if (mUseLocation) {
                    Utils.warnMsg(this, "Location is already started");
                    return true;
                }
                mUseLocation = true;
                setupLocation();
                notifyLocationDisabled();
                return true;
            case R.id.stop_location:
                if (!mUseLocation) {
                    Utils.warnMsg(this, "Location is already stopped");
                    return true;
                }
                disableLocation();
                mUseLocation = false;
                return true;
            case R.id.set_update_interval:
                setUpdateInterval();
                return true;
            case R.id.image_info:
                info();
                return true;
            case R.id.reset:
                reset();
                return true;
            case R.id.help:
                showHelp();
                return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onResume: mUseLocation=" + mUseLocation
                + " mUpdateInterval=" + mUpdateInterval);

        // Restore the state
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mUseLocation = prefs.getBoolean(PREF_USE_LOCATION, false);
        mUpdateInterval = prefs.getInt(PREF_UPDATE_INTERVAL, 0);
        String fileName = prefs.getString(PREF_FILENAME, null);
        Log.d(TAG, "  fileName=" + fileName);
        if (fileName == null) {
            mMapCalibration = null;
            setNoImage();
        } else {
            if (mImageView != null) {
                setNewImage(fileName);
                float x = prefs.getFloat(PREF_CENTER_X, X_DEFAULT);
                float y = prefs.getFloat(PREF_CENTER_Y, Y_DEFAULT);
                float scale = prefs.getFloat(PREF_SCALE, SCALE_DEFAULT);
                if (scale != SCALE_DEFAULT || x != X_DEFAULT || y !=
                        Y_DEFAULT) {
                    mImageView.setScaleAndCenter(scale, new PointF(x, y));
                }
            }
            setupLocation();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause: " +
                "mUseLocation="
                + mUseLocation + " mUpdateInterval=" + mUpdateInterval);
        super.onPause();
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                .edit();
        editor.putBoolean(PREF_USE_LOCATION, mUseLocation);
        if (mImageView != null) {
            PointF center = mImageView.getCenter();
            float scale = mImageView.getScale();
            if (center != null) {
                editor.putFloat(PREF_CENTER_X, center.x);
                editor.putFloat(PREF_CENTER_Y, center.y);
                editor.putFloat(PREF_SCALE, scale);
                editor.apply();
            }
        }
        disableLocation();
    }

    /**
     * Sets a 1x1 Bitmap as the current image.
     */

    private void setNoImage() {
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap bmp = Bitmap.createBitmap(1, 1, conf);
        mImageView.setImage(ImageSource.bitmap(bmp));
    }

    /**
     * Resets to using the default image.
     */
    private void reset() {
        if (mImageView != null) {
            mImageView.resetScaleAndCenter();
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
            int dWidth = mImageView.getSWidth();
            int dHeight = mImageView.getSHeight();
            info += String.format(Locale.US, "%d x %d\n", dWidth, dHeight);

            // Get screen size and density
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            if (metrics != null) {
                info += "Screen Size " + metrics.widthPixels + "x" + metrics
                        .heightPixels
                        + " densityDpi " + metrics.densityDpi + " " + "\n";
            }
            // Calibration
            if (mMapCalibration == null || mMapCalibration.getTransform() ==
                    null) {
                info += "Not calibrated\n";
            } else {
                info += "Calibrated\n";
                List<MapData> dataList = mMapCalibration.getDataList();
                if (dataList != null) {
                    for (MapData data : dataList) {
                        info += String.format(Locale.US,
                                "  %04d   %04d  %11.6f %11.6f\n",
                                data.getX(), data.getY(), +data.getLon(),
                                data.getLat());
                    }
                }
            }
            // Location
            if (!mUseLocation) {
                info += "Not using location\n";
            } else {
                if (mLocation == null) {
                    info += "No location available\n";
                } else {
                    double lon = mLocation.getLongitude();
                    double lat = mLocation.getLatitude();
                    float accuracy = mLocation.getAccuracy();
                    info += String.format(Locale.US,
                            "Location %.6f, %.6f +/- %.2f m",
                            mLocation.getLongitude(), mLocation.getLatitude(),
                            accuracy);
                    try {
                        int[] locationVals = mMapCalibration.inverse(lon, lat);
                        if (locationVals != null) {
                            info += String.format(Locale.US, " @ (%d, %d)\n",
                                    locationVals[0],
                                    locationVals[1]);
                            if (locationVals[0] < 0
                                    || locationVals[0] >= dWidth
                                    || locationVals[1] < 0
                                    || locationVals[1] >= dHeight) {
                                info += "Not within the image\n";
                            }
                        } else {
                            info += "\n    Error getting location image " +
                                    "coordinates\n";
                        }
                    } catch (Exception ex) {
                        // Do nothing
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
            // Reset the preferences to the defaults
            editor.putFloat(PREF_CENTER_X, X_DEFAULT);
            editor.putFloat(PREF_CENTER_Y, Y_DEFAULT);
            editor.putFloat(PREF_SCALE, SCALE_DEFAULT);
            editor.apply();
        }
    }

    /**
     * Show the help.
     */
    private void showHelp() {
        try {
            // Start theInfoActivity
            Intent intent = new Intent();
            intent.setClass(this, InfoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(INFO_URL, "file:///android_asset/mapimage.html");
            startActivity(intent);
        } catch (Exception ex) {
            Utils.excMsg(this, getString(R.string.help_show_error), ex);
        }
    }

    /**
     * Bring up a dialog to change the sort order.
     */

    private void setUpdateInterval() {
        int len = mUpdateIntervals.length;
        final CharSequence[] items = new CharSequence[len];
        System.arraycopy(mUpdateIntervals, 0, items, 0, len);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.update_title));
        builder.setSingleChoiceItems(items, mUpdateInterval,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();
                        if (item < 0 || item >= mUpdateIntervals.length) {
                            Utils.errMsg(MapImageActivity.this,
                                    "Invalid update interval");
                            mUpdateInterval = 0;
                        } else {
                            mUpdateInterval = item;
                        }
                        SharedPreferences.Editor editor = getPreferences(
                                MODE_PRIVATE).edit();
                        editor.putInt(PREF_UPDATE_INTERVAL, mUpdateInterval);
                        editor.apply();
                        // Reset the location
                        disableLocation();
                        setupLocation();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
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
     * Opens the file that contains the current location
     */
    private void openImageForLocation() {
        if (!mUseLocation) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_COARSE_LOCATION) != PackageManager
                .PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_FINE_LOCATION) != PackageManager
                .PERMISSION_GRANTED) {
            mUseLocation = false;
            return;
        }

        // Get the location
        if (mLocation == null) {
            Utils.errMsg(this, "No location available");
            return;
        }
        double lon = mLocation.getLongitude();
        double lat = mLocation.getLatitude();

        // Get the list of files
        File dir = ImageFileListActivity.getImageDirectory(this);
        List<File> fileList = null;
        File[] filesArray = null;
        try {
            if (dir != null) {
                File[] files = dir.listFiles();
                fileList = new ArrayList<>();
                for (File file : files) {
                    if (!file.isDirectory()) {
                        String ext = Utils.getExtension(file);
                        if (ext.equals("jpg") || ext.equals("jpeg")
                                || ext.equals("png") || ext.equals("gif")) {
                            if (fileContainsLocation(file, lat, lon)) {
                                fileList.add(file);
                            }
                        }
                    }
                }
                Collections.sort(fileList);
                filesArray = new File[fileList.size()];
                fileList.toArray(filesArray);
            }
        } catch (Exception ex) {
            Utils.excMsg(this, "Failed to get list of available files", ex);
            return;
        }
        if (filesArray == null || filesArray.length == 0) {
            return;
        }

        // Prompt for the file to use
        final File[] files = filesArray;
        final CharSequence[] items = new CharSequence[files.length];
        for (int i = 0; i < files.length; i++) {
            items[i] = files[i].getName();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.open_image_location_title));
        builder.setSingleChoiceItems(items, 0,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, final int
                            item) {
                        dialog.dismiss();
                        if (item < 0 || item >= files.length) {
                            Utils.errMsg(MapImageActivity.this,
                                    "Invalid item");
                            return;
                        }
                        setNewImage(files[item].getPath());
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private boolean fileContainsLocation(File file, double lat, double lon) {
        // Get the file width and height
        int dWidth = 0, dHeight = 0;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                return false;
            }
            dWidth = bitmap.getWidth();
            dHeight = bitmap.getHeight();
        } catch (Exception ex) {
            return false;
        }
        if (dWidth <= 0 || dHeight <= 0) {
            return false;
        }

        // Check if the location is within the image
        try {
            int[] locationVals = mMapCalibration.inverse(lon, lat);
            return locationVals != null && !(locationVals[0] < 0 ||
                    locationVals[0] >= dWidth || locationVals[1] < 0 ||
                    locationVals[1] >= dHeight);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Sets a new image from the given filename.
     *
     * @param filePath The path of the file to open.
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
        mMapCalibration = null;
        setNoImage();
        mImageView.setImage(ImageSource.uri(file.getPath()));
        // See if there is a calibration file
        int i = filePath.lastIndexOf('.');
        String baseName;
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
    }

    private void notifyLocationDisabled() {
        boolean enabled = mLocationManager.isProviderEnabled(mProvider);
        if (!enabled) {
            Intent intent = new Intent(Settings
                    .ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
    }

    /**
     * Initializes location parameters.  Does nothing if not using location.
     */
    private void setupLocation() {
        if (!mUseLocation) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_COARSE_LOCATION) != PackageManager
                .PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_FINE_LOCATION) != PackageManager
                .PERMISSION_GRANTED) {
            mUseLocation = false;
            return;
        }

        // Get the location manager
        mLocationManager = (LocationManager) getSystemService(Context
                .LOCATION_SERVICE);
        // Define the criteria how to select the location provider -> use
        // default
        // Criteria criteria = new Criteria();
        // provider = mLocationManager.getBestProvider(criteria, false);
        mProvider = LocationManager.GPS_PROVIDER;
        if (mLocationManager == null
                || !mLocationManager.isProviderEnabled(mProvider)) {
            return;
        }
        // Get the last known location and set it
        Location lastLocation = mLocationManager
                .getLastKnownLocation(mProvider);
        onLocationChanged(lastLocation);
        // Check index is in range
        if (mUpdateInterval >= LOCATION_UPDATE_TIMES.length
                || mUpdateInterval < 0) {
            mUpdateInterval = 0;
        }
        // Request updates
        mLocationManager.requestLocationUpdates(mProvider,
                LOCATION_UPDATE_TIMES[mUpdateInterval],
                LOCATION_UPDATE_DISTANCES[mUpdateInterval], this);
    }

    private void disableLocation() {
        if (!mUseLocation) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_COARSE_LOCATION) != PackageManager
                .PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_FINE_LOCATION) != PackageManager
                .PERMISSION_GRANTED) {
            mUseLocation = false;
            return;
        }
        mLocationManager.removeUpdates(this);
        mLocationManager = null;
        mProvider = null;
        mLocation = null;
        mImageView.setLocation(null);
    }

    @Override
    public void onLocationChanged(Location location) {
//        Log.d(TAG, this.getClass().getSimpleName() + ": onLocationChanged: "
//                + location.getLongitude() + ", " + location.getLatitude());

//        // DEBUG
//        Toast.makeText(
//                this,
//                String.format("Location %.6f %.6f", location.getLongitude(),
//                        location.getLatitude()), Toast.LENGTH_SHORT).show();
        mLocation = location;
        if (mLocation == null) {
            return;
        }
        if (mMapCalibration != null && mMapCalibration.getTransform() != null) {
            float lon = (float) location.getLongitude();
            float lat = (float) location.getLatitude();
            int[] locationVals = mMapCalibration.inverse(lon, lat);
            if (locationVals == null) {
                Log.d(TAG, this.getClass().getSimpleName()
                        + "  locationVals  is null");
                return;
            }
            PointF locationPoint = new PointF(locationVals[0], locationVals[1]);
            mImageView.setLocation(locationPoint);
        } else {
            Log.d(TAG, this.getClass().getSimpleName()
                    + ": onLocationChanged: transform is null");
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Log.d(TAG, this.getClass().getSimpleName() + ": onStatusChanged: "
        // + "enabled=" + mLocationManager.isProviderEnabled(provider)
        // + " status=" + status + " (AVAILABLE="
        // + LocationProvider.AVAILABLE + ")");

        if (status == LocationProvider.OUT_OF_SERVICE) {
            mImageView.setLocation(null);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_COARSE_LOCATION) != PackageManager
                .PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_FINE_LOCATION) != PackageManager
                .PERMISSION_GRANTED) {
            mUseLocation = false;
            return;
        }

        // Get the last known location and set it
        if (mProvider != null && mLocationManager != null
                && mLocationManager.isProviderEnabled(mProvider)) {
            Location lastLocation = mLocationManager
                    .getLastKnownLocation(mProvider);
            onLocationChanged(lastLocation);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Log.d(TAG, this.getClass().getSimpleName() + ": onProviderDisabled");
        mImageView.setLocation(null);
        mImageView.invalidate();
    }

}
