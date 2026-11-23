package com.safeyoutube.brave;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String YOUTUBE_URL = "https://m.youtube.com";
    private static final String MOBILE_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final int REQ_NOTIF_PERMISSION = 101;

    private static final List<String> AD_HOST_KEYWORDS = Arrays.asList(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.",
            "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com"
    );

    private static final List<String> AD_PATH_KEYWORDS = Arrays.asList(
            "/api/stats/ads",
            "/pagead/",
            "/ptracking",
            "/get_midroll_info",
            "/youtubei/v1/log_event"
    );

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private AudioManager audioManager;

    private static final String QUALITY_AND_ADBLOCK_JS =
            "(function() {" +
            "  try {" +
            "    var exp = Date.now() + 31536000000;" +
            "    localStorage.setItem('yt-player-quality', JSON.stringify({" +
            "      data: JSON.stringify({quality: 360, previousQuality: 360})," +
            "      expiration: exp," +
            "      creation: Date.now()" +
            "    }));" +
            "  } catch(e) {}" +
            "  if (!document.getElementById('yt-safe-style')) {" +
            "    var st = document.createElement('style');" +
            "    st.id = 'yt-safe-style';" +
            "    st.innerHTML = 'ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, ad-slot-renderer, ytm-ad-slot-renderer, .ad-container, .ytp-ad-overlay-container, .ytp-ad-text-overlay { display: none !important; }';" +
            "    (document.head || document.documentElement).appendChild(st);" +
            "  }" +
            "  function enforce360AndSkipAds() {" +
            "    try {" +
            "      var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');" +
            "      if (player) {" +
            "        if (player.classList && player.classList.contains('ad-showing')) {" +
            "          var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern');" +
            "          if (skipBtn) skipBtn.click();" +
            "          var adVid = player.querySelector('video');" +
            "          if (adVid && isFinite(adVid.duration) && adVid.duration > 0) {" +
            "            adVid.muted = true;" +
            "            adVid.currentTime = adVid.duration;" +
            "          }" +
            "        } else {" +
            "          if (typeof player.getPlaybackQuality === 'function') {" +
            "            var cur = player.getPlaybackQuality();" +
            "            if (cur && cur !== 'medium' && cur !== 'small' && cur !== 'tiny') {" +
            "              if (typeof player.setPlaybackQualityRange === 'function') {" +
            "                player.setPlaybackQualityRange('medium', 'medium');" +
            "              }" +
            "              if (typeof player.setPlaybackQuality === 'function') {" +
            "                player.setPlaybackQuality('medium');" +
            "              }" +
            "            }" +
            "          } else {" +
            "            if (typeof player.setPlaybackQualityRange === 'function') {" +
            "              player.setPlaybackQualityRange('medium', 'medium');" +
            "            }" +
            "            if (typeof player.setPlaybackQuality === 'function') {" +
            "              player.setPlaybackQuality('medium');" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    } catch(e) {}" +
            "  }" +
            "  if (!window.__ytSafe360Interval) {" +
            "    enforce360AndSkipAds();" +
            "    window.__ytSafe360Interval = setInterval(enforce360AndSkipAds, 1200);" +
            "    window.addEventListener('yt-navigate-finish', enforce360AndSkipAds);" +
            "  } else {" +
            "    enforce360AndSkipAds();" +
            "  }" +
            "})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        webView = findViewById(R.id.webView);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);

        requestNotificationPermissionIfNeeded();
        startVolumeLimiter();
        setupWebView();
        setupBackNavigation();

        webView.loadUrl(YOUTUBE_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(MOBILE_CHROME_UA);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(0xFF0F0F0F);

        webView.setWebViewClient(new WebViewClient() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && isAdRequest(uri)) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(QUALITY_AND_ADBLOCK_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(QUALITY_AND_ADBLOCK_JS, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(view);
                fullscreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                hideSystemBars();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private boolean isAdRequest(Uri uri) {
        String host = uri.getHost();
        String path = uri.getPath();
        if (host != null) {
            String lowerHost = host.toLowerCase();
            for (String kw : AD_HOST_KEYWORDS) {
                if (lowerHost.contains(kw)) return true;
            }
        }
        if (path != null) {
            String lowerPath = path.toLowerCase();
            for (String kw : AD_PATH_KEYWORDS) {
                if (lowerPath.contains(kw)) return true;
            }
        }
        return false;
    }

    private void exitFullscreen() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        showSystemBars();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void showSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    exitFullscreen();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    stopVolumeLimiter();
                    finish();
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && audioManager != null) {
            int capVol = VolumeLimiterService.getMaxAllowedVolume(audioManager);
            int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (curVol >= capVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, capVol, AudioManager.FLAG_SHOW_UI);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF_PERMISSION
                );
            }
        }
    }

    private void startVolumeLimiter() {
        if (audioManager != null) {
            int capVol = VolumeLimiterService.getMaxAllowedVolume(audioManager);
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > capVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, capVol, 0);
            }
        }
        Intent serviceIntent = new Intent(this, VolumeLimiterService.class);
        serviceIntent.setAction(VolumeLimiterService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopVolumeLimiter() {
        Intent stopIntent = new Intent(this, VolumeLimiterService.class);
        stopIntent.setAction(VolumeLimiterService.ACTION_STOP);
        startService(stopIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        startVolumeLimiter();
    }

    @Override
    protected void onDestroy() {
        stopVolumeLimiter();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
