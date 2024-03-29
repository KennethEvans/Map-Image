package net.kenevans.android.mapimage;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
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

import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
    private boolean mLocationDenied;
    private boolean mFineLocationAsked;

    private MapCalibration mMapCalibration;
    private CharSequence[] mUpdateIntervals;
    private int mUpdateInterval = 0;

    // Launcher for PREF_TREE_URI
    private final ActivityResultLauncher<Intent> openDocumentTreeLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "openDocumentTreeLauncher: result" +
                                ".getResultCode()=" + result.getResultCode());
                        // Find the UID for this application
                        Log.d(TAG, "URI=" + UriUtils.getApplicationUid(this));
                        Log.d(TAG,
                                "Current permissions (initial): "
                                        + UriUtils.getNPersistedPermissions(this));
                        try {
                            if (result.getResultCode() == RESULT_OK) {
                                // Get Uri from Storage Access Framework.
                                Uri treeUri = result.getData().getData();
                                SharedPreferences.Editor editor =
                                        getPreferences(MODE_PRIVATE)
                                                .edit();
                                if (treeUri == null) {
                                    editor.putString(PREF_TREE_URI, null);
                                    editor.apply();
                                    Utils.errMsg(this, "Failed to get " +
                                            "persistent " +
                                            "access permissions");
                                    return;
                                }
                                // Persist access permissions.
                                try {
                                    this.getContentResolver().takePersistableUriPermission(treeUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    // Save the current treeUri as PREF_TREE_URI
                                    editor.putString(PREF_TREE_URI,
                                            treeUri.toString());
                                    editor.apply();
                                    // Trim the persisted permissions
                                    UriUtils.trimPermissions(this, 1);
                                } catch (Exception ex) {
                                    String msg = "Failed to " +
                                            "takePersistableUriPermission for "
                                            + treeUri.getPath();
                                    Utils.excMsg(this, msg, ex);
                                }
                                Log.d(TAG,
                                        "Current permissions (final): "
                                                + UriUtils.getNPersistedPermissions(this));
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error in openDocumentTreeLauncher: " +
                                    "startActivity for result", ex);
                        }
                    });


    // Launcher for display Image
    private final ActivityResultLauncher<Intent> selectImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent intent = result.getData();
                        Bundle extras = intent.getExtras();
                        SharedPreferences.Editor editor =
                                getPreferences(MODE_PRIVATE).edit();
                        try {
                            String imageUri = extras.getString(EXTRA_IMAGE_URI);
                            // Just set the filePath, setNewImage will be
                            // done in onResume
                            editor.putString(PREF_IMAGE_URI, imageUri);
                            editor.apply();
                        } catch (Exception ex) {
                            Utils.excMsg(this, "Did not get file name from " +
                                            "Preferences",
                                    ex);
                            return;
                        }
                        // Reset the preferences to the defaults
                        editor.putFloat(PREF_CENTER_X, X_DEFAULT);
                        editor.putFloat(PREF_CENTER_Y, Y_DEFAULT);
                        editor.putFloat(PREF_SCALE, SCALE_DEFAULT);
                        editor.apply();
                    });

    // Launcher for saving GPX
    private final ActivityResultLauncher<Intent> saveGpxLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent intent = result.getData();
                        Uri uri;
                        if (intent == null) {
                            Utils.errMsg(this, "Got invalid Uri for creating " +
                                    "GPX file");
                        } else {
                            uri = intent.getData();
                            if (uri != null) {
                                List<String> segments = uri.getPathSegments();
                                Uri.Builder builder = new Uri.Builder();
                                for (int i = 0; i < segments.size() - 1; i++) {
                                    builder.appendPath(segments.get(i));
                                }
                                Uri parent = builder.build();
                                Log.d(TAG, "uri=" + uri + " parent=" + parent);
                                doSaveGpx(uri);
                            }
                        }
                    });

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
                    } else if (ACTION_ERROR.equals(action)) {
                        String msg = intent.getStringExtra(EXTRA_ERROR);
                        Utils.errMsg(MapImageActivity.this, msg);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, this.getClass().getSimpleName() + ": onCreate");
        super.onCreate(savedInstanceState);
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

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
                + " mBroadcastReceiver==null=" + (mBroadcastReceiver == null));
        Log.d(TAG, "    mLocationDenied=" + mLocationDenied
                + " mFineLocationAsked=" + mFineLocationAsked);

        // Check location (Will prompt user if either not granted)
        if (!isLocationGranted()) {
            requestLocationPermission();
        }
        if (!mFineLocationAsked && isLocationGranted() && !isFineLocationGranted()) {
            mFineLocationAsked = true;
            Utils.warnMsg(this,
                    "FINE location permission is not granted. Location "
                            + " results will be inaccurate. You can fix this"
                            + " in Settings|Apps for Map Image.");
        }
        // Allow if either is selected
        mUseLocation = isLocationGranted();
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mTracking = prefs.getBoolean(PREF_TRACKING, false);
        if (!mUseLocation) mTracking = false;
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
                + " mUpdateInterval=" + mUpdateInterval);
        String uriStr = prefs.getString(PREF_IMAGE_URI, null);
        Uri uri = null;
        if (uriStr != null) {
            uri = Uri.parse(uriStr);
            Log.d(TAG,
                    this.getClass().getSimpleName() + ": onResume: uri="
                            + uri.getLastPathSegment());
        }
        Log.d(TAG, this.getClass().getSimpleName()
                + ": onResume (3): mUseLocation=" + mUseLocation
                + " mUpdateInterval=" + mUpdateInterval);
        if (uri == null) {
            mMapCalibration = null;
            setNoImage();
        } else {
            if (mImageView != null) {
                setNewImage(uri);
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
            Intent intent = registerReceiver(mBroadcastReceiver,
                    makeBroadcastIntentFilter());
            Log.d(TAG, "    registerReceiver returned null="
                    + (intent == null));
        }
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume (4): end");
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause: " +
                "mUseLocation="
                + mUseLocation + " mUpdateInterval=" + mUpdateInterval);
        Log.d(TAG, "    mBroadcastReceiver==null="
                + (mBroadcastReceiver == null));
        super.onPause();
        SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
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
        try {
            // There is no easy way to tell if it is registered
            if (mBroadcastReceiver != null) {
                unregisterReceiver(mBroadcastReceiver);
            }
        } catch (Exception ex) {
            Log.d(TAG, this.getClass().getSimpleName()
                    + ": onPause: Exception on unregisterReceiver");
        }
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause: end");
    }

    @Override
    public void onBackPressed() {
        // This seems to be necessary with Android 12
        // Otherwise onDestroy is not called
        Log.d(TAG, this.getClass().getSimpleName() + ": onBackPressed");
        finish();
        super.onBackPressed();
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[]
                                                   permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "    mUseLocation=" + mUseLocation);
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode == REQ_ACCESS_LOCATION) {
            // LOCATION (Handle multiple)
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.
                        permission.ACCESS_COARSE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_LOCATION: COARSE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_LOCATION: COARSE_LOCATION " +
                                "denied");
                    }
                } else if (permissions[i].equals(Manifest.
                        permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "REQ_ACCESS_LOCATION: FINE_LOCATION " +
                                "granted");
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "REQ_ACCESS_LOCATION: FINE_LOCATION " +
                                "denied");
                    }
                }
            }
            mUseLocation = isLocationGranted();
            mLocationDenied = !mUseLocation;
            if (mUseLocation) {
                setupLocation();
            } else {
                disableLocation();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is
        // present.
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
        if (id == R.id.open) {
            selectImage();
            return true;
        } else if (id == R.id.open_image_location) {
            openImageForLocation();
            return true;
        } else if (id == R.id.start_location) {
            if (mUseLocation) {
                disableLocation();
            } else {
                if (mUseLocation) {
                    Utils.warnMsg(this, "Location is already started");
                    return true;
                }
                mUseLocation = true;
                setupLocation();
            }
            return true;
        } else if (id == R.id.start_tracking) {
            mTracking = !mTracking;
            if (mTracking && !isFineLocationGranted()) {
                Utils.warnMsg(this,
                        "FINE location permission is not granted. Tracking "
                                + " results will be inaccurate. You can fix " +
                                "this"
                                + " in Settings|Apps for Map Image.");
            }
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
        } else if (id == R.id.save_gpx) {
            saveGpx();
            return true;
        } else if (id == R.id.startclear_track) {
            if (mLocationService != null &&
                    mLocationService.mTrackpointList != null) {
                mLocationService.mTrackpointList.clear();
            }
            return true;
        } else if (id == R.id.set_update_interval) {
            setUpdateInterval();
            return true;
        } else if (id == R.id.image_info) {
            info();
            return true;
        } else if (id == R.id.reset) {
            reset();
            return true;
        } else if (id == R.id.choose_image_directory) {
            chooseImageDirectory();
            return true;
        } else if (id == R.id.help) {
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
            StringBuilder info = new StringBuilder();
            // Filename
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String uriStr = prefs.getString(PREF_IMAGE_URI, null);
            if (uriStr == null) {
                info.append("No file name\n");
            } else {
                Uri uri = Uri.parse(uriStr);
                if (uri == null) {
                    info.append("No file name\n");
                } else {
                    info.append(UriUtils.getDisplayName(this, uri)).append(
                            "\n");
                }
            }
            int dWidth = mImageView.getSWidth();
            int dHeight = mImageView.getSHeight();
            info.append(String.format(Locale.US, "%d x %d\n", dWidth,
                    dHeight));

            // Get screen size and density
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            if (metrics != null) {
                info.append("Screen Size ").append(metrics.widthPixels).append("x").append(metrics
                        .heightPixels).append(" densityDpi ").append(metrics.densityDpi).append(" ").append("\n");
            }
            // Calibration
            if (mMapCalibration == null || mMapCalibration.getTransform() ==
                    null) {
                info.append("Not calibrated\n");
            } else {
                info.append("Calibrated\n");
                List<MapData> dataList = mMapCalibration.getDataList();
                if (dataList != null) {
                    for (MapData data : dataList) {
                        info.append(String.format(Locale.US,
                                "  %04d   %04d  %11.6f %11.6f\n",
                                data.getX(), data.getY(), +data.getLon(),
                                data.getLat()));
                    }
                }
            }
            // Location
            if (!mUseLocation) {
                info.append("Not using location\n");
            } else {
                if (mLocation == null) {
                    info.append("No location available\n");
                } else {
                    double lon = mLocation.getLongitude();
                    double lat = mLocation.getLatitude();
                    float accuracy = mLocation.getAccuracy();
                    info.append(String.format(Locale.US,
                            "Location %.6f, %.6f +/- %.2f m",
                            mLocation.getLongitude(),
                            mLocation.getLatitude(),
                            accuracy));
                    try {
                        int[] locationVals = mMapCalibration.inverse(lon,
                                lat);
                        if (locationVals != null) {
                            info.append(String.format(Locale.US, " @ (%d," +
                                            " %d)" +
                                            "\n",
                                    locationVals[0],
                                    locationVals[1]));
                            if (locationVals[0] < 0
                                    || locationVals[0] >= dWidth
                                    || locationVals[1] < 0
                                    || locationVals[1] >= dHeight) {
                                info.append("Not within the image\n");
                            }
                        } else {
                            info.append("\n    Error getting location " +
                                    "image " + "coordinates\n");
                        }
                    } catch (Exception ex) {
                        // Do nothing
                    }
                }
            }
            info.append(UriUtils.getRequestedPermissionsInfo(this));
            String treeUriStr = prefs.getString(PREF_TREE_URI, null);
            if (treeUriStr == null) {
                info.append("Image Directory: Not set");
            } else {
                Uri treeUri = Uri.parse(treeUriStr);
                if (treeUri == null) {
                    info.append("Image Directory: Not set");
                } else {
                    info.append("Image Directory: ").append(treeUri.getPath());
                }
            }
            Utils.infoMsg(this, info.toString());
        } catch (Throwable t) {
            Utils.excMsg(this, "Error showing info", t);
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
            intent.putExtra(INFO_URL, "file:///android_asset/mapimage" +
                    ".html");
            startActivity(intent);
        } catch (Exception ex) {
            Utils.excMsg(this, getString(R.string.help_show_error), ex);
        }
    }

    /**
     * Brings up an ImageListActivity with a list of files to open.
     */
    private void selectImage() {
        Intent intent = new Intent(this, ImageFileListActivity.class);
        Log.d(TAG, this.getClass().getSimpleName() + ".selectFile");
        selectImageLauncher.launch(intent);
    }

    /**
     * Opens the file that contains the current location
     */
    private void openImageForLocation() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "openImageForLocation:" + " mUseLocation=" +
                mUseLocation);
        if (!mUseLocation) {
            Utils.errMsg(this, "Not using location. Try Start Location.");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest
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
        List<ImageFileListActivity.UriData> fileList =
                ImageFileListActivity.getUriList(this);
        if (fileList == null) {
            Utils.errMsg(this,
                    "openImageForLocation: Failed to get liat of image " +
                            "files");
            return;
        }
        Log.d(TAG, " fileList.size()=" + fileList.size());
        final List<ImageFileListActivity.UriData> foundList =
                new ArrayList<>();
        for (ImageFileListActivity.UriData uriData : fileList) {
            if (fileContainsLocation(uriData.uri, lat, lon)) {
                foundList.add(uriData);
            }
        }
        if (foundList.size() == 0) {
            Utils.infoMsg(this, "No images contain the current location");
            return;
        }

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[foundList.size()];
        for (int i = 0; i < foundList.size(); i++) {
            items[i] = foundList.get(i).displayName;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.open_image_location_title));
        builder.setSingleChoiceItems(items, 0,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= foundList.size()) {
                        Utils.errMsg(MapImageActivity.this,
                                "Invalid item");
                        return;
                    }
                    SharedPreferences.Editor editor =
                            getPreferences(MODE_PRIVATE).edit();
                    editor.putString(PREF_IMAGE_URI,
                            foundList.get(item).uri.toString());
                    // Reset the preferences to the defaults
                    editor.putFloat(PREF_CENTER_X, X_DEFAULT);
                    editor.putFloat(PREF_CENTER_Y, Y_DEFAULT);
                    editor.putFloat(PREF_SCALE, SCALE_DEFAULT);
                    editor.apply();
                    setNewImage(foundList.get(item).uri);
                });
        builder.setNegativeButton("Cancel",
                (dialog, whichButton) -> {
                    // Do nothing
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Checks if the current location is within the image in the file.
     *
     * @param uri The Uri for the image file.
     * @param lat The latitude.
     * @param lon The longitude.
     * @return If location is within the image.
     */
    private boolean fileContainsLocation(Uri uri, double lat, double lon) {
        // Get the file width and height
        int dWidth, dHeight;
        Uri calibUri;
        try (InputStream inputStream =
                     getContentResolver().openInputStream(uri)) {
            // Get the bitmap dimensions without loading the Bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, bmOptions);
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
        calibUri = getCalibUri(uri);
        MapCalibration mapCalibration = null;
        if (calibUri != null && UriUtils.exists(this, calibUri)) {
            mapCalibration = new MapCalibration(this);
            try {
                mapCalibration.read(calibUri);
            } catch (Exception ex) {
                mapCalibration = null;
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
     * @param uri The Uri of the file to open.
     */
    private void setNewImage(Uri uri) {
        if (mImageView == null) {
            return;
        }
        if (uri == null) {
            Log.d(TAG, this.getClass().getSimpleName()
                    + "setNewImage: Image Uri is null");
            Utils.errMsg(this, "Image Uri is null");
            return;
        }
        String lastSeg = uri.getLastPathSegment();
        if (!UriUtils.exists(this, uri)) {
            String msg = "File does not exist " + lastSeg;
            Log.d(TAG, this.getClass().getSimpleName()
                    + "setNewImage: " + msg);
            Utils.errMsg(this, msg);
            return;
        }
        mMapCalibration = null;
        setNoImage();
        mImageView.setImage(ImageSource.uri(uri));
        // See if there is a calibration file
        Uri calibUri = getCalibUri(uri);
        if (calibUri == null || !UriUtils.exists(this, calibUri)) {
            Utils.warnMsg(this, "There is no calibration file for "
                    + uri.getLastPathSegment());
            return;
        }
        mMapCalibration = new MapCalibration(this);
        try {
            mMapCalibration.read(calibUri);
        } catch (Exception ex) {
            // Have to use Exception because NumberFormatException might
            // be wrapped in an InvocationTargetException
            Utils.excMsg(this, "Error reading calibration file", ex);
            mMapCalibration = null;
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


//    /**
//     * Checks if the service is running
//     *
//     * @param serviceClass The class of the service.
//     * @return If service is running or not.
//     */
//    private boolean isServiceRunning(Class<?> serviceClass) {
//        ActivityManager manager =
//                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
//        for (ActivityManager.RunningServiceInfo service :
//                manager.getRunningServices(Integer.MAX_VALUE)) {
//            if (serviceClass.getName().equals(service.service.getClassName
//            ())) {
//                return true;
//            }
//        }
//        return false;
//    }

    /**
     * Initializes location parameters.  Does nothing if not using location.
     */
    private void setupLocation() {
        Log.d(TAG, this.getClass().getSimpleName() + ": " +
                "setupLocation:" + " mUseLocation=" +
                mUseLocation);
        // Check location
        if (!isLocationGranted()) return;
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
            Utils.errMsg(this, "No tracks: Location service is not " +
                    "running");
            return;
        }
        List<Trackpoint> trackpointList = mLocationService.mTrackpointList;
        if (trackpointList == null || trackpointList.isEmpty()) {
            Utils.errMsg(this, "There are no tracks");
            return;
        }

        // Prompt
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.gpx_save_title);
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
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
                (dialog1, which) -> {
                    String prefix = prefixBox.getText().toString();
                    String category = categoryBox.getText().toString();
                    String location = locationBox.getText().toString();
                    String suffix = suffixBox.getText().toString();
                    SharedPreferences.Editor editor =
                            getPreferences(MODE_PRIVATE).edit();
                    editor.putString(PREF_GPX_FILENAME_PREFIX, prefix);
                    editor.putString(PREF_GPX_CATEGORY, category);
                    editor.putString(PREF_GPX_LOCATION, location);
                    editor.putString(PREF_GPX_FILENAME_SUFFIX, suffix);
                    editor.apply();
                    generateGpxSaveIntent(prefix, category, location,
                            suffix);
                });
        dialog.setNegativeButton(R.string.cancel,
                (dialog12, which) -> {
                });
        dialog.show();
    }

    /**
     * Generates the filename and sets the Intent.
     *
     * @param prefix   Prefix for the file name
     * @param category Category.
     * @param location Location.
     * @param suffix   Suffix for the file name
     */
    private void generateGpxSaveIntent(String prefix, String category,
                                       String location,
                                       String suffix) {
        Log.d(TAG, this.getClass().getSimpleName());
        try {
            List<Trackpoint> trackpointList =
                    mLocationService.mTrackpointList;
            SimpleDateFormat trackpointFormatter = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            trackpointFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
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
            String date = trackpointFormatter.format(firstTkptDate);
            String fileName = prefix.replaceAll("\\s+", "_")
                    + "_" + date;
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

            // Generate the Intent
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/gpx+xml");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            // Set initialDir
            SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String lastGpxUri = prefs.getString(PREF_LAST_GPX_URI,
                    null);
            if (lastGpxUri != null) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        lastGpxUri);
            }
            saveGpxLauncher.launch(intent);
        } catch (Exception ex) {
            Utils.excMsg(this, "Error requesting saving of GPX file", ex);
        }
    }

    /**
     * Does the actual writing for the save.
     *
     * @param uri The Uri to use for writing.
     */
    private void doSaveGpx(Uri uri) {
        String name, msg;
        ParcelFileDescriptor pfd;
        try {
            pfd = getContentResolver().
                    openFileDescriptor(uri, "w");
            try (FileWriter writer =
                         new FileWriter(pfd.getFileDescriptor());
                 PrintWriter out = new PrintWriter(writer)) {
                List<Trackpoint> trackpointList =
                        mLocationService.mTrackpointList;
                SimpleDateFormat trackpointFormatter = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                trackpointFormatter.setTimeZone(TimeZone.getTimeZone("GMT"
                ));
                try {
                    PackageManager pm = getPackageManager();
                    PackageInfo po =
                            pm.getPackageInfo(this.getPackageName(),
                                    0);
                    name = "MapImage " + po.versionName;
                } catch (Exception ex) {
                    name = "MapImage";
                }
                Date firstTkptDate = new Date();
                for (Trackpoint tkpt : trackpointList) {
                    if (tkpt == null) continue;
                    firstTkptDate = new Date(tkpt.time);
                    break;
                }
                String date = trackpointFormatter.format(firstTkptDate);
                String line, lat, lon, ele;
                long time;
                boolean prevTrackpointNull = true;
                Log.d(TAG, "");
                int nItem = 0;
                int size = trackpointList.size();
                // Write header and beginning lines
                out.write(String.format(GPXUtils.GPX_FILE_START_LINES, name,
                        date));
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
                msg = "Wrote " + uri.getLastPathSegment();
                Log.d(TAG, msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                // Save the uri
                SharedPreferences.Editor editor =
                        getPreferences(MODE_PRIVATE).edit();
                editor.putString(PREF_LAST_GPX_URI, uri.toString());
                editor.apply();
            }
        } catch (Exception ex) {
            msg = "Error writing " + uri.getPath();
            Log.e(TAG, msg);
            Log.e(TAG, Log.getStackTraceString(ex));
            Utils.excMsg(this, msg, ex);
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
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= mUpdateIntervals.length) {
                        Utils.errMsg(MapImageActivity.this,
                                "Invalid update interval");
                        mUpdateInterval = 0;
                    } else {
                        mUpdateInterval = item;
                    }
                    SharedPreferences.Editor editor =
                            getPreferences(MODE_PRIVATE).edit();
                    editor.putInt(PREF_UPDATE_INTERVAL,
                            mUpdateInterval);
                    editor.apply();
                    if (mLocationService != null) {
                        try {
                            mLocationService.setUpdateInterval(mUpdateInterval);
                        } catch (final SecurityException ex) {
                            runOnUiThread(() -> Utils.excMsg(MapImageActivity.this,
                                    "SecurityException during "
                                            + "setupLocation", ex));
                        } catch (final IllegalArgumentException ex) {
                            runOnUiThread(() -> Utils.excMsg(MapImageActivity.this,
                                    "IllegalArgument exception " +
                                            "during " +
                                            "setupLocation", ex));
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
    private List<PointF> getPointsFromTrackpoints
    (List<Trackpoint> trackPointList) {
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
     * Brings up a system file chooser to get the image directory
     */
    private void chooseImageDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION &
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        openDocumentTreeLauncher.launch(intent);
    }

    /**
     * Get the calibration file Uri corresponding to the given Uri.
     *
     * @param uri The Uri.
     * @return The calibration Uri.
     */
    private Uri getCalibUri(Uri uri) {
//        Log.d(TAG, "getCalibUri: uri=" + uri.getLastPathSegment());
        if (uri == null) return null;
        Uri calibUri = null;
        String uriStr = uri.toString();
        int i = uriStr.lastIndexOf('.');
        String baseStr;
        if (i > 0) {
            baseStr = uriStr.substring(0, i + 1);
            calibUri = Uri.parse(baseStr + CALIB_EXT);
        }
        return calibUri;
    }

    /**
     * Determines if either COARSE or FINE location permission is granted.
     *
     * @return If granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isLocationGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED |
                ContextCompat.checkSelfPermission(this, Manifest
                        .permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Determines if FINE location permission is granted.
     *
     * @return If granted.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isFineLocationGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest
                .permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Determines if location (COARSE or both) is granted, and calls
     * requestPermissions if location not been previously denied.
     */
    protected void requestLocationPermission() {
        Log.d(TAG, this.getClass().getSimpleName()
                + ": requestLocationPermission: mLocationDenied=" + mLocationDenied
                + " mFineLocationAsked=" + mFineLocationAsked);
        // Check location
        if (!isLocationGranted() && !mLocationDenied) {
            // One or both location permissions are not granted
            Log.d(TAG, "    Calling requestPermissions");
            requestPermissions(new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_ACCESS_LOCATION);
        }
    }
}
