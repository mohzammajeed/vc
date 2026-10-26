package com.softvcompressor.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> createDocLauncher;

    // Staging of the compressed video sent from JavaScript
    private File stagedFile;
    private FileOutputStream stagedOut;
    private String stagedName = "Compressed_video.mp4";
    private String stagedMime = "video/mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // <input type="file"> only works in a WebView if the app handles the chooser
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (filePathCallback == null) return;
                    Uri[] uris = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        uris = WebChromeClient.FileChooserParams.parseResult(
                                result.getResultCode(), result.getData());
                    }
                    filePathCallback.onReceiveValue(uris);
                    filePathCallback = null;
                });

        createDocLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        copyStagedTo(result.getData().getData());
                    }
                });

        webView = findViewById(R.id.webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setAllowFileAccess(true);                    // needed for file:///android_asset
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMediaPlaybackRequiresUserGesture(false);  // compression plays the video programmatically
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    fileChooserLauncher.launch(params.createIntent());
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onDestroy() {
        closeStage();
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
        }
        super.onDestroy();
    }

    private void closeStage() {
        try { if (stagedOut != null) stagedOut.close(); } catch (IOException ignored) {}
        stagedOut = null;
    }

    private void copyStagedTo(Uri target) {
        new Thread(() -> {
            boolean ok = false;
            try (InputStream in = new java.io.FileInputStream(stagedFile);
                 OutputStream out = getContentResolver().openOutputStream(target, "w")) {
                if (out == null) throw new IOException("No output stream");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                ok = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            final boolean success = ok;
            runOnUiThread(() -> Toast.makeText(this,
                    success ? "Video saved" : "Could not save the video",
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    /** Called from JavaScript as window.Android.* (runs on a background thread). */
    private class Bridge {
        @JavascriptInterface
        public void begin(String name, String mime) {
            try {
                closeStage();
                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                stagedName = (name == null || name.isEmpty()) ? "Compressed_video.mp4"
                        : name.replaceAll("[\\\\/:*?\"<>|]", "_");
                stagedMime = (mime == null || mime.isEmpty()) ? "video/mp4" : mime;
                stagedFile = new File(dir, stagedName);
                stagedOut = new FileOutputStream(stagedFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void chunk(String base64) {
            try {
                if (stagedOut != null) stagedOut.write(Base64.decode(base64, Base64.DEFAULT));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void end() {
            closeStage();
        }

        @JavascriptInterface
        public void save() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(stagedMime);
                i.putExtra(Intent.EXTRA_TITLE, stagedName);
                try {
                    createDocLauncher.launch(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Cannot open the save dialog", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void share() {
            runOnUiThread(() -> {
                try {
                    Uri uri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".fileprovider", stagedFile);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(stagedMime);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.setClipData(ClipData.newRawUri("video", uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share video"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot share the video", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }
}
