package com.daniil.wristlens.core;
public final class StreamSizing {
 public static int[] fit(int w,int h,int rotation,int screenW,int screenH,int preferred){
  int sw=Math.max(2,Math.min(2048,screenW)),sh=Math.max(2,Math.min(2048,screenH));
  if(rotation%180!=0){int t=sw;sw=sh;sh=t;}
  double f=Math.min(1,Math.min(sw/(double)w,sh/(double)h));if(preferred>0)f=Math.min(f,preferred/(double)Math.max(w,h));
  return new int[]{Math.max(2,((int)(w*f))&~1),Math.max(2,((int)(h*f))&~1)};
 }
}
