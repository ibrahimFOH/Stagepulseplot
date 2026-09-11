package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity {
  private MixerUdpService udp;
  private LinearLayout channelList;
  private TextView status;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    buildUi();
    udp = new MixerUdpService();
    udp.setListener(new MixerUdpService.Listener() {
      @Override public void onPacket(String address, Object[] args) {
        runOnUiThread(() -> handleFeedback(address, args));
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
    root.setPadding(dp(8), dp(8), dp(8), dp(8));

    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    EditText host = new EditText(this);
    host.setId(1001);
    host.setSingleLine(true);
    host.setHint("X32 / M32 IP");
    top.addView(host, new LinearLayout.LayoutParams(0, dp(48), 1));
    Button connect = new Button(this);
    connect.setText("BAĞLAN");
    top.addView(connect, new LinearLayout.LayoutParams(dp(120), dp(48)));
    status = new TextView(this);
    status.setText("Bağlantı yok");
    status.setPadding(dp(8), 0, 0, 0);
    top.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
    root.addView(top);

    HorizontalScrollView scroll = new HorizontalScrollView(this);
    channelList = new LinearLayout(this);
    channelList.setOrientation(LinearLayout.HORIZONTAL);
    scroll.addView(channelList);
    root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    setContentView(root);

    for (int i = 1; i <= 32; i++) addChannel(i);

    connect.setOnClickListener(v -> {
      String h = host.getText().toString().trim();
      if (h.isEmpty()) { status.setText("X32/M32 IP gerekli"); return; }
      getSharedPreferences("stagepulsemix", MODE_PRIVATE).edit().putString("host", h).apply();
      udp.connect(h, 10023, 0);
    });
    String saved = getSharedPreferences("stagepulsemix", MODE_PRIVATE).getString("host", "");
    host.setText(saved);
  }

  private void addChannel(int ch) {
    LinearLayout strip = new LinearLayout(this);
    strip.setOrientation(LinearLayout.VERTICAL);
    strip.setPadding(dp(5), dp(5), dp(5), dp(5));
    strip.setLayoutParams(new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.MATCH_PARENT));

    TextView label = new TextView(this);
    label.setText(String.format(Locale.US, "CH %02d", ch));
    label.setGravity(17);
    strip.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

    TextView meter = new TextView(this);
    meter.setText("-∞");
    meter.setGravity(17);
    meter.setTag("meter");
    strip.addView(meter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

    TextView value = new TextView(this);
    value.setText("0.0 dB");
    value.setGravity(17);
    strip.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

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
        float normalized = bar.getProgress() / 1000f;
        send("/ch/" + String.format(Locale.US, "%02d", ch) + "/fdr", normalized);
      }
    });
    strip.addView(fader, new LinearLayout.LayoutParams(dp(88), dp(220)));

    Button mute = new Button(this);
    mute.setText("M");
    mute.setOnClickListener(v -> {
      boolean muted = !Boolean.TRUE.equals(mute.getTag());
      mute.setTag(muted);
      mute.setText(muted ? "MUTE" : "M");
      send("/ch/" + String.format(Locale.US, "%02d", ch) + "/mix/on", muted ? 0 : 1);
    });
    strip.addView(mute, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

    Button solo = new Button(this);
    solo.setText("S");
    solo.setOnClickListener(v -> {
      boolean on = !Boolean.TRUE.equals(solo.getTag());
      solo.setTag(on);
      solo.setText(on ? "SOLO" : "S");
      send("/ch/" + String.format(Locale.US, "%02d", ch) + "/mix/solo", on ? 1 : 0);
    });
    strip.addView(solo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

    channelList.addView(strip);
  }

  private void handleFeedback(String address, Object[] args) {
    status.setText("RX " + address);
    if (address.matches("/ch/[0-9]{2}/fdr") && args.length > 0 && args[0] instanceof Number) {
      int ch = Integer.parseInt(address.substring(4, 6));
      if (ch >= 1 && ch <= channelList.getChildCount()) {
        LinearLayout strip = (LinearLayout) channelList.getChildAt(ch - 1);
        SeekBar fader = findSeekBar(strip);
        if (fader != null) fader.setProgress(Math.max(0, Math.min(1000, Math.round(((Number)args[0]).floatValue() * 1000))));
      }
    }
  }

  private SeekBar findSeekBar(ViewGroup root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      if (root.getChildAt(i) instanceof SeekBar) return (SeekBar) root.getChildAt(i);
    }
    return null;
  }

  private void refreshSurface() {
    send("/xremote");
    send("/info");
    send("/status");
    send("/config");
    send("/-stat/chfaderbank");
    send("/-stat/grpfaderbank");
    send("/-stat/sendsonfader");
    send("/meters", "/meters/6", 1);
    send("/meters", "/meters/7", 1);
    send("/meters", "/meters/12", 1);
  }

  private void send(String address, Object... args) {
    try { udp.send(address, args); }
    catch (Exception e) { status.setText("TX hata: " + e.getMessage()); }
  }

  private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override protected void onDestroy() {
    if (udp != null) udp.shutdown();
    super.onDestroy();
  }
}
