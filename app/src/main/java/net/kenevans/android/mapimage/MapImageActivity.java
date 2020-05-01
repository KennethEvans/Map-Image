package net.kenevans.android.mapimage;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;

import net.kenevans.android.mapimage.MapCalibration.MapData;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MapImageActivity extends AppCompatActivity implements IConstants {
    private MapImageView mImageView;
    /**
     * Location, used for lat, lon, Accuracy only.
     */
    private Location mLocation;
    private List<PointF> mTrackPointList;
    private boolean mUseLocation = false;
    private boolean mTracking;
    private MapImageLocationService mLocationService;
    /**
     * Flag to show whether to prompt for READ_EXTERNAL_STORAGE permission if
     * it has not been granted.
     */
    private boolean mPromptForReadExternalStorage = true;
    private boolean mPromptForWriteExternalStorage = true;
    private MapCalibration mMapCalibration;
    private CharSequence[] mUpdateIntervals;
    private int mUpdateInterval = 0;

    /**
     * Manages the service lifecycle.
     */
    private final ServiceConnection mServiceConnection = new
            ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName,
                                               IBinder service) {
                    Log.d(TAG, "onServiceConnected: ");
                    mLocationService = ((MapImageLocationService.LocalBinder)
                            service).getService();
                    mLocationService.setTracking(mTracking);
//                    if (mDbAdapter != null) {
//                        mBLECardiacBleService.startDatabase(mDbAdapter);
//                    }
                    // Automatically connects to the device upon successful
                    // start-up
                    // initialization.
//                    SharedPreferences prefs = PreferenceManager
//                            .getDefaultSharedPreferences
//                                    (DeviceMonitorActivity.this);
//                    boolean manuallyDisconnected = prefs.getBoolean(
//                            PREF_MANUALLY_DISCONNECTED, false);
//                    if (!manuallyDisconnected) {
//                        boolean res = mBLECardiacBleService.connect
//                                (mDeviceAddress);
//                        Log.d(TAG, "Connect mBLECardiacBleService result=" +
//                                res);
//                        if (res) {
//                            setManuallyDisconnected(false);
//                        }
//                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.d(TAG, "onServiceDisconnected");
                    mLocationService = null;
                }
            };

    /**
     * Handles various events fired by the Service.
     */
    private final BroadcastReceiver mBroadcastReceiver = new
            BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (ACTION_LOCATION_CHANGED.equals(action)) {
                        if (mMapCalibration != null &&
                                mMapCalibration.getTransform() != null) {
                            if (mLocation == null) {
                                mLocation = new Location("");
                            }
                            double lat = intent.getDoubleExtra(EXTRA_LAT, 0.);
                            double lon = intent.getDoubleExtra(EXTRA_LON, 0.);
                            float accuracy =
                                    intent.getFloatExtra(EXTRA_ACCURACY, 0);
                            mLocation.setLatitude(lat);
                            mLocation.setLongitude(lon);
                            mLocation.setAccuracy(accuracy);
                            int[] locationVals =
                                    mMapCalibration.inverse(mLocation.getLongitude(),
                                            mLocation.getLatitude());
                            if (locationVals == null) {
                                Log.d(TAG, this.getClass().getSimpleName()
                                        + "  locationVals  is null");
                                return;
                            }
                            PointF locationPoint = new PointF(locationVals[0]
                                    , locationVals[1]);
                            if (mTracking) {
                                if (mTrackPointList == null) {
                                    mTrackPointList = new ArrayList<>();
                                }
                                mTrackPointList.add(new PointF(locationVals[0]
                                        , locationVals[1]));
                            } else {
                                mTrackPointList = null;
                            }
                            mImageView.setLocationAndTracks(locationPoint,
                                    mTrackPointList);
                        } else {
                            Log.d(TAG, this.getClass().getSimpleName()
                                    + ": onLocationChanged: transform is null");
                        }
//                    } else if (ACTION_PROVIDER_ENABLED.equals(action)) {
                    } else if (ACTION_PROVIDER_DISABLED.equals(action)) {
                        mImageView.setLocation(null);
                        mImageView.invalidate();
                    } else if (ACTION_STATUS_CHANGED.equals(action)) {
                        int status = intent.getIntExtra(EXTRA_STATUS, 0);
                        if (status == LocationProvider.OUT_OF_SERVICE) {
                            mImageView.setLocation(null);
                        }
//                    } else if (ACTION_ERROR.equals(action)) {
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);

        // Create update intervals here so getText is available
        mUpdateIntervals = new CharSequence[]{
                getText(R.string.update_fastest),
                getText(R.string.update_fast), getText(R.string.update_slow),
        };

        // // Remove title bar (Call before setContentView)
        // this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Remove notification bar (Call before setContentView)
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);
        mImageView = findViewById(R.id.imageview);
        mImageView.setMinimumDpi(MIN_DPI);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onResume: mUseLocation=" + mUseLocation
                + " mUpdateInterval=" + mUpdateInterval
                + "\nmPromptForReadExternalStorage="
                + mPromptForReadExternalStorage);

        // Restore the state
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mUseLocation = prefs.getBoolean(PREF_USE_LOCATION, false);
        mTracking = prefs.getBoolean(PREF_TRACKING, false);
        if (mLocationService != null) {
            mLocationService.setTracking(mTracking);
            if (mTracking) {
                if (mLocationService != null) {
                    mTrackPointList =
                            getPointsFromTrackpoints(
                                    mLocationService.mTrackpointList);
                }
                if (mTrackPointList == null) {
                    mTrackPointList = new ArrayList<>();
                }
            } else {
                mTrackPointList = null;
            }
            mImageView.setTracks(mTrackPointList);
        }
        mUpdateInterval = prefs.getInt(PREF_UPDATE_INTERVAL, 0);
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onResume (1): mUseLocation=" + mUseLocation
                + " mUpdateInterval=" + mUpdateInterval
                + "\nmPromptForReadExternalStorage="
                + mPromptForReadExternalStorage);
        String fileName = prefs.getString(PREF_FILENAME, null);
        Log.d(TAG, "  fileName=" + fileName);
        // Check READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.READ_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            if (mPromptForReadExternalStorage) {
                requestReadExternalStoragePermission();
            }
            Log.d(TAG, this.getClass().getSimpleName()
                    + ": onResume (2): mPromptForReadExternalStorage="
                    + mPromptForReadExternalStorage);
            mMapCalibration = null;
            setNoImage();
            return;
        }
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onResume (3): mUseLocation=" + mUseLocation
                + " mUpdateInterval=" + mUpdateInterval
                + "\nmPromptForReadExternalStorage="
                + mPromptForReadExternalStorage);
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
            // Start the service if it is not started
            if (mUseLocation && mLocationService == null) {
                bindService(new Intent(this, MapImageLocationService.class),
                        mServiceConnection, BIND_AUTO_CREATE);
            }
            registerReceiver(mBroadcastReceiver, makeBroadcastIntentFilter());
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause: " +
                "mUseLocation="
                + mUseLocation + " mUpdateInterval=" + mUpdateInterval);
        super.onPause();
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(this)
                .edit();
        editor.putBoolean(PREF_USE_LOCATION, mUseLocation);
        editor.putBoolean(PREF_TRACKING, mTracking);
        if (mImageView != null) {
            PointF center = mImageView.getCenter();
            float scale = mImageView.getScale();
            if (center != null) {
                editor.putFloat(PREF_CENTER_X, center.x);
                editor.putFloat(PREF_CENTER_Y, center.y);
                editor.putFloat(PREF_SCALE, scale);
            }
        }
        editor.apply();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy: " +
                "mUseLocation="
                + mUseLocation + " mUpdateInterval=" + mUpdateInterval);
        super.onDestroy();
        // Unbind the service if it is bound
        unbindService(mServiceConnection);
        mLocationService = null;
