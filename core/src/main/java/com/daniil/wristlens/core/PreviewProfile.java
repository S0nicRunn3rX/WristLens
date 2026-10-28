package com.daniil.wristlens.core;
/** Preview encoding only; recorder resolution/FPS are independent. */
public record PreviewProfile(String key,int preferredWidth,int jpegQuality,int intervalMs) {
    public static PreviewProfile of(String key){return switch(key){
        case "eco"->new PreviewProfile("eco",320,40,330);
        case "detail"->new PreviewProfile("detail",0,85,125);
        default->new PreviewProfile("auto",0,60,67);
    };}
}
