package pl.knieja.punkty;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Kniejapunkty — aplikacja otwierająca https://mikoorty.github.io/Knieja-punkty/ w pełnym oknie.
 * Cała logika jest na stronie; tutaj tylko: okno, aparat/galeria, zapis plików, drukowanie.
 */
public class MainActivity extends Activity {
    static final String START = "https://mikoorty.github.io/Knieja-punkty/";
    static final String HOST = "mikoorty.github.io";
    static final int REQ_FILE = 1;
    static final int REQ_CAMERA_PERMISSION = 2;

    private WebView web;
    private WebView printView;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private PermissionRequest pendingPermissionRequest;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " KniejaApp/1");
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Bridge(), "KniejaApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost()) || "panbadek.github.io".equals(u.getHost())) return false;          // nasza strona — zostaje w aplikacji
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;                                          // obce linki — w przeglądarce
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsVideo = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        wantsVideo = true;
                        break;
                    }
                }
                if (wantsVideo) {
                    if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermissionRequest = request;
                        requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
                    }
                } else {
                    request.grant(request.getResources());
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                cameraUri = null;
                Intent target;
                if (params.isCaptureEnabled()) {                      // „Zrób zdjęcie” → aparat
                    try {
                        File f = File.createTempFile("zdjecie_", ".jpg", getCacheDir());
                        cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                        target = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        target.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e) {
                        target = galleryIntent();
                    }
                } else {                                              // „Wybierz z galerii”
                    target = galleryIntent();
                }
                try {
                    startActivityForResult(target, REQ_FILE);
                } catch (Exception e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    toast("Nie można otworzyć aparatu ani galerii");
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        else web.loadUrl(START);
    }

    private Intent galleryIntent() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        return Intent.createChooser(pick, "Wybierz zdjęcie");
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request != REQ_FILE || fileCallback == null) { super.onActivityResult(request, result, data); return; }
        Uri[] out = null;
        if (result == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{ data.getData() };
            else if (cameraUri != null) out = new Uri[]{ cameraUri };
        }
        fileCallback.onReceiveValue(out);
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    pendingPermissionRequest = null;
                }
            } else {
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
                toast("Wymagane jest zezwolenie na aparat, aby skanować kody QR.");
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    /** Funkcje dostępne dla strony jako window.KniejaApp */
    class Bridge {
        @JavascriptInterface
        public void saveFile(String base64, String name, String mime) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                    toast("Zapisano w folderze Pobrane: " + name);
                } else {
                    File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                    Uri u = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent share = new Intent(Intent.ACTION_SEND).setType(mime)
                            .putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, name));
                }
            } catch (Exception e) {
                toast("Nie udało się zapisać pliku");
            }
        }

        @JavascriptInterface
        public void toast(String msg) {
            MainActivity.this.toast(msg);
        }

        @JavascriptInterface
        public void printHtml(String html, String title) {
            runOnUiThread(() -> {
                WebView pw = new WebView(MainActivity.this);
                pw.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView v, String url) {
                        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                        pm.print(title, v.createPrintDocumentAdapter(title), new PrintAttributes.Builder().build());
                    }
                });
                pw.loadDataWithBaseURL(START, html, "text/html", "utf-8", null);
                printView = pw;     // trzymamy referencję, żeby nie zniknął w trakcie drukowania
            });
        }
    }
}
