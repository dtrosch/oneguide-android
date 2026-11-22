package com.oneguide.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OneGuide for Fire TV: a full-screen WebView showing the hosted OneGuide page,
 * plus a small bridge so the page can open the streaming apps installed on the device.
 * Nothing here ever sees a username or password; those stay inside each streaming app.
 */
public class MainActivity extends Activity {

    // The hosted OneGuide page. Redeploying to Netlify updates the app without rebuilding it.
    static final String START_URL = "https://harmonious-mandazi-4b6b35.netlify.app";

    // App names to look for, in order of preference. Matching is by the name shown on the Fire TV home screen.
    static final Map<String, String[]> APP_NAMES = new HashMap<>();
    static {
        APP_NAMES.put("netflix", new String[]{"netflix"});
        APP_NAMES.put("hulu", new String[]{"hulu"});
        APP_NAMES.put("max", new String[]{"max", "hbo max"});
        APP_NAMES.put("disney", new String[]{"disney+", "disney plus"});
        APP_NAMES.put("paramount", new String[]{"paramount+", "paramount plus"});
        APP_NAMES.put("peacock", new String[]{"peacock"});
        APP_NAMES.put("espnplus", new String[]{"espn"});
        APP_NAMES.put("espn", new String[]{"espn"});
        APP_NAMES.put("appletv", new String[]{"apple tv+", "apple tv"});
        APP_NAMES.put("prime", new String[]{"prime video", "amazon prime video"});
        APP_NAMES.put("cable", new String[]{"spectrum tv", "spectrum", "cox contour", "contour", "cox"});
    }

    private WebView web;
    private long lastBack = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#080B12"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "OneGuideNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false; // stay inside the app
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    showOffline();
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.startsWith(START_URL)) {
                    showOffline();
                }
            }
        });

        setContentView(web);
        web.requestFocus();
        web.loadUrl(START_URL);
    }

    private void showOffline() {
        String html = "<html><body style='background:#080B12;color:#dfe6f5;font-family:sans-serif;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>"
                + "<div><div style='font-size:42px;font-weight:700'>OneGuide can't reach the internet</div>"
                + "<div style='font-size:26px;margin-top:20px;color:#9aa7c4'>Trying again in a few seconds...</div></div>"
                + "<script>setTimeout(function(){location.href='" + START_URL + "';},6000);</script>"
                + "</body></html>";
        web.loadDataWithBaseURL(START_URL, html, "text/html", "utf-8", null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Ask the page first. true = it closed something or moved focus; false = nothing left to do.
            web.evaluateJavascript(
                    "(function(){try{return window.OneGuideBack ? !!window.OneGuideBack() : false;}catch(e){return false;}})()",
                    value -> {
                        if (!"true".equals(value)) {
                            long now = System.currentTimeMillis();
                            if (now - lastBack < 2500) {
                                finish();
                            } else {
                                lastBack = now;
                                Toast.makeText(MainActivity.this, "Press Back again to exit OneGuide", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    // ---- finding and opening other apps ----

    /** Every app that shows up on the Fire TV home screen: lowercase name -> how to open it. */
    private Map<String, ComponentName> launchableApps() {
        Map<String, ComponentName> found = new HashMap<>();
        PackageManager pm = getPackageManager();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                ActivityInfo ai = ri.activityInfo;
                CharSequence label = ri.loadLabel(pm);
                if (label == null) continue;
                String key = label.toString().trim().toLowerCase(Locale.US);
                if (!found.containsKey(key)) {
                    found.put(key, new ComponentName(ai.packageName, ai.name));
                }
            }
        }
        return found;
    }

    /** Best match for a service id, or null. Returns {label, component} through the two out-parameters. */
    private String[] match(String serviceId, Map<String, ComponentName> apps, ComponentName[] out) {
        String[] wanted = APP_NAMES.get(serviceId);
        if (wanted == null) return null;
        for (String name : wanted) {
            if (apps.containsKey(name)) { out[0] = apps.get(name); return new String[]{name}; }
        }
        for (String name : wanted) {
            for (Map.Entry<String, ComponentName> e : apps.entrySet()) {
                if (e.getKey().startsWith(name + " ") || e.getKey().startsWith(name)) {
                    out[0] = e.getValue();
                    return new String[]{e.getKey()};
                }
            }
        }
        return null;
    }

    private static String pretty(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private class Bridge {
        @JavascriptInterface
        public boolean isNative() {
            return true;
        }

        /** JSON like {"netflix":"netflix","hulu":"hulu"} for every service whose app is installed. */
        @JavascriptInterface
        public String detect() {
            Map<String, ComponentName> apps = launchableApps();
            List<String> parts = new ArrayList<>();
            for (String id : APP_NAMES.keySet()) {
                String[] m = match(id, apps, new ComponentName[1]);
                if (m != null) {
                    parts.add("\"" + id + "\":\"" + m[0].replace("\"", "").replace("\\", "") + "\"");
                }
            }
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(parts.get(i));
            }
            return sb.append("}").toString();
        }

        /** Opens the app for a service. Returns a short message for the page to show. */
        @JavascriptInterface
        public String launch(String serviceId, String title) {
            Map<String, ComponentName> apps = launchableApps();
            ComponentName[] cn = new ComponentName[1];
            String[] m = match(serviceId, apps, cn);
            if (m == null || cn[0] == null) {
                return "That app isn't installed on this Fire Cube yet. Install it from the Appstore, then try again.";
            }
            final Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setComponent(cn[0])
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            runOnUiThread(() -> {
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open the app.", Toast.LENGTH_SHORT).show();
                }
            });
            return "Opening " + pretty(m[0]) + "...";
        }
    }
}
