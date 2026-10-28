package com.daniil.wristlens.core;

public final class CameraMath {
    public static double clamp(double v,double min,double max) {
        if(!Double.isFinite(v)) throw new IllegalArgumentException("Non-finite value");
        return Math.max(min,Math.min(max,v));
    }
    /** Raw preview crop -> camera sensor crop, including different stream aspect ratios. */
    public static int[] focusRect(int left,int top,int width,int height,double aspect,double x,double y) {
        x=clamp(x,0,1); y=clamp(y,0,1);
        double w=width,h=height;
        if(w/h>aspect) w=h*aspect; else h=w/aspect;
        double l=left+(width-w)/2,t=top+(height-h)/2;
        int side=Math.max(2,(int)(Math.min(w,h)*0.10));
        int cx=(int)(l+w*x),cy=(int)(t+h*y);
        int px=(int)Math.max(left,Math.min(left+width-side,cx-side/2));
        int py=(int)Math.max(top,Math.min(top+height-side,cy-side/2));
        return new int[]{px,py,px+side,py+side};
    }
    public static int jpegOrientation(int sensor,int device,boolean front) {
        return (sensor+(front?-device:device)+360)%360;
    }
    public static int secondsLeft(long now,long deadline) {
        return (int)Math.max(0,(deadline-now+999)/1000);
    }
}
