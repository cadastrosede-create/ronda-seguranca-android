package br.com.palmeiras.rondaseguranca;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    public static final String APP_URL = "https://script.google.com/macros/s/AKfycbyED7quZ_HctFE3ovDecIq7X5A2xqlQv-BtF6OY_0Nhvvf7JhkBCgBrCNBTSNNgqw4fkQ/exec";
    private static final int PERMISSION_REQUEST = 1101;
    private WebView webView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        configureWebView();
        requestOperationalPermissions();
        webView.loadUrl(APP_URL);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(55);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, hasLocationPermission(), false);
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestOperationalPermissions() {
        List<String> needed = new ArrayList<>();
        if (!hasLocationPermission()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else moveTaskToBack(true);
    }

    public static class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        @JavascriptInterface public void startTracking(String rondaId, String vigilante, String configJson) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Autorize a localização para iniciar a ronda.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(context, LocationTrackingService.class);
            intent.setAction(LocationTrackingService.ACTION_START);
            intent.putExtra("rondaId", rondaId);
            intent.putExtra("vigilante", vigilante);
            intent.putExtra("config", configJson);
            context.startForegroundService(intent);
        }

        @JavascriptInterface public void stopTracking() {
            Intent intent = new Intent(context, LocationTrackingService.class);
            intent.setAction(LocationTrackingService.ACTION_STOP);
            context.startService(intent);
        }

        @JavascriptInterface public String deviceId() {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
    }
}
