package com.daniil.wristlens.core;
import org.junit.Test;import static org.junit.Assert.*;
public class RecordingTimelineTest {
 @Test public void mutePreservesDuration(){RecordingTimeline t=new RecordingTimeline();t.start(100,false);t.mute(200,true);t.mute(300,false);t.stop(400);assertEquals(150,t.at(250).timeUs());assertTrue(t.at(250).muted());assertFalse(t.at(350).muted());assertEquals(299,t.at(399).timeUs());assertNull(t.at(400));}
 @Test public void pausesAreRemovedFromBothTracks(){RecordingTimeline t=new RecordingTimeline();t.start(100,false);t.pause(200);t.resume(400);t.stop(600);assertNull(t.at(250));assertEquals(150,t.at(450).timeUs());assertEquals(299,t.at(599).timeUs());}
 @Test public void lateBufferedSamplesKeepOriginalMuteState(){RecordingTimeline t=new RecordingTimeline();t.start(0,false);t.mute(1_000,true);t.mute(2_000,false);short[] input={100,100,100,100};var p=t.filter(0,input,1000);assertArrayEquals(new short[]{100,0,100,100},p.samples());assertEquals(0,p.timeUs());}
 @Test public void blockAcrossPauseTrimsOnlyPausedSamples(){RecordingTimeline t=new RecordingTimeline();t.start(0,false);t.pause(1000);t.resume(3000);var p=t.filter(0,new short[]{1,2,3,4,5},1000);assertArrayEquals(new short[]{1,4,5},p.samples());assertEquals(1000,t.at(3000).timeUs());}
 @Test public void muteDuringPauseAppliesOnResume(){RecordingTimeline t=new RecordingTimeline();t.start(0,false);t.pause(100);t.mute(200,true);t.resume(300);assertFalse(t.at(50).muted());assertTrue(t.at(350).muted());assertEquals(150,t.at(350).timeUs());}
 @Test public void repeatedPauseAndStopNeverAddTime(){RecordingTimeline t=new RecordingTimeline();t.start(10,true);t.pause(20);t.pause(100);t.resume(200);t.stop(210);t.stop(500);assertEquals(19,t.at(209).timeUs());assertNull(t.at(210));assertNull(t.at(9));}
}
