package com.archive.register;

import android.app.Activity;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.view.WindowManager;
import android.content.DialogInterface;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4101;
    private static final int SAVE_FILE_REQUEST = 4102;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSave;
    private String pendingMime = "application/json";
    private String pendingName = "archive-backup.json";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(16,47,53));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(11,48,54));
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new ArchiveBridge(), "ArchiveAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"file".equalsIgnoreCase(uri.getScheme());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel", "text/csv", "application/json", "text/plain"
                });
                try { startActivityForResult(intent, FILE_CHOOSER_REQUEST); }
                catch (ActivityNotFoundException ex) { fileCallback = null; callback.onReceiveValue(null); }
                return true;
            }
            @Override public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("حسناً", (d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("موافق", (d,w)->result.confirm()).setNegativeButton("إلغاء", (d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();
                return true;
            }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
                EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setView(input)
                    .setPositiveButton("موافق", (d,w)->result.confirm(input.getText().toString()))
                    .setNegativeButton("إلغاء", (d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();
                return true;
            }
        });
        if (state == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(state);
    }

    private class ArchiveBridge {
        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> {
                PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
                if (manager != null) manager.print("سجل الأرشيف", webView.createPrintDocumentAdapter("سجل الأرشيف"), new PrintAttributes.Builder().build());
            });
        }
        @JavascriptInterface public void saveTextFile(String name, String content, String mime) {
            runOnUiThread(() -> {
                pendingName = (name == null || name.trim().isEmpty()) ? "archive-backup.json" : name.replaceAll("[/\\\\]", "_");
                pendingMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                pendingSave = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingMime);
                intent.putExtra(Intent.EXTRA_TITLE, pendingName);
                try { startActivityForResult(intent, SAVE_FILE_REQUEST); }
                catch (ActivityNotFoundException ex) { showMessage("تعذر فتح نافذة حفظ الملف."); }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileCallback != null) {
                Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null;
                fileCallback.onReceiveValue(result); fileCallback = null;
            }
        } else if (requestCode == SAVE_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                    if (stream == null) throw new Exception("تعذر فتح الملف للحفظ");
                    stream.write(pendingSave == null ? new byte[0] : pendingSave);
                    showMessage("تم حفظ النسخة الاحتياطية بنجاح.");
                } catch (Exception ex) { showMessage("تعذر حفظ الملف: " + ex.getMessage()); }
            }
            // Release a potentially large backup buffer even when the save dialog is cancelled.
            pendingSave = null;
        }
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسناً", null).show();
    }

    @Override protected void onSaveInstanceState(Bundle outState) { webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else new AlertDialog.Builder(this).setMessage("هل تريد الخروج من سجل الأرشيف؟")
            .setPositiveButton("خروج", (d,w)->finish()).setNegativeButton("إلغاء", null).show();
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        pendingSave = null;
        if (webView != null) { webView.removeJavascriptInterface("ArchiveAndroid"); webView.destroy(); }
        super.onDestroy();
    }
}
