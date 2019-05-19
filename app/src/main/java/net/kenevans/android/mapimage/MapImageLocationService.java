package net.kenevans.android.mapimage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MapImageLocationService extends Service implements IConstants {
    private LocationManager mLocationManager = null;
    private String mProvider;
    private LocationListener mLocationListener;
    private Notification notification;

    private final IBinder mBinder = new LocalBinder();
    int mUpdateInterval;
    private boolean mTracking;
    public List<Trackpoint> mTrackpointList = new ArrayList<>();

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        LocationListener(String provider) {
            Log.d(TAG,
                    "MapImageLocationService: LocationListener: provider=" + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG,
                    "MapImageLocationService: onLocationChanged: mTracking="
                            + mTracking + " size=" + mTrackpointList.size()
//                            + "\n    " + location
            );
            mLastLocation.set(location);
            if (mTracking) {
                mTrackpointList.add(new Trackpoint(location.getLatitude(),
                        location.getLongitude(), location.getAltitude(),
                        new Date().getTime()));
            }
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
        // Post a notification the service is running
        Notification notification = getNotification();
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);
        // Make it a foreground process
        // Otherwise won't get location when Activity paused.
        startForeground(NOTIFICATION_ID, notification);
        notificationManager.notify(NOTIFICATION_ID, notification);
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

    public String createNotificationChannel(Context context) {
        // NotificationChannels are required for Notifications on O (API 26)
        // and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The user-visible name of the channel.
            CharSequence channelName = getString(R.string.app_name);
            // The user-visible description of the channel.
            String channelDescription =
                    getString(R.string.default_notification_description);
            String channelId =
                    getString(R.string.default_notification_channel_id);
            int channelImportance = NotificationManager.IMPORTANCE_LOW;
            boolean channelEnableVibrate = false;

            // Initializes NotificationChannel.
            NotificationChannel notificationChannel =
                    new NotificationChannel(channelId, channelName,
                            channelImportance);
            notificationChannel.setDescription(channelDescription);
            notificationChannel.enableVibration(channelEnableVibrate);

            // Adds NotificationChannel to system. Attempting to create an
            // existing notificationchannel with its original values performs
            // no operation, so it's safe to perform the below sequence.
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);

            return channelId;
        } else {
            // Returns null for pre-O (26) devices.
            return null;
        }
    }

    /**
     * Gets the notification to be used in startForeground(
     *
     * @return The notification.
     */
    public Notification getNotification() {
        String channnelId = createNotificationChannel(this);
        Intent activityIntent = new Intent(this, MapImageActivity.class);
        PendingIntent viewPendingIntent = PendingIntent.getActivity(this, 0,
                activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder = new
                NotificationCompat.Builder(
                this, channnelId)
                .setSmallIcon(R.drawable.ic_stat_mapimage_notification)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setContentIntent(viewPendingIntent);
        return notificationBuilder.build();
    }

    public boolean setTracking() {
        return mTracking;
    }

    public void setTracking(boolean mTracking) {
        this.mTracking = mTracking;
    }

    public List<Trackpoint> getTrackpointList() {
        return mTrackpointList;
    }

    public void setTrackpointList(List<Trackpoint> mTrackpointList) {
        this.mTrackpointList = mTrackpointList;
    }

    public void clearTrackpointList() {
        mTrackpointList.clear();
    }
}