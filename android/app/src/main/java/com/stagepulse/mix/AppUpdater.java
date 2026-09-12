package com.stagepulse.mix;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

/** Simple GitHub Releases updater. Uses public release metadata and APK asset download. */
public final class AppUpdater {
  private static final String RELEASES_URL = "https://api.github.com/repos/ibrahimFOH/StagepulseMix/releases/latest";
  private static final Pattern VERSION = Pattern.compile("^\\d+(?:\\.\\d+){2}$");
  private final Activity activity;

  public AppUpdater(Activity activity) { this.activity = activity; }

  public void check(boolean interactive) {
    new AsyncTask<Boolean, Void, UpdateInfo>() {
      private Exception error;
      @Override protected UpdateInfo doInBackground(Boolean... flags) {
        try {
          PackageInfo pkg = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
          String current = pkg.versionName == null ? "0.0.0" : pkg.versionName;
          HttpURLConnection c = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
          c.setConnectTimeout(6000); c.setReadTimeout(6000);
          c.setRequestProperty("Accept", "application/vnd.github+json");
          c.setRequestProperty("User-Agent", "StagePulseMix-Updater");
          if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
          InputStream in = c.getInputStream();
          byte[] data = readAll(in); in.close(); c.disconnect();
          JSONObject release = new JSONObject(new String(data, "UTF-8"));
          String tag = release.optString("tag_name", "").replaceFirst("^v", "");
          if (!VERSION.matcher(tag).matches() || compare(tag, current) <= 0) return null;
          String url = "";
          for (Object obj : release.optJSONArray("assets")) {
            if (obj instanceof JSONObject) {
              JSONObject a=(JSONObject)obj;
              if (a.optString("name", "").endsWith(".apk")) { url=a.optString("browser_download_url", ""); break; }
            }
          }
          if (url.length()==0) return null;
          return new UpdateInfo(tag, url);
        } catch (Exception e) { error=e; return null; }
      }
      @Override protected void onPostExecute(UpdateInfo info) {
        if (info == null) { if (flags.length>0 && flags[0] && error!=null) Toast.makeText(activity, "Güncelleme kontrolü başarısız", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(activity, "Yeni sürüm bulundu: " + info.version, Toast.LENGTH_LONG).show();
        new DownloadTask().execute(info.url);
      }
    }.execute(interactive);
  }

  private final class DownloadTask extends AsyncTask<String, Void, File> {
    private Exception error;
    @Override protected File doInBackground(String... urls) {
      try {
        HttpURLConnection c=(HttpURLConnection)new URL(urls[0]).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "StagePulseMix-Updater");
        if(c.getResponseCode()!=200) throw new IllegalStateException("APK HTTP " + c.getResponseCode());
        File out=new File(activity.getExternalCacheDir()!=null?activity.getExternalCacheDir():activity.getCacheDir(),"stagepulsemix-update.apk");
        try(InputStream in=new BufferedInputStream(c.getInputStream()); FileOutputStream fos=new FileOutputStream(out,false)){
          byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) fos.write(buf,0,n);
        }
        c.disconnect(); return out;
      } catch(Exception e){error=e;return null;}
    }
    @Override protected void onPostExecute(File file){
      if(file==null){Toast.makeText(activity,"APK indirilemedi",Toast.LENGTH_SHORT).show();return;}
      try{
        Intent i=new Intent(Intent.ACTION_VIEW);
        Uri uri=Uri.fromFile(file);
        i.setDataAndType(uri,"application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
      }catch(Exception e){
        Toast.makeText(activity,"Kurulum başlatılamadı",Toast.LENGTH_SHORT).show();
        Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(file.toURI().toString()));
        activity.startActivity(browser);
      }
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
    byte[] b=new byte[4096]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); return out.toByteArray();
  }
  private static int compare(String a,String b){int[] x=parse(a),y=parse(b);for(int i=0;i<3;i++){if(x[i]!=y[i])return x[i]-y[i];}return 0;}
  private static int[] parse(String s){String[] p=s.replaceFirst("^v","").split("\\.");return new int[]{Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])};}
  private static final class UpdateInfo {final String version,url;UpdateInfo(String v,String u){version=v;url=u;}}
}
