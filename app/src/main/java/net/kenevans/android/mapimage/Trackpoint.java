package net.kenevans.android.mapimage;

class Trackpoint {
    double lat;
    double lon;
    double alt;
    long time;

    Trackpoint(double lat, double lon, double alt, long time) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.time = time;
    }
}
