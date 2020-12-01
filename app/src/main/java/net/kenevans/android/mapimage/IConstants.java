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
    String EXTRA_OPEN_FILE_PATH = PACKAGE_NAME + ".OpenFileName";

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
    String SD_CARD_MAP_IMAGE_DIRECTORY = "MapImage";

    String PREF_FILENAME = "fileName";
    String PREF_GPX_FILENAME_SUFFIX = "gpxFileNameSuffix";
    String PREF_GPX_FILENAME_PREFIX = "gpxFileNamePrefix";
    String PREF_GPX_CATEGORY = "gpxCategory";
    String PREF_GPX_LOCATION = "gpxLocation";
    String PREF_USE_LOCATION = "useLocation";
    String PREF_TRACKING = "tracking";
    String PREF_IMAGE_DIRECTORY = "imageDirectory";
    String PREF_UPDATE_INTERVAL = "updateInterval";
    String PREF_SCALE = "scale";
    String PREF_CENTER_X = "centerX";
    String PREF_CENTER_Y = "centerY";

    /**
     * Request code for displaying an image.
     */
    int DISPLAY_IMAGE_REQ = 0;

    /**
     * Request code for FINE_LOCATION.
     */
    int ACCESS_FINE_LOCATION_REQ = 1;

    /**
     * Request code for READ_EXTERNAL_STORAGE.
     */
    int ACCESS_READ_EXTERNAL_STORAGE_REQ = 2;

    /**
     * Request code for WRITE_EXTERNAL_STORAGE.
     */
    int ACCESS_WRITE_EXTERNAL_STORAGE_REQ = 3;

    /**
     * Result code for creating a document.
     */
    int CREATE_DOCUMENT = 10;


    /**
     * Notification ID for managing notifications.
     */
    int NOTIFICATION_ID = 1;
}
