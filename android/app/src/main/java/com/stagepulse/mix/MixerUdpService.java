package com.stagepulse.mix;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal direct UDP OSC transport for X32/M32. */
public final class MixerUdpService {
  public interface Listener {
    void onPacket(String address, Object[] args);
    void onError(Exception error);
    void onConnected();
    void onDisconnected();
  }

  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean running;
  private DatagramSocket socket;
  private Listener listener;
  private InetAddress target;
  private int targetPort;

  public void setListener(Listener listener) { this.listener = listener; }

  public void connect(String host, int port, int localPort) {
    new Thread(() -> {
      try {
        target = InetAddress.getByName(host);
        targetPort = port;
        socket = localPort > 0 ? new DatagramSocket(localPort) : new DatagramSocket();
        socket.setSoTimeout(1000);
        running = true;
        send("/xremote");
        send("/info");
        postConnected();
        while (running) {
          try {
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            OscPacket decoded = decode(packet.getData(), packet.getLength());
            if (decoded != null && listener != null) {
              main.post(() -> listener.onPacket(decoded.address, decoded.args));
            }
          } catch (java.net.SocketTimeoutException ignored) {
          }
        }
      } catch (Exception e) {
        postError(e);
      } finally {
        DatagramSocket s = socket;
        socket = null;
        if (s != null) s.close();
        main.post(() -> { if (listener != null) listener.onDisconnected(); });
      }
    }, "stagepulse-udp").start();
  }

  public void send(String address, Object... args) throws IOException {
    DatagramSocket s = socket;
    if (s == null || target == null) throw new IOException("Mikser bağlantısı yok");
    byte[] data = encode(address, args);
    s.send(new DatagramPacket(data, data.length, target, targetPort));
  }

  public void shutdown() {
    running = false;
    DatagramSocket s = socket;
    if (s != null) s.close();
  }

  private void postConnected() { main.post(() -> { if (listener != null) listener.onConnected(); }); }
  private void postError(Exception e) { main.post(() -> { if (listener != null) listener.onError(e); }); }

  private static byte[] encode(String address, Object[] args) {
    byte[] a = oscString(address);
    StringBuilder tags = new StringBuilder(",");
    for (Object arg : args) {
      if (arg instanceof String) tags.append('s');
      else if (arg instanceof Integer || arg instanceof Long) tags.append('i');
      else if (arg instanceof Number) tags.append('f');
      else if (arg instanceof Boolean) tags.append(((Boolean)arg) ? 'T' : 'F');
      else throw new IllegalArgumentException("Unsupported OSC argument");
    }
    byte[] t = oscString(tags.toString());
    ByteBuffer out = ByteBuffer.allocate(a.length + t.length + args.length * 8).order(ByteOrder.BIG_ENDIAN);
    out.put(a).put(t);
    for (Object arg : args) {
      if (arg instanceof String) out.put(oscString((String)arg));
      else if (arg instanceof Integer) out.putInt((Integer)arg);
      else if (arg instanceof Long) out.putInt(((Long)arg).intValue());
      else if (arg instanceof Number) out.putFloat(((Number)arg).floatValue());
    }
    byte[] result = new byte[out.position()];
    out.flip(); out.get(result);
    return result;
  }

  private static byte[] oscString(String value) {
    byte[] raw = value.getBytes(StandardCharsets.UTF_8);
    int size = (raw.length + 1 + 3) & ~3;
    byte[] out = new byte[size];
    System.arraycopy(raw, 0, out, 0, raw.length);
    return out;
  }

  private static OscPacket decode(byte[] packet, int length) {
    try {
      Cursor c = new Cursor(packet, length);
      String address = c.readString();
      String tags = c.readString();
      if (!tags.startsWith(",")) return null;
      List<Object> args = new ArrayList<>();
      for (int i = 1; i < tags.length(); i++) {
        switch (tags.charAt(i)) {
          case 's': args.add(c.readString()); break;
          case 'i': args.add(c.readInt()); break;
          case 'f': args.add(c.readFloat()); break;
          case 'T': args.add(Boolean.TRUE); break;
          case 'F': args.add(Boolean.FALSE); break;
          default: return null;
        }
      }
      return new OscPacket(address, args.toArray());
    } catch (Exception ignored) { return null; }
  }

  private static final class Cursor {
    final byte[] b; final int limit; int pos;
    Cursor(byte[] b, int limit) { this.b = b; this.limit = limit; }
    String readString() {
      int end = pos;
      while (end < limit && b[end] != 0) end++;
      if (end >= limit) throw new IllegalArgumentException();
      String s = new String(b, pos, end-pos, StandardCharsets.UTF_8);
      pos = (end + 4) & ~3;
      return s;
    }
    int readInt() { int v = ByteBuffer.wrap(b, pos, 4).order(ByteOrder.BIG_ENDIAN).getInt(); pos += 4; return v; }
    float readFloat() { float v = ByteBuffer.wrap(b, pos, 4).order(ByteOrder.BIG_ENDIAN).getFloat(); pos += 4; return v; }
  }

  private static final class OscPacket {
    final String address; final Object[] args;
    OscPacket(String address, Object[] args) { this.address = address; this.args = args; }
  }
}
