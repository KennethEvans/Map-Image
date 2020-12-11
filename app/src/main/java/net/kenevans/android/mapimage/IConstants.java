package net.kenevans.android.mapimage;

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

/**
 * Holds constant values used by several classes in the application.
 */
interface IConstants {
    /**
     * Tag to associate with log messages.
     */
    String TAG = "MapImage";

    String PACKAGE_NAME = "net.kenevans.android.mapimage";
    String ACTION_LOCATION_CHANGED = PACKAGE_NAME + ".ACTION_LOCATION_CHANGED";
    String ACTION_PROVIDER_DISABLED = PACKAGE_NAME +
            ".ACTION_PROVIDER_DISABLED";
    String ACTION_PROVIDER_ENABLED = PACKAGE_NAME + ".ACTION_PROVIDER_ENABLED";
    String ACTION_STATUS_CHANGED = PACKAGE_NAME + ".ACTION_STATUS_CHANGED";
    String ACTION_ERROR = PACKAGE_NAME + ".ACTION_ERROR";
    String EXTRA_LAT = PACKAGE_NAME + ".extraLatitude";
    String EXTRA_LON = PACKAGE_NAME + ".extraLongitude";
    String EXTRA_STATUS = PACKAGE_NAME + ".extraStatus";
    String EXTRA_ACCURACY = PACKAGE_NAME + ".extraAccuracy";
    String EXTRA_IMAGE_URI = PACKAGE_NAME + "extraImageUri";
    String EXTRA_ERROR = PACKAGE_NAME + "extraError";

    /**
     * Key for information URL sent to InfoActivity.
     */
    String INFO_URL = "InformationURL";

    /**
     * Extension for calibration files
     */
    String CALIB_EXT = "calib";

    /**
     * Minimum update interval array. Values are in ms.
     */
    long[] LOCATION_UPDATE_TIMES = {0, 1000, 60000};

    /**
     * Minimum update distance array Values are in m.
     */
    float[] LOCATION_UPDATE_DISTANCES = {0f, .5f, 10f};

    /**
     * The default x value of the center of the image.
     */
    int X_DEFAULT = 0;
    /**
     * The default y value of the center of the image.
     */
    int Y_DEFAULT = 0;
    /**
     * The default scale of the image.
     */
    int SCALE_DEFAULT = 1;

    /**
     * Minimum Dpi for zooming.  Default is 160.
     */
    int MIN_DPI = 20;

    /**
     * Directory on the SD card where strips are saved
     */
    String PREF_GPX_FILENAME_SUFFIX = "gpxFileNameSuffix";
    String PREF_GPX_FILENAME_PREFIX = "gpxFileNamePrefix";
    String PREF_GPX_CATEGORY = "gpxCategory";
    String PREF_GPX_LOCATION = "gpxLocation";
    String PREF_LAST_GPX_URI = "gpxLastGpxUri";
    String PREF_USE_LOCATION = "useLocation";
    String PREF_USE_BACKGROUND_LOCATION = "useBackgroundLocation";
    String PREF_TRACKING = "tracking";
    String PREF_IMAGE_URI = "imageUri";
    String PREF_UPDATE_INTERVAL = "updateInterval";
    String PREF_SCALE = "scale";
    String PREF_CENTER_X = "centerX";
    String PREF_CENTER_Y = "centerY";
    String PREF_TREE_URI = "tree_uri";

    // Requests
    int REQ_DISPLAY_IMAGE = 0;
    int REQ_ACCESS_LOCATION = 1;
    int REQ_ACCESS_READ_EXTERNAL_STORAGE = 2;
//    int REQ_ACCESS_WRITE_EXTERNAL_STORAGE = 3;
    int REQ_GET_TREE = 10;
    int REQ_CREATE_DOCUMENT = 11;

    /**
     * Notification ID for managing notifications.
     */
    int NOTIFICATION_ID = 1;
}
