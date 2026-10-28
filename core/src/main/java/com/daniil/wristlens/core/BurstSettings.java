package com.daniil.wristlens.core;

public final class BurstSettings {
    public static final int DEFAULT_INTERVAL_MS=200;
    public static final int[] INTERVALS={200,250,500,1000,2000,5000};
    public static int interval(int requested){return Math.max(DEFAULT_INTERVAL_MS,Math.min(5000,requested));}
    private BurstSettings(){}
}
