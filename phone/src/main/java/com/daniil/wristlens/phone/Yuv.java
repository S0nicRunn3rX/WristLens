package com.daniil.wristlens.phone;
import android.media.Image;
import android.graphics.*;
import java.io.*;
import java.nio.ByteBuffer;

final class Yuv {
    /** Handles padded rows and both planar/interleaved chroma; never assumes rowStride == width. */
    static byte[] nv21(Image image) {
        int w=image.getWidth(),h=image.getHeight(); byte[] out=new byte[w*h*3/2];
        Image.Plane[] p=image.getPlanes();
        copy(p[0],w,h,out,0,1);
        copy(p[2],w/2,h/2,out,w*h,2);copy(p[1],w/2,h/2,out,w*h+1,2);
        return out;
    }
    private static void copy(Image.Plane p,int w,int h,byte[] out,int offset,int step) {
        ByteBuffer b=p.getBuffer().duplicate();int base=b.position(),row=p.getRowStride(),pixel=p.getPixelStride();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)out[offset+(y*w+x)*step]=b.get(base+y*row+x*pixel);
    }
    static byte[] jpeg(byte[] nv21,int w,int h,int width,int quality) {
        int nw=Math.min(w,width)&~1,nh=(h*nw/w)&~1;
        byte[] scaled=nv21;
        if(nw<w) {
            scaled=new byte[nw*nh*3/2];
            for(int y=0;y<nh;y++)for(int x=0;x<nw;x++)scaled[y*nw+x]=nv21[(y*h/nh)*w+x*w/nw];
            for(int y=0;y<nh/2;y++)for(int x=0;x<nw/2;x++){
                int src=w*h+(y*(h/2)/(nh/2))*w+(x*(w/2)/(nw/2))*2,dst=nw*nh+y*nw+x*2;
                scaled[dst]=nv21[src];scaled[dst+1]=nv21[src+1];
            }
        } else {nw=w;nh=h;}
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        new YuvImage(scaled,ImageFormat.NV21,nw,nh,null).compressToJpeg(new Rect(0,0,nw,nh),quality,out);
        return out.toByteArray();
    }
}
