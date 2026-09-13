package br.com.palmeiras.rondaseguranca;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationTrackingService extends Service implements LocationListener {
    public static final String ACTION_START = "br.com.palmeiras.ronda.START";
    public static final String ACTION_STOP = "br.com.palmeiras.ronda.STOP";
    private static final String CHANNEL_ID = "ronda_ativa";
    private static final int NOTIFICATION_ID = 4101;
    private static final long INTERVAL_MS = 15_000L;
    private static final float MIN_DISTANCE_M = 5f;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private LocationManager locationManager;
    private String rondaId;
    private String vigilante;
    private Location lastLocation;
    private long stationarySince;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            rondaId = intent.getStringExtra("rondaId");
            vigilante = intent.getStringExtra("vigilante");
            getSharedPreferences("ronda", MODE_PRIVATE).edit()
                    .putString("rondaId", rondaId).putString("vigilante", vigilante).apply();
        } else {
            rondaId = getSharedPreferences("ronda", MODE_PRIVATE).getString("rondaId", null);
            vigilante = getSharedPreferences("ronda", MODE_PRIVATE).getString("vigilante", "Vigilante");
        }
        if (rondaId == null || rondaId.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        startForeground(NOTIFICATION_ID, buildNotification("Localização ativa"));
        beginLocationUpdates();
        return START_STICKY;
    }

    private void beginLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return;
        }
        locationManager.removeUpdates(this);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, this);
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, this);
    }

    @Override public void onLocationChanged(Location location) {
        if (location.getAccuracy() > 50f) return;
        float distance = lastLocation == null ? 0f : lastLocation.distanceTo(location);
        if (lastLocation == null || distance >= 15f) stationarySince = SystemClock.elapsedRealtime();
        if (stationarySince == 0) stationarySince = SystemClock.elapsedRealtime();
        double stationaryMinutes = (SystemClock.elapsedRealtime() - stationarySince) / 60000.0;
        String status = stationaryMinutes >= 5 ? "PARADO" : "NATIVO";
        lastLocation = location;
        updateNotification(status.equals("PARADO") ? "Sem movimento há " + (int) stationaryMinutes + " min" : "Registrando percurso");
        sendPosition(location, distance, stationaryMinutes, status);
    }

    private void sendPosition(Location location, float distance, double stationaryMinutes, String status) {
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject p = new JSONObject();
                p.put("action", "nativePosition");
                p.put("rondaId", rondaId);
                p.put("lat", location.getLatitude());
                p.put("lng", location.getLongitude());
                p.put("accuracy", location.getAccuracy());
                p.put("distance", distance);
                p.put("speed", Math.max(0, location.getSpeed() * 3.6));
                p.put("stationary", stationaryMinutes);
                p.put("status", status);
                p.put("time", location.getTime());
                p.put("battery", batteryPercent());
                connection = (HttpURLConnection) new URL(MainActivity.APP_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] body = p.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                connection.getResponseCode();
            } catch (Exception ignored) {
                // A próxima posição será enviada normalmente; falhas ficam visíveis pela ausência na planilha.
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private int batteryPercent() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Ronda em andamento", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantém o registro de localização durante a ronda.");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(br.com.palmeiras.rondaseguranca.R.drawable.ic_launcher)
                .setContentTitle("Ronda em andamento")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void stopTracking() {
        if (locationManager != null) locationManager.removeUpdates(this);
        getSharedPreferences("ronda", MODE_PRIVATE).edit().clear().apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() { if (locationManager != null) locationManager.removeUpdates(this); network.shutdown(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { updateNotification("GPS desligado"); }
}
