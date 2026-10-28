package com.daniil.wristlens.phone;

import android.graphics.*;
import com.daniil.wristlens.core.WatermarkSpec;
import java.time.ZoneId;
import java.util.Locale;

/** Small premultiplied text texture; no full-resolution CPU video copies. */
final class WatermarkOverlay implements AutoCloseable {
    private final WatermarkSpec spec;
    private final int width,height,rotation;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text="";
    Bitmap bitmap;
    final float[] vertices=new float[8];
    WatermarkOverlay(WatermarkSpec spec,int width,int height,int rotation){
        this.spec=spec;this.width=width;this.height=height;this.rotation=rotation;
        paint.setTypeface(Typeface.DEFAULT);paint.setColor(spec.color().equals("black")?Color.BLACK:Color.WHITE);
    }
    boolean update(long epoch){
        String next=spec.text(epoch,ZoneId.systemDefault(),Locale.getDefault());if(bitmap!=null&&next.equals(text))return false;
        text=next;int dw=rotation%180==0?width:height,dh=rotation%180==0?height:width;
        float edge=Math.min(dw,dh),margin=Math.max(2,edge*.025f);
        paint.setTextSize(Math.max(8,edge*spec.fontFraction()));
        float available=dw-2*margin-4;if(paint.measureText(text)>available)paint.setTextSize(paint.getTextSize()*available/paint.measureText(text));
        Paint.FontMetrics metrics=paint.getFontMetrics();
        int w=Math.max(1,(int)Math.ceil(paint.measureText(text))+4),h=Math.max(1,(int)Math.ceil(metrics.descent-metrics.ascent)+4);
        if(bitmap!=null)bitmap.recycle();bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawText(text,2,2-metrics.ascent,paint);
        float left=spec.corner().endsWith("right")?dw-margin-w:margin,top=spec.corner().startsWith("bottom")?dh-margin-h:margin;
        float[] xy={left,top,left+w,top,left,top+h,left+w,top+h};
        for(int i=0;i<4;i++){float[] raw=WatermarkSpec.rawPoint(xy[i*2],xy[i*2+1],width,height,rotation);vertices[i*2]=2*raw[0]/width-1;vertices[i*2+1]=1-2*raw[1]/height;}
        return true;
    }
    @Override public void close(){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
