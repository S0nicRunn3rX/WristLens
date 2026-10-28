package com.daniil.wristlens.core;

/** Continuous time spent with both the watch absent and the camera idle. Monotonic milliseconds. */
public final class IdleSession {
    public static final long TIMEOUT_MS=60_000;
    private long idleSince=-1;
    public boolean update(long now,boolean watchActive,boolean captureActive){
        if(watchActive||captureActive){idleSince=-1;return false;}
        if(idleSince<0||now<idleSince)idleSince=now;
        return now-idleSince>=TIMEOUT_MS;
    }
}
