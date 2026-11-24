package com.mushaf.alquran;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidAudioBridge(), "AndroidAudio");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    public class AndroidAudioBridge {
        private final Map<String, String> folderMap = new HashMap<>();

        AndroidAudioBridge() {
            folderMap.put("ar.abdurrahmaansudais", "Abdurrahmaan_As-Sudais_64kbps");
            folderMap.put("ar.alafasy", "Alafasy_64kbps");
            folderMap.put("ar.husary", "Husary_64kbps");
            folderMap.put("ar.abdulbasitmurattal", "Abdul_Basit_Murattal_64kbps");
            folderMap.put("ar.mahermuaiqly", "Maher_AlMuaiqly_64kbps");
            folderMap.put("ar.saoodshuraym", "Saood_ash-Shuraym_64kbps");
        }

        @JavascriptInterface
        public String getLocalAudioUrl(String reciter, String sPad, String aPad) {
            File cached = new File(new File(getFilesDir(), "audio/" + reciter), sPad + aPad + ".mp3");
            if (cached.exists() && cached.length() > 1024) {
                return "file://" + cached.getAbsolutePath();
            }
            if ("ar.abdurrahmaansudais".equals(reciter)) {
                String assetPath = "audio/sudais/" + sPad + aPad + ".mp3";
                try (InputStream is = getAssets().open(assetPath)) {
                    return "file:///android_asset/" + assetPath;
                } catch (Exception ignored) {
                }
            }
            return "";
        }

        @JavascriptInterface
        public void cacheAyahInBackground(String reciter, String sPad, String aPad, String remoteUrl) {
            executor.execute(() -> downloadFile(reciter, sPad, aPad, remoteUrl));
        }

        @JavascriptInterface
        public void downloadSurah(String reciter, int surahNum, int ayahCount) {
            String folder = folderMap.containsKey(reciter) ? folderMap.get(reciter) : "Abdurrahmaan_As-Sudais_64kbps";
            String sPad = String.format(Locale.US, "%03d", surahNum);
            executor.execute(() -> {
                int done = 0;
                for (int i = 1; i <= ayahCount; i++) {
                    String aPad = String.format(Locale.US, "%03d", i);
                    String url = "https://everyayah.com/data/" + folder + "/" + sPad + aPad + ".mp3";
                    downloadFile(reciter, sPad, aPad, url);
                    done++;
                    final int currentDone = done;
                    runOnUiThread(() -> {
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "window.onOfflineDownloadProgress && window.onOfflineDownloadProgress(" + surahNum + "," + currentDone + "," + ayahCount + ");",
                                null
                            );
                        }
                    });
                }
            });
        }

        private void downloadFile(String reciter, String sPad, String aPad, String remoteUrl) {
            try {
                File dir = new File(getFilesDir(), "audio/" + reciter);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, sPad + aPad + ".mp3");
                if (out.exists() && out.length() > 1024) return;

                HttpURLConnection conn = (HttpURLConnection) new URL(remoteUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    File tmp = new File(dir, sPad + aPad + ".tmp");
                    try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                    tmp.renameTo(out);
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
