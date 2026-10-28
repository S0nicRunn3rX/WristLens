package com.daniil.wristlens.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

public class CaptureReleaseTest {
    private WatermarkSpec mark(boolean date,boolean time,String df,String tf){return new WatermarkSpec(true,"top_left","white","small",date,time,df,tf);}
    @Test public void timestampUsesPhoneZoneAndRollover(){
        long epoch=Instant.parse("2026-10-23T22:05:07Z").toEpochMilli();
        assertEquals("24.10.2026  01:05:07",mark(true,true,"dd.MM.yyyy","HH:mm:ss").text(epoch,ZoneId.of("Europe/Moscow"),Locale.US));
        assertEquals("2026-10-23",mark(true,false,"yyyy-MM-dd","HH:mm").text(epoch,ZoneOffset.UTC,Locale.US));
        assertEquals("10:05 PM",mark(false,true,"dd/MM/yyyy","hh:mm a").text(epoch,ZoneOffset.UTC,Locale.US));
    }
    @Test public void everyExposedFormatProducesText(){
        for(String df:WatermarkSpec.DATES)for(String tf:WatermarkSpec.TIMES)
            assertFalse(mark(true,true,df,tf).text(0,ZoneOffset.UTC,Locale.forLanguageTag("ru")).isBlank());
    }
    @Test public void enabledEmptyAndUnknownFormatsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->mark(false,false,"dd.MM.yyyy","HH:mm"));
        assertThrows(IllegalArgumentException.class,()->mark(true,true,"unbounded user input","HH:mm"));
    }
    @Test public void allRotationsRoundTripToDisplayedCoordinates(){
        int w=1920,h=1080;
        for(int rotation:new int[]{0,90,180,270}){
            int dw=rotation%180==0?w:h,dh=rotation%180==0?h:w;
            for(float x:new float[]{0,31,dw-31,dw})for(float y:new float[]{0,27,dh-27,dh}){
                float[] raw=WatermarkSpec.rawPoint(x,y,w,h,rotation);
                assertTrue(raw[0]>=0&&raw[0]<=w);assertTrue(raw[1]>=0&&raw[1]<=h);
                float[] display=switch(rotation){case 90->new float[]{h-raw[1],raw[0]};case 180->new float[]{w-raw[0],h-raw[1]};case 270->new float[]{raw[1],w-raw[0]};default->raw;};
                assertArrayEquals(new float[]{x,y},display,.001f);
            }
        }
    }
    @Test public void gestureStartsStopsAndNeverFiresDuringCountdownOrPendingCapture(){
        assertEquals("photo",CapturePolicy.primaryAction(true,false,false,false,0,false));
        assertEquals("record_start",CapturePolicy.primaryAction(true,true,false,false,0,false));
        assertEquals("record_stop",CapturePolicy.primaryAction(true,true,true,false,0,false));
        assertEquals("record_stop",CapturePolicy.primaryAction(true,false,true,false,0,false));
        assertEquals("",CapturePolicy.primaryAction(false,false,false,false,0,false));
        assertEquals("",CapturePolicy.primaryAction(true,true,false,true,0,false));
        assertEquals("",CapturePolicy.primaryAction(true,false,false,false,3,false));
        assertEquals("",CapturePolicy.primaryAction(true,false,false,false,0,true));
    }
    @Test public void backgroundPreservesOnlySinglePhotoAndVideoTimers(){
        for(String action:List.of("photo","video","hold","burst","")){
            assertFalse(CapturePolicy.keepTimer(false,action));
            assertEquals(action.equals("photo")||action.equals("video"),CapturePolicy.keepTimer(true,action));
        }
    }
    @Test public void lifecycleRejectsReorderedPauseAndOldActivity(){
        PresenceOrder p=new PresenceOrder();assertTrue(p.hello("watch-A",1));assertTrue(p.hidden("watch-A",2));
        assertFalse(p.hello("watch-A",1));assertTrue(p.hello("watch-A",4));assertFalse(p.hidden("watch-A",3));
        assertTrue(p.hello("watch-B",1));assertFalse(p.hidden("watch-A",9));assertFalse(p.hello("watch-A",10));
        assertTrue(p.hidden("watch-B",2));assertFalse(p.hidden("watch-B",2));assertTrue(p.hello("watch-B",3));
    }

}
