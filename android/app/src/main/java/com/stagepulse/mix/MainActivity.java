package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.stagepulse.mix.android.MixerUdpService;

public class MainActivity extends Activity {
  private EditText host;
  private EditText port;
  private MixerUdpService udp;
  private SharedPreferences prefs;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    setContentView(R.layout.activity_main);
    host = findViewById(R.id.backendUrl);
    port = findViewById(R.id.mixerPort);
    Button open = findViewById(R.id.open);
    prefs = getSharedPreferences("stagepulsemix", MODE_PRIVATE);
    host.setText(prefs.getString("host", "192.168.1.10"));
    port.setText(prefs.getString("port", "10023"));
    open.setText("X32 / M32 BAĞLAN");
    udp = new MixerUdpService();
    udp.setListener(new MixerUdpService.Listener() {
      @Override public void onPacket(String address, Object[] args) { }
      @Override public void onError(Exception error) { runOnUiThread(() -> Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show()); }
      @Override public void onConnected() { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Mikser bağlantısı hazır", Toast.LENGTH_SHORT).show()); }
      @Override public void onDisconnected() { }
    });
    open.setOnClickListener(v -> connect());
  }

  private void connect() {
    String h = host.getText().toString().trim();
    int p;
    try { p = Integer.parseInt(port.getText().toString().trim()); }
    catch (Exception e) { Toast.makeText(this, "Port geçersiz", Toast.LENGTH_SHORT).show(); return; }
    if (h.isEmpty() || p < 1 || p > 65535) { Toast.makeText(this, "X32/M32 IP ve port gerekli", Toast.LENGTH_SHORT).show(); return; }
    prefs.edit().putString("host", h).putString("port", String.valueOf(p)).apply();
    udp.connect(h, p, 0);
  }

  @Override protected void onDestroy() {
    udp.shutdown();
    super.onDestroy();
  }
}
