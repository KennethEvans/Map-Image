package net.kenevans.android.mapimage;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class MapImageLocationService extends Service implements IConstants {
    private LocationManager mLocationManager = null;
    private String mProvider;
    private LocationListener mLocationListener;

    private final IBinder mBinder = new LocalBinder();
    int mUpdateInterval;

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.d(TAG,
                    "MapImageLocationService: LocationListener: provider=" + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
//            Log.d(TAG,
//                    "MapImageLocationService: onLocationChanged: " +
//                    location);
//            mLastLocation.set(location);
            final Intent intent = new Intent(ACTION_LOCATION_CHANGED);
            intent.putExtra(EXTRA_LAT, location.getLatitude());
            intent.putExtra(EXTRA_LON, location.getLongitude());
            intent.putExtra(EXTRA_ACCURACY, location.getAccuracy());
            sendBroadcast(intent);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG,
                    "MapImageLocationService: onProviderDisabled: " + provider);
            final Intent intent = new Intent(ACTION_PROVIDER_DISABLED);

            sendBroadcast(intent);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG,
                    "MapImageLocationService: onProviderEnabled: " + provider);
            final Intent intent = new Intent(ACTION_PROVIDER_ENABLED);

            sendBroadcast(intent);
        }

        @Override
        public void onStatusChanged(String provider, int status,
                                    Bundle extras) {
            Log.d(TAG, "MapImageLocationService: onStatusChanged: " + provider);
            final Intent intent = new Intent(ACTION_STATUS_CHANGED);
            intent.putExtra(EXTRA_STATUS, status);
            sendBroadcast(intent);
        }
    }

    class LocalBinder extends Binder {
        MapImageLocationService getService() {
            return MapImageLocationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "MapImageLocationService: onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "MapImageLocationService: onUnbind");
//        stopDatabase();
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MapImageLocationService: onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "MapImageLocationService: onCreate");
        setupLocation();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "MapImageLocationService: onDestroy");
        super.onDestroy();     // Does nothing
        disableLocation();
    }

    public void setUpdateInterval(int updateInterval) throws SecurityException,
            IllegalArgumentException {
        Log.d(TAG, "MapImageLocationService: setUpdateInterval: " +
                "updateInterval=" + updateInterval);
        mUpdateInterval = updateInterval;
        if (mUpdateInterval >= LOCATION_UPDATE_TIMES.length
                || mUpdateInterval < 0) {
            mUpdateInterval = 0;
        }
        if (mLocationManager == null) {
            setupLocation();
            return;
        }
        mLocationManager.removeUpdates(mLocationListener);
        mLocationManager.requestLocationUpdates(
                mProvider,
                LOCATION_UPDATE_TIMES[mUpdateInterval],
                LOCATION_UPDATE_DISTANCES[mUpdateInterval],
                mLocationListener);
    }

    public void setupLocation() {
        Log.d(TAG, "MapImageLocationService: setupLocation");
        if (mLocationManager == null) {
            mLocationManager =
                    (LocationManager) getApplicationContext().
                            getSystemService(Context.LOCATION_SERVICE);
        }
        mLocationListener = new LocationListener(LocationManager.GPS_PROVIDER);
        mProvider = LocationManager.GPS_PROVIDER;
        try {
            mLocationManager.requestLocationUpdates(
                    mProvider,
                    LOCATION_UPDATE_TIMES[mUpdateInterval],
                    LOCATION_UPDATE_DISTANCES[mUpdateInterval],
                    mLocationListener);
            mLocationListener.mLastLocation = mLocationManager
                    .getLastKnownLocation(mProvider);
            mLocationListener.onLocationChanged(mLocationListener.mLastLocation);
        } catch (final SecurityException ex) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    Utils.excMsg(MapImageLocationService.this,
                            "SecurityException starting " +
                                    "MapImageLocationService", ex);
                }
            });
        } catch (final Exception ex) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    Utils.excMsg(MapImageLocationService.this, "Error " +
                            "starting MapImageLocationService", ex);
                }
            });
        }
    }

    private void disableLocation() {
        Log.d(TAG, "MapImageLocationService: disableLocation");
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
            mLocationManager = null;
        }
        mProvider = null;
    }

}