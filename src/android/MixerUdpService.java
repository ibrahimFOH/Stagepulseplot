package com.stagepulse.mix.android;

import android.os.Handler;
import android.os.Looper;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Direct UDP OSC transport for StagePulseMix Android. */
public final class MixerUdpService {
    public interface Listener {
        void onPacket(String address, Object[] args);
        void onError(Exception error);
        void onConnected();
        void onDisconnected();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private DatagramSocket socket;
    private InetAddress mixerAddress;
    private int mixerPort = 10023;
    private Listener listener;
    private volatile boolean running;

    public void setListener(Listener listener) { this.listener = listener; }

    public void connect(String host, int port, int localPort) {
        close();
        io.execute(() -> {
            try {
                mixerAddress = InetAddress.getByName(host);
                mixerPort = port;
                socket = new DatagramSocket(localPort);
                running = true;
                main.post(() -> { if (listener != null) listener.onConnected(); });
                byte[] buffer = new byte[65535];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    // Full OSC decoding remains in protocol layer; Android transport is intentionally vendor-neutral.
                    final String text = new String(packet.getData(), packet.getOffset(), packet.getLength());
                    main.post(() -> { if (listener != null) listener.onPacket(text, new Object[0]); });
                }
            } catch (Exception e) {
                if (running) main.post(() -> { if (listener != null) listener.onError(e); });
            } finally {
                running = false;
                main.post(() -> { if (listener != null) listener.onDisconnected(); });
            }
        });
    }

    public void send(byte[] data) {
        io.execute(() -> {
            try {
                if (socket == null || mixerAddress == null) throw new IllegalStateException("Mixer bağlantısı yok");
                DatagramPacket packet = new DatagramPacket(data, data.length, mixerAddress, mixerPort);
                socket.send(packet);
            } catch (Exception e) {
                main.post(() -> { if (listener != null) listener.onError(e); });
            }
        });
    }

    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    public void shutdown() {
        close();
        io.shutdownNow();
    }
}
