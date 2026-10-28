package com.daniil.wristlens.phone;
import com.daniil.wristlens.core.IdleSession;
import com.daniil.wristlens.shared.Wire;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class SessionIdleTest {
    @Test public void everyCapturePhaseProtectsSessionForLongerThanOneMinute(){
        for(JSONObject state:new JSONObject[]{Wire.object("recording",true),Wire.object("paused",true),Wire.object("busy",true),Wire.object("holding",true),Wire.object("countdown",1),Wire.object("timerAction","photo"),Wire.object("timerAction","video"),Wire.object("burstRemaining",1)}){
            assertTrue("Must protect "+state,CameraService.captureInProgress(state));IdleSession idle=new IdleSession();idle.update(0,false,false);
            assertFalse(idle.update(60_000,false,CameraService.captureInProgress(state)));
            assertFalse(idle.update(180_000,false,CameraService.captureInProgress(state)));
            assertFalse(idle.update(181_000,false,false));assertFalse(idle.update(240_999,false,false));assertTrue(idle.update(241_000,false,false));
        }
    }
    @Test public void configuredTimerDoesNotCountAsRunningTimer(){JSONObject state=Wire.object("phase","ready","photoTimer",60,"videoTimer",60,"countdown",0,"timerAction","","recording",false,"paused",false,"busy",false,"holding",false,"burstRemaining",0);assertFalse(CameraService.captureInProgress(state));}
}
