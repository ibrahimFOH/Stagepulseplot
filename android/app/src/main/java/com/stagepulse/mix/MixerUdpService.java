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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Direct UDP OSC transport for StagePulseMix Android. */
public final class MixerUdpService {
  public interface Listener {
    void onPacket(String address, Object[] args);
    void onError(Exception error);
    void onConnected();
    void onDisconnected();
  }

  private final Handler main = new Handler(Looper.getMainLooper());
  private final ScheduledExecutorService io = Executors.newScheduledThreadPool(2);
  private DatagramSocket socket;
  private InetAddress target;
  private int targetPort = 10023;
  private volatile boolean running;
  private volatile long lastRxMs;
  private Listener listener;

  public void setListener(Listener listener) { this.listener = listener; }

  public synchronized void connect(String host, int port, int localPort) {
    shutdownSocket();
    io.execute(() -> {
      try {
        target = InetAddress.getByName(host);
        targetPort = port > 0 ? port : 10023;
        socket = localPort > 0 ? new DatagramSocket(localPort) : new DatagramSocket();
        socket.setSoTimeout(1000);
        running = true;
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
        postConnected();
        io.scheduleWithFixedDelay(() -> {
          if (!running) return;
          try { send("/xremote"); } catch (Exception e) { postError(e); }
        }, 8, 8, TimeUnit.SECONDS);
        io.execute(this::receiveLoop);
      } catch (Exception e) {
        postError(e);
        shutdownSocket();
      }
    });
  }

  private void receiveLoop() {
    try {
      byte[] buffer = new byte[65535];
      while (running) {
        try {
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          socket.receive(packet);
          List<OscPacket> decoded = decodePackets(packet.getData(), packet.getOffset(), packet.getLength());
          lastRxMs = System.currentTimeMillis();
          for (OscPacket p : decoded) {
            final String address = p.address;
            final Object[] args = p.args;
            main.post(() -> { if (listener != null) listener.onPacket(address, args); });
          }
        } catch (java.net.SocketTimeoutException ignored) { }
      }
    } catch (Exception e) {
      if (running) postError(e);
    } finally {
      if (running) {
        running = false;
        main.post(() -> { if (listener != null) listener.onDisconnected(); });
      }
    }
  }

  public void send(String address, Object... args) throws IOException {
    DatagramSocket s;
    InetAddress t;
    int p;
    synchronized (this) {
      s = socket; t = target; p = targetPort;
    }
    if (!running || s == null || t == null) throw new IOException("Mikser bağlantısı yok");
    byte[] data = encode(address, args);
    s.send(new DatagramPacket(data, data.length, t, p));
  }

  public boolean isConnected() { return running; }
  public long getLastRxMs() { return lastRxMs; }

  public synchronized void shutdownSocket() {
    running = false;
    if (socket != null) {
      socket.close();
      socket = null;
    }
    target = null;
  }

  public void shutdown() {
    shutdownSocket();
    io.shutdownNow();
  }

  private void postConnected() { main.post(() -> { if (listener != null) listener.onConnected(); }); }
  private void postError(Exception e) { main.post(() -> { if (listener != null) listener.onError(e); }); }

  private static byte[] encode(String address, Object[] args) {
    byte[] a = oscString(address);
    StringBuilder tags = new StringBuilder(",");
    for (Object arg : args) {
      if (arg instanceof String) tags.append('s');
      else if (arg instanceof Boolean) tags.append(((Boolean) arg) ? 'T' : 'F');
      else if (arg instanceof Byte || arg instanceof Short || arg instanceof Integer || arg instanceof Long) tags.append('i');
      else if (arg instanceof Number) tags.append('f');
      else throw new IllegalArgumentException("Unsupported OSC argument");
    }
    byte[] t = oscString(tags.toString());
    int size = a.length + t.length;
    for (Object arg : args) size += arg instanceof String ? oscString((String) arg).length : (arg instanceof Boolean ? 0 : 4);
    ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
    out.put(a).put(t);
    for (Object arg : args) {
      if (arg instanceof String) out.put(oscString((String)arg));
      else if (arg instanceof Boolean) { }
      else if (arg instanceof Byte || arg instanceof Short || arg instanceof Integer || arg instanceof Long) out.putInt(((Number)arg).intValue());
      else out.putFloat(((Number)arg).floatValue());
    }
    byte[] result = new byte[out.position()]; out.flip(); out.get(result); return result;
  }

  private static byte[] oscString(String value) {
    byte[] raw = value.getBytes(StandardCharsets.UTF_8);
    int size = (raw.length + 1 + 3) & ~3;
    byte[] out = new byte[size];
    System.arraycopy(raw, 0, out, 0, raw.length);
    return out;
  }

  private static List<OscPacket> decodePackets(byte[] packet, int offset, int length) {
    Cursor c = new Cursor(packet, offset, length);
    String address = c.readString();
    if ("#bundle".equals(address)) {
      c.skip(8);
      List<OscPacket> result = new ArrayList<>();
      while (c.remaining() >= 4) {
        int size = c.readInt();
        if (size <= 0 || size > c.remaining()) break;
        result.addAll(decodePackets(c.readBytes(size), 0, size));
      }
      return result;
    }
    String tags = c.readString();
    if (!tags.startsWith(",")) return new ArrayList<>();
    List<Object> args = new ArrayList<>();
    for (int i = 1; i < tags.length(); i++) {
      switch (tags.charAt(i)) {
        case 's': args.add(c.readString()); break;
        case 'i': args.add(c.readInt()); break;
        case 'f': args.add(c.readFloat()); break;
        case 'T': args.add(Boolean.TRUE); break;
        case 'F': args.add(Boolean.FALSE); break;
        case 'b':
          int n = c.readInt();
          args.add(c.readBytes(n));
          c.align4();
          break;
        default: return new ArrayList<>();
      }
    }
    List<OscPacket> one = new ArrayList<>();
    one.add(new OscPacket(address, args.toArray()));
    return one;
  }

  private static final class Cursor {
    final byte[] b; final int end; int pos;
    Cursor(byte[] b, int offset, int length) { this.b = b; this.pos = offset; this.end = offset + length; }
    int remaining() { return end - pos; }
    void skip(int n) { if (n < 0 || pos + n > end) throw new IllegalArgumentException(); pos += n; }
    void align4() { pos = (pos + 3) & ~3; if (pos > end) throw new IllegalArgumentException(); }
    String readString() {
      int p = pos;
      while (p < end && b[p] != 0) p++;
      if (p >= end) throw new IllegalArgumentException();
      String s = new String(b, pos, p - pos, StandardCharsets.UTF_8);
      pos = (p + 4) & ~3;
      if (pos > end) throw new IllegalArgumentException();
      return s;
    }
    int readInt() { if (pos + 4 > end) throw new IllegalArgumentException(); int v = ByteBuffer.wrap(b,pos,4).order(ByteOrder.BIG_ENDIAN).getInt(); pos += 4; return v; }
    float readFloat() { if (pos + 4 > end) throw new IllegalArgumentException(); float v = ByteBuffer.wrap(b,pos,4).order(ByteOrder.BIG_ENDIAN).getFloat(); pos += 4; return v; }
    byte[] readBytes(int n) { if (n < 0 || pos + n > end) throw new IllegalArgumentException(); byte[] out = new byte[n]; System.arraycopy(b,pos,out,0,n); pos += n; return out; }
  }

  private static final class OscPacket {
    final String address; final Object[] args;
    OscPacket(String address, Object[] args) { this.address = address; this.args = args; }
  }
}
