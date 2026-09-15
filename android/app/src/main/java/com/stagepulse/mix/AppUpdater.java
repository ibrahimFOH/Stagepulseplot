package com.stagepulse.mix;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppUpdater {
    private static final String UPDATE_MANIFEST_URL = "https://stagepulse.com/public-updates/latest.json";
    private static final String EXPECTED_PACKAGE = "com.stagepulse.mix";

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private File pendingApk;

    public AppUpdater(Activity activity) {
        this.activity = activity;
    }

    public void check() {
        executor.execute(() -> {
            try {
                JSONObject manifest = readJson(UPDATE_MANIFEST_URL);
                if (!EXPECTED_PACKAGE.equals(manifest.optString("package"))) return;
                long remoteVersionCode = manifest.optLong("versionCode", -1);
                if (remoteVersionCode <= 0 || remoteVersionCode <= currentVersionCode()) return;
                String apkUrl = manifest.optString("apk", "");
                String sha256 = manifest.optString("sha256", "");
                if (apkUrl.isEmpty() || sha256.isEmpty()) return;
                File apk = downloadApk(apkUrl, sha256, remoteVersionCode);
                activity.runOnUiThread(() -> prepareInstall(apk, manifest.optString("version", "")));
            } catch (Exception ignored) {
                // Update checks are non-critical. The planner must remain usable offline.
            }
        });
    }

    private long currentVersionCode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
        }
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
    }

    private JSONObject readJson(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("Update manifest HTTP " + connection.getResponseCode());
        }
        try (InputStream input = connection.getInputStream()) {
            byte[] data = input.readAllBytes();
            return new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private File downloadApk(String urlString, String expectedSha256, long versionCode) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(60000);
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("APK HTTP " + connection.getResponseCode());
        }
        File file = new File(activity.getCacheDir(), "stagepulse-update-" + versionCode + ".apk");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
        String actual = hex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            file.delete();
            throw new SecurityException("APK SHA-256 mismatch");
        }
        return file;
    }

    private void prepareInstall(File apk, String version) {
        pendingApk = apk;
        Toast.makeText(activity, "StagePulse Plot " + version + " güncellemesi indirildi.", Toast.LENGTH_LONG).show();
        installPending();
    }

    public void installPending() {
        if (pendingApk == null || !pendingApk.isFile()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", pendingApk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) builder.append(String.format("%02x", b));
        return builder.toString();
    }
}
