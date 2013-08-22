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
public interface IConstants {
	/** Tag to associate with log messages. */
	public static final String TAG = "MapImage";

	public static final String DEBUG_DIRNAME = "Dilbert";
	public static final String DEBUG_FILENAME = "Dilbert-1989-04-16.png";
	public static final String DEBUG_RAW_DIRNAME = "Test";
	public static final String DEBUG_RAW_FILENAME = "Dilbert-1989-04-16.png";

	/** Extension for calibration files */
	public static final String CALIB_EXT = "calib";

	/** Directory on the SD card where strips are saved */
	public static final String SD_CARD_MAP_IMAGE_DIRECTORY = "MapImage";

	public static final String PREF_FILENAME = "fileName";
	public static final String PREF_USE_LOCATION = "useLocation";

	// Messages
	/** Request code for displaying aa image. */
	public static final int DISPLAY_IMAGE = 0;
	/** Result code for DISPLAY_MESSAGE indicating the previous message. */
	public static final String OPEN_FILE_PATH = "OpenFileName";
}
