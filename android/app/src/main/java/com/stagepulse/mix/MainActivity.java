package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import java.util.Locale;

public class MainActivity extends Activity {
  private MixerUdpService udp;
  private SharedPreferences prefs;
  private LinearLayout channelList;
  private TextView status;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    buildUi();
    prefs = getSharedPreferences("stagepulsemix", MODE_PRIVATE);
    EditText host = findViewById(1001);
    host.setText(prefs.getString("host", "192.168.1.100"));
    udp = new MixerUdpService();
    udp.setListener(new MixerUdpService.Listener() {
      @Override public void onPacket(String address, Object[] args) {
        runOnUiThread(() -> status.setText("RX " + address));
      }
      @Override public void onError(Exception error) {
        runOnUiThread(() -> status.setText("Hata: " + error.getMessage()));
      }
      @Override public void onConnected() {
        runOnUiThread(() -> status.setText("X32 / M32 bağlı"));
        refreshSurface();
      }
      @Override public void onDisconnected() {
        runOnUiThread(() -> status.setText("Bağlantı kesildi"));
      }
    });
  }

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    LinearLayout top = new LinearLayout(this);
    top.setPadding(8,8,8,8);

    EditText host = new EditText(this);
    host.setId(1001);
    host.setHint("X32 / M32 IP");
    top.addView(host, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    Button connect = new Button(this);
    connect.setText("BAĞLAN");
    top.addView(connect, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    status = new TextView(this);
    status.setText("Bağlantı yok");
    top.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    root.addView(top);

    ScrollView scroll = new ScrollView(this);
    channelList = new LinearLayout(this);
    channelList.setOrientation(LinearLayout.HORIZONTAL);
    scroll.addView(channelList);
    root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    setContentView(root);

    for (int i = 1; i <= 32; i++) addChannel(i);
    connect.setOnClickListener(v -> connectToMixer());
  }

  private void addChannel(int ch) {
    LinearLayout strip = new LinearLayout(this);
    strip.setOrientation(LinearLayout.VERTICAL);
    strip.setPadding(6,6,6,6);
    strip.setLayoutParams(new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.MATCH_PARENT));

    TextView label = new TextView(this);
    label.setText(String.format(Locale.US, "CH %02d", ch));
    strip.addView(label);

    TextView value = new TextView(this);
    value.setText("0.0 dB");
    strip.addView(value);

    SeekBar fader = new SeekBar(this);
    fader.setMax(1000);
    fader.setProgress(900);
    fader.setRotation(-90f);
    fader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (fromUser) value.setText(String.format(Locale.US, "%.1f dB", -90.0 + progress * 0.1));
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {
        float db = (float)(-90.0 + bar.getProgress() * 0.1);
        sendFader(ch, db);
      }
    });
    strip.addView(fader, new LinearLayout.LayoutParams(dp(82), dp(220)));

    Button mute = new Button(this);
    mute.setText("M");
    mute.setOnClickListener(v -> {
      boolean on = !Boolean.TRUE.equals(mute.getTag());
      mute.setTag(on);
      mute.setText(on ? "MUTE" : "M");
      send("/ch/" + String.format(Locale.US, "%02d", ch) + "/mix/on", !on);
    });
    strip.addView(mute);

    Button solo = new Button(this);
    solo.setText("S");
    solo.setOnClickListener(v -> {
      boolean on = !Boolean.TRUE.equals(solo.getTag());
      solo.setTag(on);
      solo.setText(on ? "SOLO" : "S");
      send("/ch/" + String.format(Locale.US, "%02d", ch) + "/mix/solo", on);
    });
    strip.addView(solo);
    channelList.addView(strip);
  }

  private void connectToMixer() {
    EditText host = findViewById(1001);
    String h = host.getText().toString().trim();
    if (h.isEmpty()) { toast("IP gerekli"); return; }
    prefs.edit().putString("host", h).apply();
    udp.connect(h, 10023, 0);
  }

  private void refreshSurface() {
    send("/-stat/chfaderbank");
    send("/-stat/grpfaderbank");
    send("/-stat/sendsonfader");
    send("/config");
    send("/status");
    send("/info");
    send("/meters", "/meters/6", 1);
    send("/meters", "/meters/7", 1);
    send("/meters", "/meters/12", 1);
  }

  private void sendFader(int ch, float db) {
    float normalized = Math.max(0f, Math.min(1f, (db + 90f) / 100f));
    send("/ch/" + String.format(Locale.US, "%02d", ch) + "/fdr", normalized);
  }

  private void send(String address, Object... args) {
    try { udp.send(address, args); }
    catch (Exception e) { status.setText("TX hata: " + e.getMessage()); }
  }

  private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
  private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override protected void onDestroy() {
    if (udp != null) udp.shutdown();
    super.onDestroy();
  }
}
