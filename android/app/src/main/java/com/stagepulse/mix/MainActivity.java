package com.stagepulse.mix;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
  private EditText host;
  private MixerUdpService udp;
  private SharedPreferences prefs;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    setContentView(R.layout.activity_main);
    host = findViewById(R.id.backendUrl);
    Button open = findViewById(R.id.open);
    prefs = getSharedPreferences("stagepulsemix", MODE_PRIVATE);
    host.setText(prefs.getString("host", "192.168.1.100"));
    open.setText("X32 / M32 BAĞLAN");
    udp = new MixerUdpService();
    udp.setListener(new MixerUdpService.Listener() {
      @Override public void onPacket(String address, Object[] args) { }
      @Override public void onError(Exception error) { runOnUiThread(() -> Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show()); }
      @Override public void onConnected() { runOnUiThread(() -> Toast.makeText(MainActivity.this, "X32 / M32 bağlantısı hazır", Toast.LENGTH_SHORT).show()); }
      @Override public void onDisconnected() { }
    });
    open.setOnClickListener(v -> connect());
  }

  private void connect() {
    String h = host.getText().toString().trim();
    if (h.isEmpty()) { Toast.makeText(this, "X32/M32 IP gerekli", Toast.LENGTH_SHORT).show(); return; }
    prefs.edit().putString("host", h).apply();
    udp.connect(h, 10023, 0);
  }

  @Override protected void onDestroy() {
    if (udp != null) udp.shutdown();
    super.onDestroy();
  }
}
