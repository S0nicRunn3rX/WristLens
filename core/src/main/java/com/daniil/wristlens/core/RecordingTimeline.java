package com.daniil.wristlens.core;
import java.util.*;
/** One monotonic capture clock for audio/video. Pauses remove time; mute preserves it. */
public final class RecordingTimeline {
 private record Span(long start,long end,long offset,boolean muted){}
 public record Point(long timeUs,boolean muted){}
 public record Pcm(long timeUs,short[] samples){}
 private final List<Span> spans=new ArrayList<>();private long start=-1,offset;private boolean muted,ended;
 public synchronized void start(long now,boolean mute){if(start>=0||!spans.isEmpty()||ended)throw new IllegalStateException();start=now;muted=mute;}
 private void close(long now){if(start>=0){long end=Math.max(start,now);spans.add(new Span(start,end,offset,muted));offset+=end-start;start=-1;}}
 public synchronized void pause(long now){close(now);}
 public synchronized void resume(long now){if(!ended&&start<0)start=now;}
 public synchronized void mute(long now,boolean value){if(value==muted)return;boolean active=start>=0;close(now);muted=value;if(active)start=now;}
 public synchronized void stop(long now){close(now);ended=true;}
 public synchronized Point at(long time){if(start>=0&&time>=start)return new Point(offset+time-start,muted);for(int i=spans.size()-1;i>=0;i--){Span s=spans.get(i);if(time>=s.start&&time<s.end)return new Point(s.offset+time-s.start,s.muted);}return null;}
 public synchronized Pcm filter(long time,short[] input,int rate){if(rate<=0)throw new IllegalArgumentException();short[] out=new short[input.length];int n=0;long first=0;for(int i=0;i<input.length;i++){Point p=at(time+i*1_000_000L/rate);if(p!=null){if(n==0)first=p.timeUs;out[n++]=p.muted?0:input[i];}}return new Pcm(first,Arrays.copyOf(out,n));}
}
