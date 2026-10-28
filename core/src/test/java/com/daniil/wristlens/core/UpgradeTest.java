package com.daniil.wristlens.core;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.time.LocalDateTime;import java.util.*;
public class UpgradeTest {
 @Test public void releaseBeforeStart(){HoldBurst h=new HoldBurst();h.stop("a");assertFalse(h.start("a",0,30));}
 @Test public void lostReleaseExpires(){HoldBurst h=new HoldBurst();assertTrue(h.start("a",0,30));h.renew("a",500);assertTrue(h.active(1600));assertFalse(h.active(1800));}
 @Test public void countLimit(){HoldBurst h=new HoldBurst();h.start("a",0,3);assertTrue(h.take(0));assertTrue(h.take(100));assertTrue(h.take(200));assertFalse(h.take(300));}
 @Test public void fiftyFramesAt250ms(){HoldBurst h=new HoldBurst();h.start("fifty",0,999);for(int i=0;i<50;i++){h.renew("fifty",i*250);assertTrue(h.take(i*250));}assertFalse(h.take(12500));}
 @Test public void timeLimit(){HoldBurst h=new HoldBurst();h.start("a",0,30);assertTrue(h.take(0));for(int i=1;i<60;i++)h.renew("a",i*1000);h.renew("a",60000);assertFalse(h.take(60000));}
 @Test public void timerDoesNotConsumeDuration(){HoldBurst h=new HoldBurst();h.start("a",0,30);for(int i=0;i<=60;i++)h.renew("a",i*1000);assertTrue(h.take(60000));assertTrue(h.active(61000));}
 @Test public void unrelatedRenewDoesNotKeepBurstAlive(){HoldBurst h=new HoldBurst();h.start("a",0,30);h.renew("b",1100);assertFalse(h.active(1300));}
 @Test public void rotatedScreenCap(){assertArrayEquals(new int[]{480,270},StreamSizing.fit(1920,1080,90,480,480,0));int[] s=StreamSizing.fit(4000,3000,90,320,480,0);assertTrue(s[1]<=320&&s[0]<=480);}
 @Test public void noUpscale(){assertArrayEquals(new int[]{160,120},StreamSizing.fit(160,120,0,480,480,0));}
 private static byte[] encoded(byte[] source)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();OriginalTransfer.send(new ByteArrayInputStream(source),new DataOutputStream(bytes),source.length,n->{});return bytes.toByteArray();}
 @Test public void originalMultiChunkExact()throws Exception{byte[] data=new byte[500000];new Random(42).nextBytes(data);ByteArrayOutputStream result=new ByteArrayOutputStream();assertEquals(data.length,OriginalTransfer.receive(new DataInputStream(new ByteArrayInputStream(encoded(data))),result,1000000,n->{}));assertArrayEquals(data,result.toByteArray());}
 @Test(expected=IOException.class) public void corruptedOriginalRejected()throws Exception{byte[] e=encoded(new byte[100]);e[20]=7;OriginalTransfer.receive(new DataInputStream(new ByteArrayInputStream(e)),new ByteArrayOutputStream(),10000,n->{});}
 @Test(expected=IOException.class) public void lowSpaceRejected()throws Exception{OriginalTransfer.receive(new DataInputStream(new ByteArrayInputStream(encoded(new byte[100]))),new ByteArrayOutputStream(),99,n->{});}
 @Test(expected=IOException.class) public void truncatedOriginalRejected()throws Exception{byte[] e=encoded(new byte[100]);OriginalTransfer.receive(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(e,e.length-1))),new ByteArrayOutputStream(),10000,n->{});}
 @Test public void filenameDefaultsAndCollision(){LocalDateTime d=LocalDateTime.of(2026,10,22,13,4,5);assertEquals("IMG_20261022_130405.jpg",FileNames.name(false,"standard",d,"jpg",0));assertEquals("VID_20261022_130405_001.mp4",FileNames.name(true,"standard",d,"mp4",1));assertEquals("IMG_2026-10-22_13-04-05.png",FileNames.name(false,"iso",d,"png",0));}
}
