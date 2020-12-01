package net.kenevans.android.mapimage;

@SuppressWarnings("WeakerAccess")
public class GPXUtils {
    /**
     * Lines for the beginning of a GPX file with two %s for creator name.time.
     * There is no <metadata>.</metadata>
     */
    public static final String GPX_FILE_START_LINES = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" " +
            "creator=\"%s\" version=\"1.1\">\n"
            + "    <metadata>\n"
            + "        <time>%s</time>\n"
            + "    </metadata>\n"
            + "    <trk>\n" + "        <trkseg>\n";

    /**
     * Lines for the beginning of a GPX file with four %s for creator name,
     * creation time, category, and location.
     */
    public static final String GPX_FILE_START_LINES_EX = ""
            + "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" " +
            "creator=\"%s\" version=\"1.1\">\n"
            + "    <metadata>\n"
            + "        <time>%s</time>\n"
            + "        <category>%s</category>\n"
            + "        <location>%s</location>\n"
            + "    </metadata>\n"
            + "    <trk>\n" + "        <trkseg>\n";

    /**
     * Lines for a track point in a GPX file with 4 %s for lat, lon, ele, time.
     */
    public static final String GPX_FILE_TRACK_LINES = ""
            + "            <trkpt lat=\"%s\" lon=\"%s\">\n"
            + "                <ele>%s</ele>\n"
            + "                <time>%s</time>\n"
            + "            </trkpt>\n";

    /**
     * Lines for the end of a GPX file with no %s.
     */
    public static final String GPX_FILE_END_LINES = "        </trkseg>\n"
            + "    </trk>\n" + "</gpx>\n";

    /**
     * Lines for a new segment with no %s.
     */
    public static final String GPX_FILE_NEW_SEGMENT = "        </trkseg>\n"
            + "        <trkseg>\n";

}
