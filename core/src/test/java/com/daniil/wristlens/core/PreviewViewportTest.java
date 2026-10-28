package com.daniil.wristlens.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewViewportTest {
    private PreviewViewport round(double w,double h) {
        PreviewViewport v=new PreviewViewport();v.geometry(480,480,w,h,true,true);return v;
    }
    @Test public void defaultFillsCircleAndCentersFourByThreeImage() {
        PreviewViewport v=round(640,480);
        assertEquals(640,v.displayedWidth(),1e-7);assertEquals(480,v.displayedHeight(),1e-7);
        assertEquals(1,v.zoom(),0);assertEquals(0,v.x(),0);assertEquals(0,v.y(),0);
    }
    @Test public void minimumFitsEveryCornerInsideRoundScreensWithoutDistortion() {
        for(int screen:new int[]{384,416,480})for(int[] frame:new int[][]{{640,480},{480,640},{1920,1080},{1080,1920},{720,720},{2400,1080}}) {
            PreviewViewport v=new PreviewViewport();v.geometry(screen,screen,frame[0],frame[1],true,true);
            v.scaleBy(.0001,screen*.25,screen*.7);v.pan(10000,-10000);
            assertEquals(screen/2.,Math.hypot(v.displayedWidth()/2,v.displayedHeight()/2),1e-7);
            assertEquals(frame[0]/(double)frame[1],v.displayedWidth()/v.displayedHeight(),1e-7);
            assertEquals(0,v.x(),0);assertEquals(0,v.y(),0);
        }
    }
    @Test public void fourByThreeFitsAs384By288On480Circle() {
        PreviewViewport v=round(640,480);v.scaleBy(.01,240,240);
        assertEquals(.6,v.zoom(),1e-7);assertEquals(384,v.displayedWidth(),1e-7);assertEquals(288,v.displayedHeight(),1e-7);
    }
    @Test public void fullScreenCropCanMoveToEitherEdgeButCannotExposeBlankSpace() {
        PreviewViewport v=round(640,480);v.pan(10000,10000);
        assertEquals(80,v.x(),1e-7);assertEquals(0,v.y(),0);
        v.pan(-10000,-10000);assertEquals(-80,v.x(),1e-7);assertEquals(0,v.y(),0);
        PreviewViewport portrait=round(480,640);portrait.pan(10000,10000);
        assertEquals(0,portrait.x(),0);assertEquals(80,portrait.y(),1e-7);
    }
    @Test public void zoomLimitAndPanLimitAreFinite() {
        PreviewViewport v=round(640,480);v.scaleBy(1000,240,240);v.pan(-10000,10000);
        assertEquals(3,v.zoom(),0);assertEquals(-720,v.x(),1e-7);assertEquals(480,v.y(),1e-7);
    }
    @Test public void pinchKeepsOffCenterImagePointUnderFingers() {
        PreviewViewport v=round(640,480);v.scaleBy(2,280,200);
        assertEquals(-40,v.x(),1e-7);assertEquals(40,v.y(),1e-7);
        // Raw frame point (360,200), originally under (280,200), stays at (280,200).
        assertEquals(280,240+v.x()+(360-320)*v.scale(),1e-7);
        assertEquals(200,240+v.y()+(200-240)*v.scale(),1e-7);
    }
    @Test public void resetReturnsOriginalViewAfterFitAndAfterZoomAndPan() {
        PreviewViewport v=round(640,480);
        for(double factor:new double[]{.01,3}) {
            v.scaleBy(factor,170,280);v.pan(66,-80);v.reset();v.geometry(480,480,640,480,true,true);
            assertEquals(1,v.zoom(),0);assertEquals(0,v.x(),0);assertEquals(0,v.y(),0);
        }
    }
    @Test public void incomingFramesAndResolutionChangesKeepView() {
        PreviewViewport v=round(640,480);v.scaleBy(2,240,240);v.pan(40,-30);
        for(int i=0;i<100;i++)assertFalse(v.geometry(480,480,i%2==0?320:640,i%2==0?240:480,true,true));
        assertEquals(2,v.zoom(),0);assertEquals(40,v.x(),1e-7);assertEquals(-30,v.y(),1e-7);
        v.geometry(384,384,640,480,true,true);assertEquals(32,v.x(),1e-7);assertEquals(-24,v.y(),1e-7);
    }
    @Test public void aspectChangeResetsAndFitSurvivesResolutionChange() {
        PreviewViewport v=round(640,480);v.scaleBy(.01,240,240);
        assertFalse(v.geometry(384,384,320,240,true,true));assertEquals(v.minimumZoom(),v.zoom(),0);
        assertTrue(v.geometry(384,384,320,180,true,true));assertEquals(1,v.zoom(),0);
        assertEquals(0,v.x(),0);assertEquals(0,v.y(),0);
    }
    @Test public void rectangularScreensUseRectangleContainAndPhoneDefaultRemainsFit() {
        PreviewViewport v=new PreviewViewport();v.geometry(480,800,640,480,false,false);
        assertEquals(480,v.displayedWidth(),1e-7);assertEquals(360,v.displayedHeight(),1e-7);
        v.geometry(480,480,640,480,false,true);v.scaleBy(.01,240,240);
        assertEquals(480,v.displayedWidth(),1e-7);assertEquals(360,v.displayedHeight(),1e-7);
    }
    @Test public void invalidInputsDoNotPoisonFutureFrames() {
        PreviewViewport v=new PreviewViewport();v.pan(1,1);v.scaleBy(2,1,1);assertFalse(v.geometry(0,0,640,480,true,true));
        v.geometry(480,480,640,480,true,true);v.scaleBy(Double.NaN,240,240);v.scaleBy(-1,240,240);v.pan(Double.NaN,20);
        v.geometry(480,480,Double.POSITIVE_INFINITY,480,true,true);
        assertEquals(1,v.zoom(),0);assertEquals(0,v.x(),0);assertEquals(0,v.y(),0);
    }
}
