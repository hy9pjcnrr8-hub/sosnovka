package ru.ugolnyyar.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#0d1220"));
        w.setNavigationBarColor(Color.parseColor("#0d1220"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d1220"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Выйти из игры?")
            .setMessage("Прогресс текущей партии не сохранится.")
            .setPositiveButton("Выйти", (d, i) -> finish())
            .setNegativeButton("Остаться", null)
            .show();
    }
}
