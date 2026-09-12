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
import android.widget.TextView;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
  private MixerUdpService udp;
  private LinearLayout channelList;
  private TextView status;
  private int bank = 0;
  private int sendsBus = 1;
  private String activeView = "INPUTS";
  private static final Pattern CH_FADER = Pattern.compile("/ch/(\\d{2})/fdr");
  private static final Pattern CH_METER = Pattern.compile("/meters.*");

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    buildUi();
    udp = new MixerUdpService();
    udp.setListener(new MixerUdpService.Listener() {
      @Override public void onPacket(String address, Object[] args) { runOnUiThread(() -> handleFeedback(address, args)); }
      @Override public void onError(Exception error) { runOnUiThread(() -> status.setText("Hata: " + error.getMessage())); }
      @Override public void onConnected() { runOnUiThread(() -> { status.setText("X32 / M32 bağlı"); refreshSurface(); }); }
      @Override public void onDisconnected() { runOnUiThread(() -> status.setText("Bağlantı kesildi")); }
    });
  }

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(6), dp(6), dp(6), dp(6));

    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    EditText host = new EditText(this);
    host.setSingleLine(true);
    host.setHint("X32 / M32 IP");
    top.addView(host, new LinearLayout.LayoutParams(0, dp(46), 1));
    Button connect = button("BAĞLAN");
    top.addView(connect, new LinearLayout.LayoutParams(dp(108), dp(46)));
    status = text("Bağlantı yok");
    status.setGravity(Gravity.CENTER_VERTICAL);
    top.addView(status, new LinearLayout.LayoutParams(dp(175), dp(46)));
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
    prev.setOnClickListener(v -> { bank = Math.max(0, bank - 8); rebuildActive(); });
    next.setOnClickListener(v -> { bank = Math.min(24, bank + 8); rebuildActive(); });
    ch.setOnClickListener(v -> { activeView = "INPUTS"; rebuildActive(); });
    bus.setOnClickListener(v -> { activeView = "BUS"; rebuildActive(); });
    dca.setOnClickListener(v -> { activeView = "DCA"; rebuildActive(); });
    main.setOnClickListener(v -> { activeView = "MAIN"; rebuildActive(); });
    sof.setOnClickListener(v -> { activeView = "SENDS"; rebuildActive(); });
    connect.setOnClickListener(v -> connectToMixer(host));
    host.setText(getSharedPreferences("stagepulsemix", MODE_PRIVATE).getString("host", ""));
  }

  private void rebuildActive() {
    if ("BUS".equals(activeView)) buildBusBank();
    else if ("DCA".equals(activeView)) buildDcaBank();
    else if ("MAIN".equals(activeView)) buildMainBank();
    else if ("SENDS".equals(activeView)) buildSendOnFader();
    else buildBank(bank);
  }

  private void buildBank(int start) {
    channelList.removeAllViews();
    for (int i = 0; i < 8 && start + i < 32; i++) addInputStrip(start + i + 1);
    status.setText("INPUTS " + (start + 1) + "-" + Math.min(32, start + 8));
  }

  private void addInputStrip(int input) {
    LinearLayout strip = baseStrip("CH " + pad(input));
    TextView meter = text("M -∞");
    meter.setTag("meter:" + input);
    strip.addView(meter);
    TextView value = text("-∞ dB");
    value.setTag("faderText:" + input);
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
    int start = (bank / 8) * 8 + 1;
    for (int i = 0; i < 8 && start + i <= 16; i++) addBusStrip(start + i);
    status.setText("BUS " + start + "-" + Math.min(16, start + 7));
  }

  private void addBusStrip(int bus) {
    LinearLayout strip = baseStrip("BUS " + pad(bus));
    TextView value = text("-∞ dB");
    value.setTag("busText:" + bus);
    strip.addView(value);
    SeekBar f = fader();
    f.setTag("busFader:" + bus);
    f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar b, int p, boolean u) { if (u) value.setText(String.format(Locale.US, "%.1f dB", faderDb(p))); }
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
      final int dca = i;
      LinearLayout strip = baseStrip("DCA " + dca);
      TextView value = text("-∞ dB");
      strip.addView(value);
      SeekBar f = fader();
      f.setTag("dcaFader:" + dca);
      f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar b, int p, boolean u) { if (u) value.setText(String.format(Locale.US, "%.1f dB", faderDb(p))); }
        public void onStartTrackingTouch(SeekBar b) {}
        public void onStopTrackingTouch(SeekBar b) { send("/dca/" + pad(dca) + "/fdr", b.getProgress()/1000f); }
      });
      strip.addView(f, new LinearLayout.LayoutParams(dp(78), dp(250)));
      Button mute = button("M");
      mute.setOnClickListener(v -> toggle(mute, "/dca/" + pad(dca) + "/on", true));
      strip.addView(mute);
      channelList.addView(strip);
    }
    status.setText("DCA 1-8");
  }

  private void buildMainBank() {
    channelList.removeAllViews();
    addMainStrip("MAIN", "/main/st/mix/fader");
    addMainStrip("MONO", "/main/m/mix/fader");
    for (int i = 1; i <= 6; i++) addMainStrip("MATRIX " + i, "/mtx/" + pad(i) + "/fdr");
    status.setText("MAIN / MATRIX");
  }

  private void addMainStrip(String name, String address) {
    LinearLayout strip = baseStrip(name);
    TextView value = text("-∞ dB");
    strip.addView(value);
    SeekBar f = fader();
    f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar b, int p, boolean u) { if (u) value.setText(String.format(Locale.US, "%.1f dB", faderDb(p))); }
      public void onStartTrackingTouch(SeekBar b) {}
      public void onStopTrackingTouch(SeekBar b) { send(address, b.getProgress()/1000f); }
    });
    strip.addView(f, new LinearLayout.LayoutParams(dp(78), dp(250)));
    channelList.addView(strip);
  }

  private void buildSendOnFader() {
    channelList.removeAllViews();
    Button busMinus = button("BUS -");
    busMinus.setOnClickListener(v -> { sendsBus = Math.max(1, sendsBus - 1); buildSendOnFader(); });
    Button busPlus = button("BUS +");
    busPlus.setOnClickListener(v -> { sendsBus = Math.min(16, sendsBus + 1); buildSendOnFader(); });
    LinearLayout head = new LinearLayout(this);
    head.addView(busMinus); head.addView(text("BUS " + sendsBus)); head.addView(busPlus);
    channelList.addView(head);
    int start = (bank / 8) * 8 + 1;
    for (int i = 0; i < 8 && start + i <= 32; i++) {
      final int input = start + i;
      LinearLayout strip = baseStrip("CH " + pad(input));
      TextView value = text("-∞ dB");
      strip.addView(value);
      SeekBar f = fader();
      f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar b, int p, boolean u) { if (u) value.setText(String.format(Locale.US, "%.1f dB", faderDb(p))); }
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
    addEdit(strip, "GAIN +0", chAddr(input, "preamp/gain"));
    addEdit(strip, "PHANTOM", chAddr(input, "preamp/phantom"));
    addEdit(strip, "POLARITY", chAddr(input, "preamp/invert"));
    addEdit(strip, "HPF", chAddr(input, "eq/lo/f"));
    addEdit(strip, "EQ1 F", chAddr(input, "eq/1/f"));
    addEdit(strip, "EQ1 G", chAddr(input, "eq/1/g"));
    addEdit(strip, "EQ2 F", chAddr(input, "eq/2/f"));
    addEdit(strip, "EQ2 G", chAddr(input, "eq/2/g"));
    addEdit(strip, "EQ3 F", chAddr(input, "eq/3/f"));
    addEdit(strip, "EQ3 G", chAddr(input, "eq/3/g"));
    addEdit(strip, "EQ4 F", chAddr(input, "eq/4/f"));
    addEdit(strip, "EQ4 G", chAddr(input, "eq/4/g"));
    addEdit(strip, "DELAY", chAddr(input, "delay/time"));
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
    send("/xremote"); send("/info"); send("/status"); send("/config");
    send("/-stat/chfaderbank"); send("/-stat/grpfaderbank"); send("/-stat/sendsonfader");
    send("/meters", "/meters/0", 1); send("/meters", "/meters/6", 1); send("/meters", "/meters/7", 1); send("/meters", "/meters/12", 1);
  }

  private void handleFeedback(String address, Object[] args) {
    Matcher m = CH_FADER.matcher(address);
    if (m.matches() && args.length > 0 && args[0] instanceof Number) {
      int input = Integer.parseInt(m.group(1));
      float raw = ((Number) args[0]).floatValue();
      float normalized = normalizeFader(raw);
      View view = channelList.findViewWithTag("fader:" + input);
      if (view instanceof SeekBar) ((SeekBar) view).setProgress(Math.max(0, Math.min(1000, Math.round(normalized * 1000))));
      View value = channelList.findViewWithTag("faderText:" + input);
      if (value instanceof TextView) ((TextView) value).setText(String.format(Locale.US, "%.1f dB", faderDb(Math.round(normalized * 1000))));
      status.setText("RX " + address + "  " + String.format(Locale.US, "%.1f dB", faderDb(Math.round(normalized * 1000))));
      return;
    }
    if (address.startsWith("/info") || address.startsWith("/status") || address.startsWith("/config")) {
      status.setText("Bağlı: " + address);
    }
  }

  private float normalizeFader(float value) {
    if (value >= 0f && value <= 1.01f) return value;
    return Math.max(0f, Math.min(1f, (value + 90f) / 100f));
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
    strip.addView(text(name));
    return strip;
  }

  private SeekBar fader() { SeekBar f = new SeekBar(this); f.setMax(1000); f.setProgress(1000); f.setRotation(-90f); return f; }
  private TextView text(String s) { TextView t = new TextView(this); t.setText(s); t.setGravity(Gravity.CENTER); t.setPadding(2,2,2,2); return t; }
  private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }
  private void send(String address, Object... args) { try { if (udp != null) udp.send(address, args); } catch (Exception e) { status.setText("TX hata: " + e.getMessage()); } }
  private String chAddr(int ch, String suffix) { return "/ch/" + pad(ch) + "/" + suffix; }
  private String pad(int n) { return String.format(Locale.US, "%02d", n); }
  private float faderDb(int p) { return -90f + p * 0.1f; }
  private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override protected void onDestroy() { if (udp != null) udp.shutdown(); super.onDestroy(); }
}
