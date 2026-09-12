package com.stagepulse.mix;

import java.util.HashMap;
import java.util.Map;

/** Lightweight live surface cache for direct M32/X32 OSC feedback. */
public final class MixerSurfaceState {
  public static final class Channel {
    public float fader = 0f;
    public float pan = 0.5f;
    public float meter = 0f;
    public boolean muted = false;
    public boolean solo = false;
  }

  private final Map<Integer, Channel> channels = new HashMap<>();
  private final Map<Integer, Float> busFaders = new HashMap<>();
  private final Map<Integer, Float> dcaFaders = new HashMap<>();
  private float mainFader = 0f;

  public Channel channel(int index) {
    Channel c = channels.get(index);
    if (c == null) { c = new Channel(); channels.put(index, c); }
    return c;
  }

  public void setChannelFader(int index, float value) { channel(index).fader = clamp01(value); }
  public void setChannelPan(int index, float value) { channel(index).pan = clamp01(value); }
  public void setChannelMeter(int index, float value) { channel(index).meter = clamp01(value); }
  public void setChannelMute(int index, boolean value) { channel(index).muted = value; }
  public void setChannelSolo(int index, boolean value) { channel(index).solo = value; }
  public void setBusFader(int index, float value) { busFaders.put(index, clamp01(value)); }
  public void setDcaFader(int index, float value) { dcaFaders.put(index, clamp01(value)); }
  public void setMainFader(float value) { mainFader = clamp01(value); }
  public float getChannelFader(int index) { return channel(index).fader; }
  public float getChannelMeter(int index) { return channel(index).meter; }
  public boolean isMuted(int index) { return channel(index).muted; }
  public boolean isSolo(int index) { return channel(index).solo; }
  public float getBusFader(int index) { return busFaders.containsKey(index) ? busFaders.get(index) : 0f; }
  public float getDcaFader(int index) { return dcaFaders.containsKey(index) ? dcaFaders.get(index) : 0f; }
  public float getMainFader() { return mainFader; }

  public static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
}
