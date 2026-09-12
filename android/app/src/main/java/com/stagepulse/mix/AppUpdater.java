package com.stagepulse.mix;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AppUpdater {
  private static final String UPDATE_MANIFEST_URL = "https://ibrahimfoh.github.io/StagepulseMix/latest.json";
  private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
  private final Activity activity;

  public AppUpdater(Activity activity) { this.activity = activity; }

  public void check(boolean notifyWhenCurrent) {
    new AsyncTask<Boolean, Void, UpdateInfo>() {
      @Override protected UpdateInfo doInBackground(Boolean... flags) {
        try {
          PackageInfo pkg = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
          String current = pkg.versionName == null ? "0.0.0" : pkg.versionName;
          HttpURLConnection c = (HttpURLConnection) new URL(UPDATE_MANIFEST_URL).openConnection();
          c.setConnectTimeout(7000); c.setReadTimeout(7000);
          c.setRequestProperty("Accept", "application/json");
          c.setRequestProperty("User-Agent", "StagePulseMix-Updater/1");
          if (c.getResponseCode() != 200) throw new IllegalStateException("Update manifest HTTP " + c.getResponseCode());
          byte[] raw = readAll(c.getInputStream());
          c.disconnect();

          JSONObject manifest = new JSONObject(new String(raw, "UTF-8"));
          String version = manifest.optString("version", "");
          if (!VERSION.matcher(version).matches() || compare(version, current) <= 0) {
            if (Boolean.TRUE.equals(flags.length > 0 ? flags[0] : Boolean.FALSE)) {
              Toast.makeText(activity, "StagePulseMix güncel", Toast.LENGTH_SHORT).show();
            }
            return null;
          }

          String apkUrl = manifest.optString("apk", "");
          String sha256 = manifest.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
          if (apkUrl.isEmpty() || !sha256.matches("^[0-9a-f]{64}$")) return null;
          return new UpdateInfo(version, apkUrl, sha256);
        } catch (Exception e) {
          return null;
        }
      }

      @Override protected void onPostExecute(UpdateInfo info) {
        if (info != null) {
          Toast.makeText(activity, "Yeni sürüm: " + info.version, Toast.LENGTH_LONG).show();
          new DownloadTask(info.sha256).execute(info.apkUrl);
        }
      }
    }.execute(notifyWhenCurrent);
  }

  private final class DownloadTask extends AsyncTask<String, Void, File> {
    private final String expectedSha256;

    DownloadTask(String expectedSha256) { this.expectedSha256 = expectedSha256; }

    @Override protected File doInBackground(String... urls) {
      File base = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
      File part = new File(base, "stagepulsemix-update.apk.part");
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(urls[0]).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(10000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "StagePulseMix-Updater/1");
        if (c.getResponseCode() != 200) throw new IllegalStateException("APK HTTP " + c.getResponseCode());
        try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream out = new FileOutputStream(part, false)) {
          byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        c.disconnect();

        String actualSha256 = sha256(part);
        if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
          part.delete();
          throw new IllegalStateException("APK SHA256 mismatch");
        }

        File apk = new File(base, "stagepulsemix-update.apk");
        if (apk.exists() && !apk.delete()) throw new IllegalStateException("Old APK delete failed");
        if (!part.renameTo(apk)) throw new IllegalStateException("APK rename failed");
        return apk;
      } catch (Exception e) {
        if (part.exists()) part.delete();
        return null;
      }
    }

    @Override protected void onPostExecute(File apk) {
      if (apk == null) {
        Toast.makeText(activity, "APK indirilemedi veya doğrulama başarısız", Toast.LENGTH_SHORT).show();
        return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
        Toast.makeText(activity, "APK kurulumu için izin ver", Toast.LENGTH_LONG).show();
        return;
      }
      try {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(install);
      } catch (Exception e) {
        Toast.makeText(activity, "Kurulum başlatılamadı", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192]; int n;
      while ((n = input.read(b)) != -1) out.write(b, 0, n);
      return out.toByteArray();
    }
  }

  private static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(file))) {
      byte[] buf = new byte[16384]; int n;
      while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
    }
    StringBuilder out = new StringBuilder(64);
    for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
    return out.toString();
  }

  private static int compare(String a, String b) {
    int[] x=parse(a), y=parse(b);
    for (int i=0;i<3;i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
    return 0;
  }

  private static int[] parse(String v) {
    String[] p=v.replaceFirst("^v","").split("\\.");
    return new int[]{Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])};
  }

  private static final class UpdateInfo {
    final String version, apkUrl, sha256;
    UpdateInfo(String v,String u,String s){version=v;apkUrl=u;sha256=s;}
  }
}
