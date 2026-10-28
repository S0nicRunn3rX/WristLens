package com.daniil.wristlens.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class IdleSessionTest {
    @Test public void expiresAtOneFullMinuteButNotBefore(){IdleSession s=new IdleSession();assertFalse(s.update(1_000,false,false));assertFalse(s.update(60_999,false,false));assertTrue(s.update(61_000,false,false));}
    @Test public void foregroundWatchIncludingMenusNeverExpires(){IdleSession s=new IdleSession();for(long now=0;now<=180_000;now+=1_000)assertFalse(s.update(now,true,false));assertFalse(s.update(181_000,false,false));assertFalse(s.update(240_999,false,false));assertTrue(s.update(241_000,false,false));}
    @Test public void foregroundReturnResetsTheWholeMinute(){IdleSession s=new IdleSession();s.update(0,false,false);s.update(59_000,true,false);assertFalse(s.update(60_000,false,false));assertFalse(s.update(119_999,false,false));assertTrue(s.update(120_000,false,false));}
    @Test public void longBackgroundCaptureIsProtectedAndRestartsIdleMinute(){IdleSession s=new IdleSession();s.update(0,false,false);for(long now=30_000;now<=600_000;now+=1_000)assertFalse(s.update(now,false,true));assertFalse(s.update(601_000,false,false));assertFalse(s.update(660_999,false,false));assertTrue(s.update(661_000,false,false));}
    @Test public void newCaptureAtDeadlinePreventsShutdown(){IdleSession s=new IdleSession();s.update(0,false,false);assertFalse(s.update(60_000,false,true));assertFalse(s.update(61_000,false,false));assertFalse(s.update(120_999,false,false));assertTrue(s.update(121_000,false,false));}
}
