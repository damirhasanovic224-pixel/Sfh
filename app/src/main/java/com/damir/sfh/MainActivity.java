package com.damir.sfh;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String BASE = "https://" + HOST;
    static final int PICK = 1;
    WebView web;
    int pending = 0;
    boolean inGame = false;

    File game(int n) { return new File(getFilesDir(), "game" + n + ".swf"); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String p = u.getPath();
                try {
                    if (p.startsWith("/swf/")) {
                        File f = new File(getFilesDir(), new File(p).getName());
                        return resp("application/x-shockwave-flash", new FileInputStream(f));
                    }
                    if (p.startsWith("/assets/")) {
                        String a = p.substring(8);
                        return resp(mime(a), getAssets().open(a));
                    }
                } catch (IOException e) { }
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                        new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }
        });
        web.loadUrl(BASE + "/assets/index.html");
    }

    WebResourceResponse resp(String mime, InputStream in) {
        String enc = (mime.startsWith("text") || mime.contains("javascript") || mime.contains("json")) ? "utf-8" : null;
        WebResourceResponse r = new WebResourceResponse(mime, enc, in);
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        r.setResponseHeaders(h);
        return r;
    }

    String mime(String n) {
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".css")) return "text/css";
        return "application/octet-stream";
    }

    class Bridge {
        @JavascriptInterface public boolean has(int n) { return game(n).exists(); }
        @JavascriptInterface public void choose(int n) { runOnUiThread(() -> pick(n)); }
        @JavascriptInterface public void play(int n) { runOnUiThread(() -> start(n)); }
    }

    void pick(int n) {
        pending = n;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Choose .swf for game " + n), PICK);
    }

    void start(int n) {
        inGame = true;
        web.loadUrl(BASE + "/assets/player.html?g=" + n);
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req != PICK || res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        final int n = pending;
        new Thread(() -> {
            File tmp = new File(getFilesDir(), "tmp.swf");
            try (InputStream in = getContentResolver().openInputStream(u);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int c;
                while ((c = in.read(buf)) > 0) out.write(buf, 0, c);
            } catch (Exception e) { }
            boolean ok = false;
            try (InputStream in = new FileInputStream(tmp)) {
                byte[] h = new byte[3];
                ok = in.read(h) == 3 && h[1] == 'W' && h[2] == 'S'
                        && (h[0] == 'F' || h[0] == 'C' || h[0] == 'Z');
            } catch (Exception e) { }
            final boolean good = ok;
            if (good) { game(n).delete(); tmp.renameTo(game(n)); } else tmp.delete();
            runOnUiThread(() -> {
                if (good) start(n);
                else Toast.makeText(this, "That is not a valid .swf file", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    @Override public void onBackPressed() {
        if (inGame) {
            inGame = false;
            web.loadUrl(BASE + "/assets/index.html");
        } else super.onBackPressed();
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
