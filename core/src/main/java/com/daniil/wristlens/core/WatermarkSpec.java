package com.daniil.wristlens.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Validated, platform-independent layout of the timestamp in the displayed video. */
public record WatermarkSpec(boolean enabled, String corner, String color, String size,
                            boolean date, boolean time, String dateFormat, String timeFormat) {
    public static final List<String> CORNERS=List.of("top_left","top_right","bottom_left","bottom_right");
    public static final List<String> COLORS=List.of("white","black");
    public static final List<String> SIZES=List.of("small","medium","large");
    public static final List<String> DATES=List.of("dd.MM.yyyy","yyyy-MM-dd","dd/MM/yyyy","MM/dd/yyyy");
    public static final List<String> TIMES=List.of("HH:mm:ss","HH:mm","hh:mm:ss a","hh:mm a");
    public WatermarkSpec {
        if(!CORNERS.contains(corner)||!COLORS.contains(color)||!SIZES.contains(size)
                ||!DATES.contains(dateFormat)||!TIMES.contains(timeFormat))throw new IllegalArgumentException(CoreText.choose("Unknown watermark format","Неизвестный формат водяного знака"));
        if(enabled&&!date&&!time)throw new IllegalArgumentException(CoreText.choose("Choose date and/or time","Выберите дату и/или время"));
    }
    public String text(long epochMillis, ZoneId zone, Locale locale) {
        String pattern=(date?dateFormat:"")+(date&&time?"  ":"")+(time?timeFormat:"");
        return DateTimeFormatter.ofPattern(pattern,locale).withZone(zone).format(Instant.ofEpochMilli(epochMillis));
    }
    public float fontFraction(){return switch(size){case "small"->.022f;case "large"->.045f;default->.032f;};}
    /** Coordinates use a top-left origin. Metadata rotates raw video clockwise. */
    public static float[] rawPoint(float x,float y,int width,int height,int rotation){
        return switch(Math.floorMod(rotation,360)){
            case 0->new float[]{x,y};case 90->new float[]{y,height-x};
            case 180->new float[]{width-x,height-y};case 270->new float[]{width-y,x};
            default->throw new IllegalArgumentException("Rotation must be a quarter turn");
        };
    }
}