//        if (mDbAdapter != null) {
//            mDbAdapter.close();
//            mDbAdapter = null;
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mUseLocation) {
            menu.findItem(R.id.start_location).
                    setTitle(R.string.stop_location_item);
        } else {
            menu.findItem(R.id.start_location).
                    setTitle(R.string.start_location_item);
        }
        if (mTracking) {
            menu.findItem(R.id.start_tracking).
                    setTitle(R.string.stop_tracking_item);
        } else {
            menu.findItem(R.id.start_tracking).
                    setTitle(R.string.start_tracking_item);
        }
        return super.onPrepareOptionsMenu(menu);
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
                    disableLocation();
                } else {
                    if (Build.VERSION.SDK_INT >= 23
                            && ContextCompat.checkSelfPermission(this, Manifest
                            .permission.READ_EXTERNAL_STORAGE) != PackageManager
                            .PERMISSION_GRANTED) {
                        Utils.warnMsg(this, "Location cannot be started if " +
                                "there" +
                                " is no permission for READ_EXTERNAL_STORAGE");
                        return true;
                    }
                    if (mUseLocation) {
                        Utils.warnMsg(this, "Location is already started");
                        return true;
                    }
                    mUseLocation = true;
                    setupLocation();
                }
                return true;
            case R.id.start_tracking:
                mTracking = !mTracking;
                if (mLocationService != null) {
                    mLocationService.setTracking(mTracking);
                }
                // Get the stored trackpoints from the service if any
                if (mTracking) {
                    if (mLocationService != null) {
                        mTrackPointList =
                                getPointsFromTrackpoints(
                                        mLocationService.mTrackpointList);
                    }
                    if (mTrackPointList == null) {
                        mTrackPointList = new ArrayList<>();
                    }
                } else {
                    mTrackPointList = null;
                }
                mImageView.setTracks(mTrackPointList);
                return true;
            case R.id.save_gpx:
                saveGpx();
                return true;
            case R.id.startclear_track:
                if (mLocationService != null &&
                        mLocationService.mTrackpointList != null) {
                    mLocationService.mTrackpointList.clear();
                }
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

    /**
     * Make an IntentFilter for the actions in which we are interested.
     *
     * @return The IntentFilter.
     */
    private static IntentFilter makeBroadcastIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_ERROR);
        intentFilter.addAction(ACTION_LOCATION_CHANGED);
        intentFilter.addAction(ACTION_PROVIDER_DISABLED);
        intentFilter.addAction(ACTION_PROVIDER_ENABLED);
        intentFilter.addAction(ACTION_STATUS_CHANGED);
        return intentFilter;
    }

    /**
     * Sets a 1x1 Bitmap as the current image.
     */
    private void setNoImage() {
        if (mImageView == null) {
            return;
        }
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
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(this);
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
            if (Build.VERSION.SDK_INT >= 23
                    && ContextCompat.checkSelfPermission(this, Manifest
                    .permission.READ_EXTERNAL_STORAGE) != PackageManager
                    .PERMISSION_GRANTED) {
                info += "No permission granted for READ_EXTERNAL_STORAGE\n";
            }
            if (Build.VERSION.SDK_INT >= 23
                    && ContextCompat.checkSelfPermission(this, Manifest
                    .permission.ACCESS_COARSE_LOCATION) != PackageManager
                    .PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest
                    .permission.ACCESS_FINE_LOCATION) != PackageManager
                    .PERMISSION_GRANTED) {
                info += "No permission granted for ACCESS_FINE_LOCATION\n";
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
        if (requestCode == DISPLAY_IMAGE_REQ && resultCode == RESULT_OK) {
            Bundle extras = intent.getExtras();
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .edit();
            try {
                String filePath = extras.getString(EXTRA_OPEN_FILE_PATH);
                // Just set the filePath, setNewImage will be done in onResume
                editor.putString(PREF_FILENAME, filePath);
            } catch (Exception ex) {
                Utils.excMsg(this, "Did not get file name from Preferences",
                        ex);
            }
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
     * Brings up a list of files to open.
     */
    private void selectFile() {
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.READ_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            if (mPromptForReadExternalStorage) {
                requestReadExternalStoragePermission();
            } else {
                Utils.errMsg(this, "No permission granted for " +
                        "READ_EXTERNAL_STORAGE." +
                        "\nTo continue restart app and allow or set " +
                        "permission manually in the Application Manager."
                );
            }
            return;
        }
        Intent i = new Intent(this, ImageFileListActivity.class);
        Log.d(TAG, this.
                getClass().
                getSimpleName() + ".selectFile");
        startActivityForResult(i, DISPLAY_IMAGE_REQ);
    }

    /**
     * Opens the file that contains the current location
     */
    private void openImageForLocation() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "openImageForLocation:" + " mUseLocation=" +
                mUseLocation);
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.READ_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            if (mPromptForReadExternalStorage) {
                requestReadExternalStoragePermission();
            } else {
                Utils.errMsg(this, "No permission granted for " +
                        "READ_EXTERNAL_STORAGE." +
                        "\nTo continue restart app and allow or set " +
                        "permission manually in the Application Manager."
                );
            }
            return;
        }
        if (!mUseLocation) {
            Utils.errMsg(this, "Not using location. Try Start Location.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_COARSE_LOCATION) != PackageManager
                .PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_FINE_LOCATION) != PackageManager
                .PERMISSION_GRANTED) {
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
        File[] filesArray = null;
        try {
            if (dir != null) {
                File[] files = dir.listFiles();
                List<File> fileList = new ArrayList<>();
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
            Utils.infoMsg(this, "No images contain the current location");
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
                        String filePath = files[item].getPath();
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(MapImageActivity
                                        .this)
                                .edit();
                        editor.putString(PREF_FILENAME, filePath);
                        // Reset the preferences to the defaults
                        editor.putFloat(PREF_CENTER_X, X_DEFAULT);
                        editor.putFloat(PREF_CENTER_Y, Y_DEFAULT);
                        editor.putFloat(PREF_SCALE, SCALE_DEFAULT);
                        editor.apply();
                        setNewImage(filePath);
                    }
                });
        builder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int
                            whichButton) {
                        // Do nothing
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Checks if the current location is within the image in the file.
     *
     * @param file The image file.
     * @param lat  The latitiude.
     * @param lon  The longitude.
     * @return If location is within the image.
     */
    private boolean fileContainsLocation(File file, double lat, double lon) {
        // Get the file width and height
        int dWidth, dHeight;
        try {
            // Get the bitmap domensions without loading the Bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), bmOptions);
            dWidth = bmOptions.outWidth;
            dHeight = bmOptions.outHeight;
        } catch (Exception ex) {
            Utils.excMsg(this, "Error getting Bitmap dimensions", ex);
            return false;
        }
        if (dWidth <= 0 || dHeight <= 0) {
            return false;
        }

        // See if there is a calibration file
        MapCalibration mapCalibration = null;
        String filePath = file.getPath();
        int i = filePath.lastIndexOf('.');
        String baseName;
        if (i > 0) {
            baseName = filePath.substring(0, i + 1);
            String calibFileName = baseName + CALIB_EXT;
            File calibFile = new File(calibFileName);
            if (calibFile.exists()) {
                mapCalibration = new MapCalibration(this);
                try {
                    mapCalibration.read(calibFile);
                } catch (Exception ex) {
                    mapCalibration = null;
                }
            }
        }
        if (mapCalibration == null) {
            return false;
        }

        // Check if the location is within the image
        try {
            int[] locationVals = mapCalibration.inverse(lon, lat);
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
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.READ_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            if (mPromptForReadExternalStorage) {
                requestReadExternalStoragePermission();
            } else {
                Utils.errMsg(this, "No permission granted for " +
                        "READ_EXTERNAL_STORAGE." +
                        "\nTo continue restart app and allow or set " +
                        "permission manually in the Application Manager."
                );
            }
            return;
        }
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
            // Reset mTrackPointList with new calib
            if (mTracking) {
                if (mLocationService != null) {
                    mTrackPointList =
                            getPointsFromTrackpoints(
                                    mLocationService.mTrackpointList);
                }
                if (mTrackPointList == null) {
                    mTrackPointList = new ArrayList<>();
                }
            } else {
                mTrackPointList = null;
            }
            mImageView.setTracks(mTrackPointList);
        }
    }

    /**
     * Checks if the service is running
     *
     * @param serviceClass The class of the service.
     * @return If service is running or not.
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initializes location parameters.  Does nothing if not using location.
     */
    private void setupLocation() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "setupLocation:" + " mUseLocation=" +
                mUseLocation);
        // Do not set up location if there is no permission for
        // READ_EXTERNAL_STORAGE since there will be no map anyway
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.READ_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            return;
        }
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
            // Don't set mUseLocation = false here
            // It will be set on onRequestPermissionResult if not allowed
            requestLocationPermission();
            return;
        } else if (Build.VERSION.SDK_INT < 23) {
            // Bring up dialog to enable location
            // Only used on Android 22 and lower
            if (mLocationService == null) {
                Intent intent = new Intent(Settings
                        .ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        }

        // Start the service
        if (mLocationService == null) {
            bindService(new Intent(this, MapImageLocationService.class),
                    mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    private void disableLocation() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "disableLocation:" + " mUseLocation=" + mUseLocation);
        // Unbind the service
        if (mLocationService != null) {
            unbindService(mServiceConnection);
        }
        mLocationService = null;
        mLocation = null;
        mUseLocation = false;
        if (mImageView != null) mImageView.setLocation(null);
    }

    private void saveGpx() {
        if (mLocationService == null) {
            Utils.errMsg(this, "No tracks: Location service is not running");
            return;
        }
        List<Trackpoint> trackpointList = mLocationService.mTrackpointList;
        if (trackpointList == null || trackpointList.isEmpty()) {
            Utils.errMsg(this, "There are no tracks");
            return;
        }
        // Check WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 23
                && ContextCompat.checkSelfPermission(this, Manifest
                .permission.WRITE_EXTERNAL_STORAGE) != PackageManager
                .PERMISSION_GRANTED) {
            if (mPromptForWriteExternalStorage) {
                requestWriteExternalStoragePermission();
            } else {
                Utils.errMsg(this,
                        "Permission for WRITE_EXTERNAL_STORAGE is not granted");
            }
            return;
        }

        String msg;
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            msg = "External Storage is not available";
            Log.e(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }
        // Get an identifier
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.gpx_save_title);
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        LinearLayout ll = new LinearLayout(this);
        // Convert to dip
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10,
                getResources().getDisplayMetrics()
        );
        ll.setPadding(padding, padding, padding, padding);
        ll.setOrientation(LinearLayout.VERTICAL);

        final EditText prefixBox = new EditText(this);
        prefixBox.setHint("Prefix");
        prefixBox.setInputType(InputType.TYPE_CLASS_TEXT);
        prefixBox.setText(prefs.getString(PREF_GPX_FILENAME_PREFIX, ""));
        ll.addView(prefixBox);

        final EditText categoryBox = new EditText(this);
        categoryBox.setHint("Category");
        categoryBox.setInputType(InputType.TYPE_CLASS_TEXT);
        categoryBox.setText(prefs.getString(PREF_GPX_CATEGORY, ""));
        ll.addView(categoryBox);

        final EditText locationBox = new EditText(this);
        locationBox.setHint("Location");
        locationBox.setInputType(InputType.TYPE_CLASS_TEXT);
        locationBox.setText(prefs.getString(PREF_GPX_LOCATION, ""));
        ll.addView(locationBox);

        final EditText suffixBox = new EditText(this);
        suffixBox.setHint("Suffix");
        suffixBox.setInputType(InputType.TYPE_CLASS_TEXT);
        suffixBox.setText(prefs.getString(PREF_GPX_FILENAME_SUFFIX, ""));
        ll.addView(suffixBox);

        dialog.setView(ll);
        dialog.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String prefix = prefixBox.getText().toString();
                        String category = categoryBox.getText().toString();
                        String location = locationBox.getText().toString();
                        String suffix = suffixBox.getText().toString();
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(MapImageActivity
                                        .this)
                                .edit();
                        editor.putString(PREF_GPX_FILENAME_PREFIX, prefix);
                        editor.putString(PREF_GPX_CATEGORY, category);
                        editor.putString(PREF_GPX_LOCATION, location);
                        editor.putString(PREF_GPX_FILENAME_SUFFIX, suffix);
                        editor.apply();
                        finishSaveGpx(prefix, category, location, suffix);
                    }
                });
        dialog.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
        dialog.show();
    }

    /**
     * Finishes the save after getting the paramaters.
     *
     * @param prefix   Prefix for the file name
     * @param category Category.
     * @param location Location.
     * @param suffix   Suffix for the file name
     */
    private void finishSaveGpx(String prefix, String category, String location,
                               String suffix) {
        Log.d(TAG, this.getClass().getSimpleName());
        String msg;
        File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);

        List<Trackpoint> trackpointList = mLocationService.mTrackpointList;

        String name;
        SimpleDateFormat trackpointFormatter = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        trackpointFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            PackageManager pm = getPackageManager();
            PackageInfo po = pm.getPackageInfo(this.getPackageName(), 0);
            name = "MapImage " + po.versionName;
        } catch (Exception ex) {
            name = "MapImage";
        }

        String format = "yyyy-MM-dd_HH-mm-ss";
        SimpleDateFormat filenameFormatter = new SimpleDateFormat(format,
                Locale.US);
        // Find time of first trackpoint
        Date firstTkptDate = new Date();
        for (Trackpoint tkpt : trackpointList) {
            if (tkpt == null) continue;
            firstTkptDate = new Date(tkpt.time);
            break;
        }
        if (prefix == null || prefix.isEmpty()) {
            prefix = "MapImage";
        }
        String fileName = prefix.replaceAll("\\s+", "_")
                + "_" + filenameFormatter.format(firstTkptDate);
        if (category != null && !category.isEmpty()) {
            fileName += "_" + category.replaceAll("\\s+", "_");
        }
        if (location != null && !location.isEmpty()) {
            fileName += "_" + location.replaceAll("\\s+", "_");
        }
        if (suffix != null && !suffix.isEmpty()) {
            fileName += suffix;
        }
        fileName += ".gpx";
        File file = new File(dir, fileName);
        PrintWriter out = null;
        String line, lat, lon, ele;
        long time;
        boolean prevTrackpointNull = true;
        Log.d(TAG, "");
        int nItem = 0;
        int size = trackpointList.size();
        try {
            // Write header
            out = new PrintWriter(new FileWriter(file));
            // Write the beginning lines
            out.write(String.format(GPXUtils.GPX_FILE_START_LINES, name,
                    trackpointFormatter.format(firstTkptDate),
                    category == null ? "" : category,
                    location == null ? "" : location));
            for (Trackpoint tkpt : trackpointList) {
                nItem++;
                // Make a new segment if the trackpoint is null
                if (tkpt == null) {
                    // Avoid empty segments
                    if (prevTrackpointNull || nItem == size) continue;
                    prevTrackpointNull = true;
                    out.write(GPXUtils.GPX_FILE_NEW_SEGMENT);
                    continue;
                } else {
                    prevTrackpointNull = false;
                }
                lat = String.format(Locale.US, "%.6f", tkpt.lat);
                lon = String.format(Locale.US, "%.6f", tkpt.lon);
                ele = String.format(Locale.US, "%.6f", tkpt.alt);
                time = tkpt.time;
                line = String.format(GPXUtils.GPX_FILE_TRACK_LINES,
                        lat, lon, ele,
                        trackpointFormatter.format(new Date(time)));
                out.write(line);
            }
            out.write(GPXUtils.GPX_FILE_END_LINES);
            out.flush();
            msg = "Wrote " + file.getPath();
            Log.d(TAG, msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            msg = "Error writing " + file.getPath();
            Log.e(TAG, msg);
            Log.e(TAG, Log.getStackTraceString(ex));
            Utils.excMsg(this, msg, ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ex) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Bring up a dialog to change the update interval.
     */
    private void setUpdateInterval() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "setUpdateInterval:");
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
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(MapImageActivity
                                        .this).edit();
                        editor.putInt(PREF_UPDATE_INTERVAL,
                                mUpdateInterval);
                        editor.apply();
                        if (mLocationService != null) {
                            try {
                                mLocationService.setUpdateInterval(mUpdateInterval);
                            } catch (SecurityException ex) {
                                Utils.excMsg(MapImageActivity.this,
                                        "SecurityException during " +
                                                "setupLocation", ex);
                            } catch (IllegalArgumentException ex) {
                                Utils.excMsg(MapImageActivity.this,
                                        "IllegalArgument exception during" +
                                                " " +
                                                "setupLocation", ex);
                            }
                        }
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Converts a List<Trackpoint> to a List<PointF> containing the pixel
     * coordinates of the lat, lon values.
     *
     * @param trackPointList The trackpoint list.
     * @return The list of pixel coordinates.
     */
    private List<PointF> getPointsFromTrackpoints(List<Trackpoint> trackPointList) {
        if (trackPointList == null) {
            return null;
        }
        if (mMapCalibration == null) {
            return null;
        }
        List<PointF> points = new ArrayList<>();
        for (Trackpoint tkpt : trackPointList) {
            if (tkpt == null) continue;
            int[] locationVals =
                    mMapCalibration.inverse(tkpt.lon, tkpt.lat);
            points.add(new PointF(locationVals[0], locationVals[1]));
        }
        return points;
    }

    /**
     * Request permission for FINE_LOCATION.
     */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest
                        .permission.ACCESS_FINE_LOCATION},
                ACCESS_FINE_LOCATION_REQ);
    }

    /**
     * Request permission for READ_EXTERNAL_STORAGE.
     */
    private void requestReadExternalStoragePermission() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "requestReadExternalStoragePermission:");
        if (Build.VERSION.SDK_INT < 16) return;
        ActivityCompat.requestPermissions(this, new String[]{Manifest
                        .permission.READ_EXTERNAL_STORAGE},
                ACCESS_READ_EXTERNAL_STORAGE_REQ);
    }

    /**
     * Request permission for WRITE_EXTERNAL_STORAGE.
     */
    private void requestWriteExternalStoragePermission() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "requestWriteExternalStoragePermission:");
        if (Build.VERSION.SDK_INT < 16) return;
        ActivityCompat.requestPermissions(this, new String[]{Manifest
                        .permission.WRITE_EXTERNAL_STORAGE},
                ACCESS_WRITE_EXTERNAL_STORAGE_REQ);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[]
                                                   permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "onRequestPermissionsResult:" + " permissions=" +
                permissions[0]
                + "\ngrantResults=" + grantResults[0]
                + "\nmUseLocation=" + mUseLocation
                + "\nmPromptForReadExternalStorage="
                + mPromptForReadExternalStorage);
        switch (requestCode) {
            case ACCESS_FINE_LOCATION_REQ:
                // FINE_LOCATION
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "FINE_LOCATION granted");
                    if (mUseLocation) {
                        setupLocation();
                    }
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "FINE_LOCATION denied");
                    mUseLocation = false;
                    // Save this as onResume will be called next, not
                    // onPause
                    SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(this)
                            .edit();
                    editor.putBoolean(PREF_USE_LOCATION, mUseLocation);
                    editor.apply();
                }
                break;
            case ACCESS_READ_EXTERNAL_STORAGE_REQ:
                // READ_EXTERNAL_STORAGE
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "READ_EXTERNAL_STORAGE granted");
                    mPromptForReadExternalStorage = true;
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "READ_EXTERNAL_STORAGE denied");
                    mPromptForReadExternalStorage = false;
                    mUseLocation = false;
                    // Save this as onResume will be called next, not
                    // onPause
                    SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(this)
                            .edit();
                    editor.putBoolean(PREF_USE_LOCATION, mUseLocation);
                    editor.apply();
                }
                break;
            case ACCESS_WRITE_EXTERNAL_STORAGE_REQ:
                // READ_EXTERNAL_STORAGE
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "WRITE_EXTERNAL_STORAGE granted");
                    mPromptForWriteExternalStorage = true;
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "WRITE_EXTERNAL_STORAGE denied");
                    mPromptForWriteExternalStorage = false;
                }
                break;
        }
    }

}
