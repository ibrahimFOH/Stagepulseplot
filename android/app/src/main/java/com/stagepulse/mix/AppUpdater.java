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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

public final class AppUpdater {
  private static final String RELEASES_URL = "https://api.github.com/repos/ibrahimFOH/StagepulseMix/releases/latest";
  private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
  private final Activity activity;

  public AppUpdater(Activity activity) { this.activity = activity; }

  public void check(boolean notifyWhenCurrent) {
    new AsyncTask<Boolean, Void, UpdateInfo>() {
      @Override protected UpdateInfo doInBackground(Boolean... ignored) {
        try {
          PackageInfo pkg = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
          String current = pkg.versionName == null ? "0.0.0" : pkg.versionName;
          HttpURLConnection c = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
          c.setConnectTimeout(7000); c.setReadTimeout(7000);
          c.setRequestProperty("Accept", "application/vnd.github+json");
          c.setRequestProperty("User-Agent", "StagePulseMix/" + current);
          if (c.getResponseCode() != 200) throw new IllegalStateException("GitHub HTTP " + c.getResponseCode());
          byte[] raw = readAll(c.getInputStream());
          c.disconnect();
          JSONObject release = new JSONObject(new String(raw, "UTF-8"));
          String version = release.optString("tag_name", "").replaceFirst("^v", "");
          if (!VERSION.matcher(version).matches()) return null;
          if (compare(version, current) <= 0) {
            if (notifyWhenCurrent) Toast.makeText(activity, "StagePulseMix güncel", Toast.LENGTH_SHORT).show();
            return null;
          }
          JSONArray assets = release.optJSONArray("assets");
          String apkUrl = null;
          if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
              JSONObject asset = assets.optJSONObject(i);
              if (asset != null && asset.optString("name", "").endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url", null); break;
              }
            }
          }
          if (apkUrl == null || apkUrl.isEmpty()) return null;
          return new UpdateInfo(version, apkUrl);
        } catch (Exception ignored) { return null; }
      }
      @Override protected void onPostExecute(UpdateInfo info) {
        if (info != null) {
          Toast.makeText(activity, "Yeni sürüm bulundu: " + info.version, Toast.LENGTH_LONG).show();
          new DownloadTask().execute(info.apkUrl);
        }
      }
    }.execute(notifyWhenCurrent);
  }

  private final class DownloadTask extends AsyncTask<String, Void, File> {
    @Override protected File doInBackground(String... urls) {
      File base = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
      File part = new File(base, "stagepulsemix-update.apk.part");
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(urls[0]).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "StagePulseMix-Updater");
        if (c.getResponseCode() != 200) throw new IllegalStateException();
        try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream out = new FileOutputStream(part, false)) {
          byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        c.disconnect();
        File apk = new File(base, "stagepulsemix-update.apk");
        if (apk.exists()) apk.delete();
        if (!part.renameTo(apk)) throw new IllegalStateException();
        return apk;
      } catch (Exception e) { if (part.exists()) part.delete(); return null; }
    }
    @Override protected void onPostExecute(File apk) {
      if (apk == null) { Toast.makeText(activity, "APK indirilemedi", Toast.LENGTH_SHORT).show(); return; }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
        Toast.makeText(activity, "APK kurulumu için izin ver", Toast.LENGTH_LONG).show(); return;
      }
      try {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent install = new Intent(Intent.ACTION_VIEW, uri);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(install);
      } catch (Exception e) { Toast.makeText(activity, "Kurulum başlatılamadı", Toast.LENGTH_SHORT).show(); }
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
    while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray();
  }
  private static int compare(String a, String b) { int[] x=parse(a),y=parse(b); for(int i=0;i<3;i++) if(x[i]!=y[i]) return Integer.compare(x[i],y[i]); return 0; }
  private static int[] parse(String v) { String[] p=v.replaceFirst("^v","").split("\\."); return new int[]{Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])}; }
  private static final class UpdateInfo { final String version, apkUrl; UpdateInfo(String v,String u){version=v;apkUrl=u;} }
}
