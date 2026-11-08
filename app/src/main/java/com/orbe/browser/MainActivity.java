package com.orbe.browser;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.orbe.browser.ui.OrbeAction;
import com.orbe.browser.ui.OrbeFab;
import com.orbe.browser.ui.OrbeSwitch;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orbe — janela enxuta para o Google.
 * Google em tela cheia + botão flutuante que abre a central de comando
 * (modo desktop, limpeza de cache animada e retorno ao Google).
 */
public class MainActivity extends Activity {

    private static final String HOME_URL = "https://www.google.com/";
    private static final String PREFS = "orbe_prefs";
    private static final String K_DESKTOP = "desktop_mode";
    private static final String K_FAB_X = "fab_x";
    private static final String K_FAB_Y = "fab_y";

    private static final String DESKTOP_CSS_JS =
            "(function(){try{if(window.__orbeDesk)return;window.__orbeDesk=1;"
                    + "var m=document.querySelector('meta[name=viewport]');"
                    + "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');"
                    + "(document.head||document.documentElement).appendChild(m);}"
                    + "m.setAttribute('content','width=1280');"
                    + "var s=document.createElement('style');"
                    + "s.textContent='html,body{min-width:1280px!important;}';"
                    + "(document.head||document.documentElement).appendChild(s);}catch(e){}})();";

    private WebView webView;
    private View root, scrim, panel, progressBar;
    private View uiLayer;
    private OrbeFab fab;
    private OrbeSwitch switchDesktop;
    private OrbeAction btnClear, btnHome;
    private ImageView btnClose, btnReload;
    private TextView title, subtitle, lblDesktop, lblDesktopDesc;

    private SharedPreferences prefs;
    private boolean desktopMode;
    private boolean panelOpen;
    private ValueAnimator progressAnim;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bindViews();
        styleTypography();
        setupWebView();
        setupPanel();
        setupFab();
        setupInsets();

