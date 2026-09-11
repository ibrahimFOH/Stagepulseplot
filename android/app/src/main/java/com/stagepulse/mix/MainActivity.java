package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity {
  private MixerUdpService udp;
  private LinearLayout channelList;
  private TextView status;
  private int bank = 0;
  private int sendsBus = 1;

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
    root.setPadding(dp(6), dp(6), dp(6), dp(6));

    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    EditText host = new EditText(this);
    host.setId(1001);
    host.setSingleLine(true);
    host.setHint("X32 / M32 IP");
    top.addView(host, new LinearLayout.LayoutParams(0, dp(46), 1));

    Button connect = new Button(this);
    connect.setText("BAĞLAN");
    top.addView(connect, new LinearLayout.LayoutParams(dp(108), dp(46)));
    status = new TextView(this);
    status.setText("Bağlantı yok");
    status.setGravity(Gravity.CENTER_VERTICAL);
    top.addView(status, new LinearLayout.LayoutParams(dp(150), dp(46)));
    root.addView(top);

    LinearLayout nav = new LinearLayout(this);
    Button prev = button("‹");
    Button next = button("›");
    Button ch = button("INPUTS");
    Button bus = button("BUS");
    Button dca = button("DCA");
    Button main = button("MAIN");
    Button sof = button("SENDS");
    nav.addView(prev); nav.addView(next); nav.addView(ch); nav.addView(bus); nav.addView(dca); nav.addView(main); nav.addView(sof);
    root.addView(nav);

    HorizontalScrollView scroll = new HorizontalScrollView(this);
    channelList = new LinearLayout(this);
    channelList.setOrientation(LinearLayout.HORIZONTAL);
    scroll.addView(channelList);
    root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    setContentView(root);

    buildBank(0);

    prev.setOnClickListener(v -> { bank = Math.max(0, bank - 8); buildBank(bank); });
    next.setOnClickListener(v -> { bank = Math.min(24, bank + 8); buildBank(bank); });
    ch.setOnClickListener(v -> { buildBank(bank); });
    bus.setOnClickListener(v -> { buildBusBank(); });
    dca.setOnClickListener(v -> { buildDcaBank(); });
    main.setOnClickListener(v -> { buildMainBank(); });
    sof.setOnClickListener(v -> { buildSendOnFader(); });
    connect.setOnClickListener(v -> connectToMixer(host));
    host.setText(getSharedPreferences("stagepulsemix", MODE_PRIVATE).getString("host", ""));
  }

  private void buildBank(int start) {
    channelList.removeAllViews();
    for (int i = 0; i < 8 && start + i < 32; i++) addInputStrip(start + i + 1);
    status.setText("INPUTS " + (start + 1) + "-" + Math.min(32, start + 8));
  }

  private void addInputStrip(int input) {
    LinearLayout strip = baseStrip("CH " + String.format(Locale.US, "%02d", input));
    TextView meter = text("-∞");
    meter.setTag("meter");
    strip.addView(meter);
    TextView value = text("-∞ dB");
    value.setTag("faderText");
    strip.addView(value);
    SeekBar fader = fader();
    fader.setTag("fader:" + input);
    fader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
        if (fromUser) value.setText(String.format(Locale.US, "%.1f dB", faderDb(p)));
      }
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) { send(chAddr(input, "fdr"), bar.getProgress() / 1000f); }
    });
    strip.addView(fader, new LinearLayout.LayoutParams(dp(78), dp(250)));
    Button mute = button("M");
    mute.setTag("mute:" + input);
    mute.setOnClickListener(v -> toggle(mute, chAddr(input, "mix/on"), true));
    strip.addView(mute);
    Button solo = button("S");
    solo.setTag("solo:" + input);
    solo.setOnClickListener(v -> toggle(solo, chAddr(input, "mix/solo"), false));
    strip.addView(solo);
    Button pan = button("PAN");
    pan.setOnClickListener(v -> send(chAddr(input, "pan"), 0.5f));
    strip.addView(pan);
    Button detail = button("EDIT");
    detail.setOnClickListener(v -> showInputTools(input));
    strip.addView(detail);
    channelList.addView(strip);
  }

  private void buildBusBank() {
    channelList.removeAllViews();
    for (int i = 1; i <= 8; i++) addBusStrip(i + (bank / 8) * 8);
    status.setText("BUS 1-16");
  }

  private void addBusStrip(int bus) {
    LinearLayout strip = baseStrip("BUS " + String.format(Locale.US, "%02d", bus));
    SeekBar f = fader();
    f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar b, int p, boolean u) {}
      public void onStartTrackingTouch(SeekBar b) {}
      public void onStopTrackingTouch(SeekBar b) { send("/bus/" + pad(bus) + "/fdr", b.getProgress()/1000f); }
    });
    strip.addView(f, new LinearLayout.LayoutParams(dp(78), dp(250)));
    Button mute = button("M");
    mute.setOnClickListener(v -> toggle(mute, "/bus/" + pad(bus) + "/mix/on", true));
    strip.addView(mute);
    channelList.addView(strip);
  }

  private void buildDcaBank() {
    channelList.removeAllViews();
    for (int i = 1; i <= 8; i++) {
      LinearLayout strip = baseStrip("DCA " + i);
      SeekBar f = fader();
      f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar b, int p, boolean u) {}
        public void onStartTrackingTouch(SeekBar b) {}
        public void onStopTrackingTouch(SeekBar b) { send("/dca/" + pad(i) + "/fdr", b.getProgress()/1000f); }
      });
      strip.addView(f, new LinearLayout.LayoutParams(dp(78), dp(250)));
      Button mute = button("M");
      mute.setOnClickListener(v -> toggle(mute, "/dca/" + pad(i) + "/on", true));
      strip.addView(mute);
      channelList.addView(strip);
    }
    status.setText("DCA 1-8");
  }

  private void buildMainBank() {
    channelList.removeAllViews();
    addMainStrip("MAIN", "/main/st/m");
    addMainStrip("MONO", "/main/m/m");
    for (int i = 1; i <= 6; i++) addMainStrip("MATRIX " + i, "/mtx/" + pad(i) + "/fdr");
    status.setText("MAIN / MATRIX");
  }

  private void addMainStrip(String name, String address) {
    LinearLayout strip = baseStrip(name);
    SeekBar f = fader();
    f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar b, int p, boolean u) {}
      public void onStartTrackingTouch(SeekBar b) {}
      public void onStopTrackingTouch(SeekBar b) { send(address, b.getProgress()/1000f); }
    });
    strip.addView(f, new LinearLayout.LayoutParams(dp(78), dp(250)));
    channelList.addView(strip);
  }

  private void buildSendOnFader() {
    channelList.removeAllViews();
    LinearLayout header = baseStrip("BUS " + sendsBus);
    TextView t = text("SENDS-ON-FADER");
    header.addView(t);
    channelList.addView(header);
    for (int i = 1; i <= 8; i++) {
      final int input = i + bank;
      if (input > 32) break;
      LinearLayout strip = baseStrip("CH " + pad(input));
      SeekBar f = fader();
      f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar b, int p, boolean u) {}
        public void onStartTrackingTouch(SeekBar b) {}
        public void onStopTrackingTouch(SeekBar b) { send("/ch/" + pad(input) + "/mix/" + pad(sendsBus) + "/send", b.getProgress()/1000f); }
      });
      strip.addView(f, new LinearLayout.LayoutParams(dp(78), dp(250)));
      channelList.addView(strip);
    }
    status.setText("SENDS ON FADER: BUS " + sendsBus);
  }

  private void showInputTools(int input) {
    channelList.removeAllViews();
    LinearLayout strip = baseStrip("CH " + pad(input));
    addEdit(strip, "GAIN -", chAddr(input, "preamp/trim"));
    addEdit(strip, "HPF", chAddr(input, "eq/lo/f"));
    addEdit(strip, "EQ1 F", chAddr(input, "eq/1/f"));
    addEdit(strip, "EQ1 G", chAddr(input, "eq/1/g"));
    addEdit(strip, "EQ2 F", chAddr(input, "eq/2/f"));
    addEdit(strip, "EQ2 G", chAddr(input, "eq/2/g"));
    addEdit(strip, "EQ3 F", chAddr(input, "eq/3/f"));
    addEdit(strip, "EQ3 G", chAddr(input, "eq/3/g"));
    addEdit(strip, "EQ4 F", chAddr(input, "eq/4/f"));
    addEdit(strip, "EQ4 G", chAddr(input, "eq/4/g"));
    channelList.addView(strip);
    status.setText("CH " + pad(input) + " EDIT");
  }

  private void addEdit(LinearLayout parent, String title, String address) {
    Button b = button(title);
    b.setOnClickListener(v -> send(address, 0.5f));
    parent.addView(b);
  }

  private void connectToMixer(EditText host) {
    String h = host.getText().toString().trim();
    if (h.isEmpty()) { status.setText("X32/M32 IP gerekli"); return; }
    getSharedPreferences("stagepulsemix", MODE_PRIVATE).edit().putString("host", h).apply();
    udp.connect(h, 10023, 0);
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

  private void handleFeedback(String address, Object[] args) {
    if (address.matches("/ch/[0-9]{2}/fdr") && args.length > 0 && args[0] instanceof Number) {
      int input = Integer.parseInt(address.substring(4, 6));
      View view = channelList.findViewWithTag("fader:" + input);
      if (view instanceof SeekBar) ((SeekBar)view).setProgress(Math.max(0, Math.min(1000, Math.round(((Number)args[0]).floatValue()*1000))));
      status.setText("RX " + address);
      return;
    }
    if (address.startsWith("/info") || address.startsWith("/status")) status.setText("Bağlı: " + address);
  }

  private void toggle(Button b, String address, boolean activeHigh) {
    boolean on = !Boolean.TRUE.equals(b.getTag());
    b.setTag(on);
    b.setText(on ? "●" : "M");
    send(address, activeHigh ? (on ? 0 : 1) : (on ? 1 : 0));
  }

  private LinearLayout baseStrip(String name) {
    LinearLayout strip = new LinearLayout(this);
    strip.setOrientation(LinearLayout.VERTICAL);
    strip.setGravity(Gravity.CENTER_HORIZONTAL);
    strip.setPadding(dp(5), dp(5), dp(5), dp(5));
    strip.setLayoutParams(new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.MATCH_PARENT));
    TextView label = text(name);
    strip.addView(label);
    return strip;
  }

  private SeekBar fader() {
    SeekBar f = new SeekBar(this);
    f.setMax(1000);
    f.setProgress(900);
    f.setRotation(-90f);
    return f;
  }

  private TextView text(String s) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setGravity(Gravity.CENTER);
    t.setPadding(2,2,2,2);
    return t;
  }

  private Button button(String s) {
    Button b = new Button(this);
    b.setText(s);
    return b;
  }

  private void send(String address, Object... args) {
    try { udp.send(address, args); }
    catch (Exception e) { status.setText("TX hata: " + e.getMessage()); }
  }

  private String chAddr(int ch, String suffix) { return "/ch/" + pad(ch) + "/" + suffix; }
  private String pad(int n) { return String.format(Locale.US, "%02d", n); }
  private float faderDb(int p) { return -90f + p * 0.1f; }
  private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override protected void onDestroy() {
    if (udp != null) udp.shutdown();
    super.onDestroy();
  }
}
