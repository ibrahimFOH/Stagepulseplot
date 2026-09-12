package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
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
  private final MixerSurfaceState surface = new MixerSurfaceState();
  private final AppUpdater updater = new AppUpdater(this);
  private static final Pattern CH_FADER = Pattern.compile("/ch/(\\d{2})/fdr");
  private static final Pattern CH_MUTE = Pattern.compile("/ch/(\\d{2})/mix/on");
  private static final Pattern CH_SOLO = Pattern.compile("/ch/(\\d{2})/mix/solo");
  private static final Pattern CH_PAN = Pattern.compile("/ch/(\\d{2})/pan");
  private static final Pattern BUS_FADER = Pattern.compile("/bus/(\\d{2})/fdr");
  private static final Pattern DCA_FADER = Pattern.compile("/dca/(\\d{2})/fdr");

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    buildUi();
    udp = new MixerUdpService();
    udp.setListener(new MixerUdpService.Listener() {
      @Override public void onPacket(String address, Object[] args) { runOnUiThread(() -> handleFeedback(address, args)); }
      @Override public void onError(Exception error) { runOnUiThread(() -> status.setText("Hata: " + error.getMessage())); }
      @Override public void onConnected() { runOnUiThread(() -> { status.setText("M32 / X32 bağlandı"); refreshSurface(); }); }
      @Override public void onDisconnected() { runOnUiThread(() -> status.setText("Bağlantı kesildi")); }
    });
    updater.check(false);
  }

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(8), dp(5), dp(8), dp(5));

    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    EditText host = new EditText(this);
    host.setSingleLine(true);
    host.setHint("M32 / X32 IP");
    top.addView(host, new LinearLayout.LayoutParams(0, dp(44), 1));
    Button connect = button("BAĞLAN");
    top.addView(connect, new LinearLayout.LayoutParams(dp(112), dp(44)));
    Button update = button("GÜNCELLE");
    top.addView(update, new LinearLayout.LayoutParams(dp(120), dp(44)));
    status = text("Bağlantı yok");
    status.setGravity(Gravity.CENTER_VERTICAL);
    top.addView(status, new LinearLayout.LayoutParams(dp(190), dp(44)));
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
    update.setOnClickListener(v -> updater.check(true));
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
    TextView meter = text(formatMeter(surface.getChannelMeter(input)));
    meter.setTag("meter:" + input);
    strip.addView(meter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
    TextView value = text(formatDb(surface.getChannelFader(input)));
    value.setTag("faderText:" + input);
    strip.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
    SeekBar fader = fader();
    fader.setTag("fader:" + input);
    fader.setProgress(Math.round(surface.getChannelFader(input) * 1000));
    fader.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar bar, int p, boolean fromUser) { if (fromUser) value.setText(formatDb(p / 1000f)); }
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) { float v = bar.getProgress()/1000f; surface.setChannelFader(input,v); send(chAddr(input,"fdr"),v); }
    });
    strip.addView(fader, new LinearLayout.LayoutParams(dp(82), dp(248)));
    Button mute = button(surface.isMuted(input) ? "M●" : "M");
    mute.setTag("mute:"+input);
    mute.setOnClickListener(v -> { boolean next=!surface.isMuted(input); surface.setChannelMute(input,next); mute.setText(next?"M●":"M"); send(chAddr(input,"mix/on"),next?0:1); });
    strip.addView(mute);
    Button solo = button(surface.isSolo(input) ? "S●" : "S");
    solo.setTag("solo:"+input);
    solo.setOnClickListener(v -> { boolean next=!surface.isSolo(input); surface.setChannelSolo(input,next); solo.setText(next?"S●":"S"); send(chAddr(input,"mix/solo"),next?1:0); });
    strip.addView(solo);
    Button pan=button("PAN");
    pan.setTag("pan:"+input);
    pan.setOnTouchListener((v,e)->{ if(e.getAction()==MotionEvent.ACTION_UP){float next=surface.channel(input).pan+0.1f;if(next>0.99f)next=0.01f;surface.setChannelPan(input,next);send(chAddr(input,"pan"),next);} return true; });
    strip.addView(pan);
    Button detail=button("EDIT"); detail.setOnClickListener(v->showInputTools(input)); strip.addView(detail);
    channelList.addView(strip);
  }

  private void buildBusBank(){
    channelList.removeAllViews(); int start=(bank/8)*8+1;
    for(int i=0;i<8&&start+i<=16;i++)addBusStrip(start+i);
    status.setText("BUS "+start+"-"+Math.min(16,start+7));
  }
  private void addBusStrip(int bus){
    LinearLayout strip=baseStrip("BUS "+pad(bus)); TextView value=text(formatDb(surface.getBusFader(bus))); strip.addView(value,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)));
    SeekBar f=fader(); f.setTag("busFader:"+bus); f.setProgress(Math.round(surface.getBusFader(bus)*1000));
    f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)value.setText(formatDb(p/1000f));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){float v=b.getProgress()/1000f;surface.setBusFader(bus,v);send("/bus/"+pad(bus)+"/fdr",v);}});
    strip.addView(f,new LinearLayout.LayoutParams(dp(82),dp(248))); channelList.addView(strip);
  }
  private void buildDcaBank(){
    channelList.removeAllViews(); for(int i=1;i<=8;i++){final int dca=i;LinearLayout strip=baseStrip("DCA "+dca);TextView value=text(formatDb(surface.getDcaFader(dca)));strip.addView(value,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)));SeekBar f=fader();f.setProgress(Math.round(surface.getDcaFader(dca)*1000));f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)value.setText(formatDb(p/1000f));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){float v=b.getProgress()/1000f;surface.setDcaFader(dca,v);send("/dca/"+pad(dca)+"/fdr",v);}});strip.addView(f,new LinearLayout.LayoutParams(dp(82),dp(248)));channelList.addView(strip);} status.setText("DCA 1-8");
  }
  private void buildMainBank(){channelList.removeAllViews();addMainStrip("MAIN","/main/st/mix/fader",surface.getMainFader());addMainStrip("MONO","/main/m/mix/fader",0f);for(int i=1;i<=6;i++)addMainStrip("MATRIX "+i,"/mtx/"+pad(i)+"/fdr",0f);status.setText("MAIN / MONO / MATRIX");}
  private void addMainStrip(String name,String address,float current){LinearLayout strip=baseStrip(name);TextView value=text(formatDb(current));strip.addView(value,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)));SeekBar f=fader();f.setProgress(Math.round(current*1000));f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)value.setText(formatDb(p/1000f));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){send(address,b.getProgress()/1000f);}});strip.addView(f,new LinearLayout.LayoutParams(dp(82),dp(248)));channelList.addView(strip);}
  private void buildSendOnFader(){channelList.removeAllViews();Button minus=button("BUS -");minus.setOnClickListener(v->{sendsBus=Math.max(1,sendsBus-1);buildSendOnFader();});Button plus=button("BUS +");plus.setOnClickListener(v->{sendsBus=Math.min(16,sendsBus+1);buildSendOnFader();});LinearLayout head=new LinearLayout(this);head.addView(minus);head.addView(text("BUS "+sendsBus));head.addView(plus);channelList.addView(head);int start=(bank/8)*8+1;for(int i=0;i<8&&start+i<=32;i++){final int input=start+i;LinearLayout strip=baseStrip("CH "+pad(input));TextView value=text("-∞ dB");strip.addView(value,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)));SeekBar f=fader();f.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)value.setText(formatDb(p/1000f));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){send("/ch/"+pad(input)+"/mix/"+pad(sendsBus)+"/send",b.getProgress()/1000f);}});strip.addView(f,new LinearLayout.LayoutParams(dp(82),dp(248)));channelList.addView(strip);}status.setText("SENDS ON FADER: BUS "+sendsBus);}
  private void showInputTools(int input){channelList.removeAllViews();LinearLayout strip=baseStrip("CH "+pad(input)+" DETAIL");addNumericEdit(strip,"GAIN",chAddr(input,"preamp/gain"),0f);addToggleEdit(strip,"PHANTOM",chAddr(input,"preamp/phantom"));addToggleEdit(strip,"POLARITY",chAddr(input,"preamp/invert"));addNumericEdit(strip,"HPF",chAddr(input,"eq/lo/f"),0.5f);for(int band=1;band<=4;band++){addNumericEdit(strip,"EQ"+band+" F",chAddr(input,"eq/"+band+"/f"),0.5f);addNumericEdit(strip,"EQ"+band+" G",chAddr(input,"eq/"+band+"/g"),0.5f);addNumericEdit(strip,"EQ"+band+" Q",chAddr(input,"eq/"+band+"/q"),0.5f);}addNumericEdit(strip,"DELAY",chAddr(input,"delay/time"),0f);Button back=button("‹ INPUTS");back.setOnClickListener(v->rebuildActive());strip.addView(back);channelList.addView(strip);status.setText("CH "+pad(input)+" DETAIL");}
  private void addNumericEdit(LinearLayout parent,String title,String address,float value){Button b=button(title);b.setOnClickListener(v->send(address,value));parent.addView(b);} private void addToggleEdit(LinearLayout parent,String title,String address){Button b=button(title);b.setOnClickListener(v->send(address,1));parent.addView(b);}
  private void connectToMixer(EditText host){String h=host.getText().toString().trim();if(h.isEmpty()){status.setText("M32/X32 IP gerekli");return;}getSharedPreferences("stagepulsemix",MODE_PRIVATE).edit().putString("host",h).apply();status.setText("Bağlanıyor: "+h);udp.connect(h,10023,0);}
  private void refreshSurface(){send("/xremote");send("/info");send("/status");send("/config");send("/-stat/chfaderbank");send("/-stat/grpfaderbank");send("/-stat/sendsonfader");send("/meters", "/meters/0",1);send("/meters", "/meters/6",1);send("/meters", "/meters/7",1);send("/meters", "/meters/12",1);for(int i=1;i<=32;i++){send(chAddr(i,"fdr"));send(chAddr(i,"mix/on"));send(chAddr(i,"mix/solo"));send(chAddr(i,"pan"));}for(int i=1;i<=16;i++)send("/bus/"+pad(i)+"/fdr");for(int i=1;i<=8;i++)send("/dca/"+pad(i)+"/fdr");}
  private void handleFeedback(String address,Object[] args){if(args.length==0)return;Object first=args[0];Matcher m=CH_FADER.matcher(address);if(m.matches()&&first instanceof Number){int ch=Integer.parseInt(m.group(1));float v=normalizeFader(((Number)first).floatValue());surface.setChannelFader(ch,v);updateInputFader(ch,v);return;}m=CH_MUTE.matcher(address);if(m.matches()&&first instanceof Number){int ch=Integer.parseInt(m.group(1));boolean muted=((Number)first).floatValue()<0.5f;surface.setChannelMute(ch,muted);updateInputMute(ch,muted);return;}m=CH_SOLO.matcher(address);if(m.matches()&&first instanceof Number){int ch=Integer.parseInt(m.group(1));boolean solo=((Number)first).floatValue()>=0.5f;surface.setChannelSolo(ch,solo);updateInputSolo(ch,solo);return;}m=CH_PAN.matcher(address);if(m.matches()&&first instanceof Number){surface.setChannelPan(Integer.parseInt(m.group(1)),normalize01(((Number)first).floatValue()));return;}m=BUS_FADER.matcher(address);if(m.matches()&&first instanceof Number){surface.setBusFader(Integer.parseInt(m.group(1)),normalizeFader(((Number)first).floatValue()));return;}m=DCA_FADER.matcher(address);if(m.matches()&&first instanceof Number){surface.setDcaFader(Integer.parseInt(m.group(1)),normalizeFader(((Number)first).floatValue()));return;}if(address.contains("/meters")||address.startsWith("/meters")){handleMeter(args);return;}if(address.startsWith("/info"))status.setText("Mikser bilgisi alındı");}
  private void updateInputFader(int input,float v){View f=channelList.findViewWithTag("fader:"+input);if(f instanceof SeekBar){SeekBar bar=(SeekBar)f;int target=Math.round(v*1000);if(Math.abs(bar.getProgress()-target)>2)bar.setProgress(target);}View t=channelList.findViewWithTag("faderText:"+input);if(t instanceof TextView)((TextView)t).setText(formatDb(v));}
  private void updateInputMute(int input,boolean muted){View v=channelList.findViewWithTag("mute:"+input);if(v instanceof Button)((Button)v).setText(muted?"M●":"M");}
  private void updateInputSolo(int input,boolean solo){View v=channelList.findViewWithTag("solo:"+input);if(v instanceof Button)((Button)v).setText(solo?"S●":"S");}
  private void handleMeter(Object[] args){for(int i=0;i<args.length&&i<32;i++)if(args[i] instanceof Number){float v=normalizeMeter(((Number)args[i]).floatValue());surface.setChannelMeter(i+1,v);View meter=channelList.findViewWithTag("meter:"+(i+1));if(meter instanceof TextView)((TextView)meter).setText(formatMeter(v));}}
  private float normalizeMeter(float value){if(Float.isNaN(value)||Float.isInfinite(value))return 0f;if(value>=0f&&value<=1f)return value;float db=Math.max(-90f,Math.min(10f,value));return(db+90f)/100f;}
  private float normalizeFader(float value){if(value>=0f&&value<=1.01f)return clamp01(value);return clamp01((value+90f)/100f);} private float normalize01(float value){return clamp01(value);} private float clamp01(float value){return Math.max(0f,Math.min(1f,value));}
  private String formatDb(float value){float db=-90f+clamp01(value)*100f;return db<=-89.9f?"-∞ dB":String.format(Locale.US,"%.1f dB",db);} private String formatMeter(float value){float db=-90f+clamp01(value)*100f;return db<=-89.9f?"M -∞":"M "+String.format(Locale.US,"%.1f",db);}
  private LinearLayout baseStrip(String name){LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.VERTICAL);strip.setGravity(Gravity.CENTER_HORIZONTAL);strip.setPadding(dp(4),dp(4),dp(4),dp(4));strip.setLayoutParams(new LinearLayout.LayoutParams(dp(104),ViewGroup.LayoutParams.MATCH_PARENT));strip.addView(text(name));return strip;}
  private SeekBar fader(){SeekBar f=new SeekBar(this);f.setMax(1000);f.setProgress(0);f.setRotation(-90f);return f;} private TextView text(String s){TextView t=new TextView(this);t.setText(s);t.setGravity(Gravity.CENTER);t.setPadding(2,2,2,2);return t;} private Button button(String s){Button b=new Button(this);b.setText(s);return b;} private void send(String address,Object...args){try{if(udp!=null)udp.send(address,args);}catch(Exception e){status.setText("TX hata: "+e.getMessage());}} private String chAddr(int ch,String suffix){return "/ch/"+pad(ch)+"/"+suffix;} private String pad(int n){return String.format(Locale.US,"%02d",n);} private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);}
  @Override protected void onDestroy(){if(udp!=null)udp.shutdown();super.onDestroy();}
}