        desktopMode = prefs.getBoolean(K_DESKTOP, false);
        switchDesktop.setChecked(desktopMode, false, false);
        applyDesktopMode(desktopMode, false);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String url = urlFromIntent(getIntent());
            webView.loadUrl(url != null ? url : HOME_URL);
        }
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        scrim = findViewById(R.id.scrim);
        uiLayer = findViewById(R.id.uiLayer);
        panel = findViewById(R.id.panel);
        fab = findViewById(R.id.fab);
        switchDesktop = findViewById(R.id.switchDesktop);
        btnClear = findViewById(R.id.btnClear);
        btnHome = findViewById(R.id.btnHome);
        btnClose = findViewById(R.id.btnClose);
        btnReload = findViewById(R.id.btnReload);
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        lblDesktop = findViewById(R.id.lblDesktop);
        lblDesktopDesc = findViewById(R.id.lblDesktopDesc);
    }

    private void styleTypography() {
        title.setTypeface(Ui.font(this, true));
        subtitle.setTypeface(Ui.font(this, true));
        lblDesktop.setTypeface(Ui.font(this, true));
        lblDesktopDesc.setTypeface(Ui.font(this, false));

        btnClear.setup(getString(R.string.clear_cache), R.drawable.ic_broom, OrbeAction.STYLE_PRIMARY);
        btnHome.setup(getString(R.string.back_to_google), R.drawable.ic_home, OrbeAction.STYLE_GHOST);
    }

    private void setupInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getSystemWindowInsetBottom();
            int top = insets.getSystemWindowInsetTop();
            if (uiLayer.getPaddingBottom() != bottom) {
                uiLayer.setPadding(0, 0, 0, bottom);
            }
            if (webView.getPaddingTop() != top) {
                webView.setPadding(0, top, 0, 0);
            }
            return insets;
        });
    }

    // ------------------------------------------------------------------ WebView

    private void setupWebView() {
        webView.setBackgroundColor(Color.WHITE);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(null);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                animateProgress(8);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                animateProgress(100);
                if (desktopMode) view.evaluateJavascript(DESKTOP_CSS_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && !isOnline()) {
                    toast(getString(R.string.offline));
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                animateProgress(newProgress);
            }
        });
    }

    /** Links especiais (tel:, mailto:, whatsapp:, intent:) vão para o app certo. */
    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) {
            return false;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
            toast("Não foi possível abrir este link.");
        }
        return true;
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private void animateProgress(int progress) {
        if (progressBar == null || root.getWidth() == 0) return;
        if (progressAnim != null) progressAnim.cancel();
        ViewGroup.LayoutParams lp = progressBar.getLayoutParams();
        int target = (int) (root.getWidth() * Math.min(100, Math.max(0, progress)) / 100f);
        if (progress >= 100) {
            progressBar.setVisibility(View.VISIBLE);
            progressAnim = ValueAnimator.ofInt(lp.width, root.getWidth());
            progressAnim.setDuration(220);
            progressAnim.addUpdateListener(a -> {
                progressBar.getLayoutParams().width = (int) a.getAnimatedValue();
                progressBar.requestLayout();
            });
            progressAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    progressBar.setVisibility(View.INVISIBLE);
                    progressBar.getLayoutParams().width = 0;
                    progressBar.requestLayout();
                }
            });
        } else {
            progressBar.setVisibility(View.VISIBLE);
            progressAnim = ValueAnimator.ofInt(lp.width, target);
            progressAnim.setDuration(180);
            progressAnim.addUpdateListener(a -> {
                progressBar.getLayoutParams().width = (int) a.getAnimatedValue();
                progressBar.requestLayout();
            });
        }
        progressAnim.start();
    }

    // ------------------------------------------------------------- modo desktop

    private String desktopUserAgent() {
        String def = WebSettings.getDefaultUserAgent(this);
        String chrome = "Chrome/126.0.0.0";
        Matcher m = Pattern.compile("Chrome/[0-9.]+").matcher(def);
        if (m.find()) chrome = m.group();
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + chrome + " Safari/537.36";
    }

    private void applyDesktopMode(boolean on, boolean reload) {
        desktopMode = on;
        WebSettings s = webView.getSettings();
        if (on) {
            s.setUserAgentString(desktopUserAgent());
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(false);
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
        } else {
            s.setUserAgentString(null);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
        }
        prefs.edit().putBoolean(K_DESKTOP, on).apply();
        if (reload) {
            webView.reload();
        }
    }

    // -------------------------------------------------------------------- painel

    private void setupPanel() {
        switchDesktop.setOnChangeListener(checked -> {
            applyDesktopMode(checked, true);
            toast(checked ? getString(R.string.desktop_on_toast) : getString(R.string.desktop_off_toast));
        });

        btnClear.setOnClickListener(v -> clearEverything());

        btnHome.setOnClickListener(v -> {
            closePanel();
            if (!HOME_URL.equals(webView.getUrl())) {
                webView.loadUrl(HOME_URL);
            } else {
                webView.reload();
            }
            fab.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        });

        btnClose.setOnClickListener(v -> closePanel());
        btnReload.setOnClickListener(v -> {
            webView.reload();
            closePanel();
        });
        scrim.setOnClickListener(v -> closePanel());
    }

    private void openPanel() {
        if (panelOpen) {
            closePanel();
            return;
        }
        panelOpen = true;
        switchDesktop.setChecked(desktopMode, false, false);
        panel.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);

        panel.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(Ui.dp(this, 26));
        panel.animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(220).start();
        fab.setOpen(true);
    }

    private void closePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        panel.animate()
                .alpha(0f).scaleX(0.94f).scaleY(0.94f).translationY(Ui.dp(this, 18))
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> panel.setVisibility(View.GONE))
                .start();
        scrim.animate().alpha(0f).setDuration(190)
                .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        fab.setOpen(false);
    }

    // ------------------------------------------------------------------ limpeza

    private void clearEverything() {
        if (btnClear.isBusy()) return;
        btnClear.setBusy(true);
        btnClear.setLabelText(getString(R.string.clearing));

        try {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            WebStorage.getInstance().deleteAllData();
            WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword();
            deleteTree(getCacheDir());
            deleteTree(getExternalCacheDir());
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                deleteTree(getCodeCacheDir());
            }
            webView.clearSslPreferences();
        } catch (Exception ignored) {
        }

        handler.postDelayed(() -> {
            btnClear.setDone();
            btnClear.setLabelText(getString(R.string.cache_cleared));
            toast(getString(R.string.cache_cleared_toast));
            handler.postDelayed(() -> {
                btnClear.reset(getString(R.string.clear_cache));
                webView.reload();
            }, 1400);
        }, 950);
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ---------------------------------------------------------------------- FAB

    private void setupFab() {
        OrbeFab.Listener listener = new OrbeFab.Listener() {
            @Override
            public void onTap() {
                if (panelOpen) closePanel();
                else openPanel();
            }

            @Override
            public void onLongPressAction() {
                closePanel();
                webView.loadUrl(HOME_URL);
                toast("Voltando ao Google…");
            }

            @Override
            public void onMoved() {
                prefs.edit()
                        .putFloat(K_FAB_X, fab.getX())
                        .putFloat(K_FAB_Y, fab.getY())
                        .apply();
            }
        };
        fab.setListener(listener);
        fab.restorePosition(prefs.getFloat(K_FAB_X, -1f), prefs.getFloat(K_FAB_Y, -1f));
    }

    // ------------------------------------------------------------------- extras

    private String urlFromIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String s = intent.getData().toString();
            if (s.startsWith("http")) return s;
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = urlFromIntent(intent);
        if (url != null) {
            webView.loadUrl(url);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) {
            closePanel();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
