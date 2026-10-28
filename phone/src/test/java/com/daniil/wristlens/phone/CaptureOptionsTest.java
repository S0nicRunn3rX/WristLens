package com.daniil.wristlens.phone;

import android.content.Context;
import android.graphics.*;
import com.daniil.wristlens.core.WatermarkSpec;
import com.daniil.wristlens.shared.Wire;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CaptureOptionsTest {
    private Context context;
    @Before public void before(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("capture",0).edit().clear().commit();}
    @Test public void settingsSurviveNewInstanceAndRejectInvalidValues()throws Exception{
        CaptureOptions a=new CaptureOptions(context);assertTrue(a.background());assertFalse(a.watermark().enabled());
        a.apply(Wire.object("key","background","value",false));a.apply(Wire.object("key","watermark","value",true));a.apply(Wire.object("key","corner","value","top_left"));
        CaptureOptions b=new CaptureOptions(context);assertFalse(b.background());assertTrue(b.watermark().enabled());assertEquals("top_left",b.watermark().corner());
        assertThrows(IllegalArgumentException.class,()->b.apply(Wire.object("key","corner","value","outside")));
        assertThrows(IllegalArgumentException.class,()->b.apply(Wire.object("key","background","value","false")));
        assertEquals("top_left",b.watermark().corner());assertFalse(b.background());
    }
    @Test public void emptyWatermarkCannotBeEnabled()throws Exception{
        CaptureOptions a=new CaptureOptions(context);a.apply(Wire.object("key","date","value",false));a.apply(Wire.object("key","time","value",false));
        assertThrows(IllegalArgumentException.class,()->a.apply(Wire.object("key","watermark","value",true)));
        assertFalse(a.watermark().enabled());a.apply(Wire.object("key","date","value",true));a.apply(Wire.object("key","watermark","value",true));
        assertThrows(IllegalArgumentException.class,()->a.apply(Wire.object("key","date","value",false)));assertTrue(a.watermark().date());
    }
    @Test public void nativeTextDrawsInsideEveryDisplayedCornerAfterRotation()throws Exception{
        for(int rotation:new int[]{0,90,180,270})for(String corner:WatermarkSpec.CORNERS){
            WatermarkSpec spec=new WatermarkSpec(true,corner,"white","medium",true,true,"dd.MM.yyyy","HH:mm:ss");
            try(WatermarkOverlay o=new WatermarkOverlay(spec,960,540,rotation)){
                assertTrue(o.update(1792782307000L));assertFalse(o.update(1792782307000L));
                int w=o.bitmap.getWidth(),h=o.bitmap.getHeight();float[] raw=new float[8];
                for(int i=0;i<4;i++){raw[2*i]=(o.vertices[2*i]+1)*480;raw[2*i+1]=(1-o.vertices[2*i+1])*270;}
                Matrix text=new Matrix();assertTrue(text.setPolyToPoly(new float[]{0,0,w,0,0,h,w,h},0,raw,0,4));
                Bitmap encoded=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(encoded);c.drawColor(0xff324453);c.drawBitmap(o.bitmap,text,null);
                Matrix r=new Matrix();r.postRotate(rotation);Bitmap displayed=Bitmap.createBitmap(encoded,0,0,960,540,r,true);
                int left=displayed.getWidth(),top=displayed.getHeight(),right=-1,bottom=-1,pixels=0;
                for(int y=0;y<displayed.getHeight();y++)for(int x=0;x<displayed.getWidth();x++)if(Color.red(displayed.getPixel(x,y))>230){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);pixels++;}
                assertTrue("text missing",pixels>40);assertTrue(left>0&&top>0&&right<displayed.getWidth()-1&&bottom<displayed.getHeight()-1);
                assertEquals(corner.endsWith("right"),(left+right)/2>displayed.getWidth()/2);
                assertEquals(corner.startsWith("bottom"),(top+bottom)/2>displayed.getHeight()/2);
                if(rotation==90){File dir=new File("build/ui-verification");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"watermark-"+corner+".png"))){displayed.compress(Bitmap.CompressFormat.PNG,100,out);}}
                if(displayed!=encoded)displayed.recycle();encoded.recycle();
            }
        }
    }
    @Test public void blackTextAndSizesAreRealPixels(){
        int old=0;for(String size:WatermarkSpec.SIZES){
            try(WatermarkOverlay o=new WatermarkOverlay(new WatermarkSpec(true,"bottom_right","black",size,true,true,"yyyy-MM-dd","HH:mm:ss"),1920,1080,0)){
                o.update(0);assertTrue(o.bitmap.getHeight()>old);old=o.bitmap.getHeight();boolean opaque=false;
                for(int y=0;y<o.bitmap.getHeight();y++)for(int x=0;x<o.bitmap.getWidth();x++){int pixel=o.bitmap.getPixel(x,y);if(Color.alpha(pixel)>200){assertEquals(0,Color.red(pixel));opaque=true;}}
                assertTrue(opaque);
            }
        }
    }
}
