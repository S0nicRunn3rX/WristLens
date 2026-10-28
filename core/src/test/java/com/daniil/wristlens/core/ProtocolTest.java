package com.daniil.wristlens.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class ProtocolTest {
    @Test public void frameRoundTrip() throws Exception {
        var bytes=new ByteArrayOutputStream();
        FrameCodec.write(new DataOutputStream(bytes),new FrameCodec.Frame(42,320,240,90,true,new byte[]{1,2,3}));
        var f=FrameCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(42,f.sequence()); assertEquals(90,f.rotation()); assertTrue(f.mirror());
        assertArrayEquals(new byte[]{1,2,3},f.jpeg());
    }
    @Test(expected=IOException.class) public void rejectsOversizedFrame() throws Exception {
        FrameCodec.write(new DataOutputStream(new ByteArrayOutputStream()),new FrameCodec.Frame(1,320,240,0,false,new byte[FrameCodec.MAX_BYTES+1]));
    }
    @Test(expected=IOException.class) public void rejectsTruncation() throws Exception {
        var b=new ByteArrayOutputStream();
        FrameCodec.write(new DataOutputStream(b),new FrameCodec.Frame(1,320,240,0,false,new byte[30]));
        byte[] raw=b.toByteArray();
        FrameCodec.read(new DataInputStream(new ByteArrayInputStream(java.util.Arrays.copyOf(raw,raw.length-1))));
    }
    @Test public void sensorMappingAccountsForStreamCrop() {
        assertArrayEquals(new int[]{1888,1388,2113,1613},CameraMath.focusRect(0,0,4000,3000,16.0/9,0.5,0.5));
        var edge=CameraMath.focusRect(100,200,2000,1500,4.0/3,1,1);
        assertTrue(edge[2]<=2100); assertTrue(edge[3]<=1700);
    }
    @Test public void orientationAndCountdown() {
        assertEquals(0,CameraMath.jpegOrientation(90,90,true));
        assertEquals(180,CameraMath.jpegOrientation(90,90,false));
        assertEquals(1,CameraMath.secondsLeft(1999,2000));
        assertEquals(0,CameraMath.secondsLeft(2001,2000));
    }
    @Test public void retriesAreRememberedAndBounded() {
        var ledger=new CommandLedger(); ledger.put("photo-1","pending");
        assertTrue(ledger.contains("photo-1")); ledger.put("photo-1","saved");
        assertEquals("saved",ledger.get("photo-1"));
        for(int i=0;i<300;i++) ledger.put("id-"+i,"ok");
        assertFalse(ledger.contains("photo-1")); assertTrue(ledger.contains("id-299"));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidZoomRejected() { CameraMath.clamp(Double.NaN,1,10); }
}
