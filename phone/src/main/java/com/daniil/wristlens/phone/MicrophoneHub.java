package com.daniil.wristlens.phone;
import android.Manifest;import android.content.*;import android.content.pm.PackageManager;import android.media.*;import java.util.*;import java.util.concurrent.*;
/** One phone microphone, shared by recording and monitoring. Never changes system mute. */
final class MicrophoneHub {
 interface Sink {void pcm(long timeUs,short[] pcm);void error(Exception e);}
 static final int RATE=48000;private final Context context;private final Map<Object,Sink> sinks=new ConcurrentHashMap<>();private AudioRecord record;private int generation;
 MicrophoneHub(Context c){context=c;}
 synchronized void add(Object key,Sink sink)throws Exception{
  if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Microphone permission required");
  sinks.put(key,sink);if(record!=null)return;
  AudioRecord next=null;
  try{int size=Math.max(AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),RATE*2/5);next=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(next.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone unavailable");next.startRecording();record=next;int token=++generation;AudioRecord source=next;new Thread(()->read(source,token),"microphone").start();}
  catch(Exception e){sinks.remove(key);if(next!=null)next.release();throw e;}
 }
 private void read(AudioRecord source,int token){long frames=0;AudioTimestamp stamp=new AudioTimestamp();try{while(current(source,token)){short[] pcm=new short[960];int n=source.read(pcm,0,pcm.length,AudioRecord.READ_BLOCKING);if(n<=0)throw new IllegalStateException("Microphone read failed: "+n);long time=System.nanoTime()/1000-n*1_000_000L/RATE;if(source.getTimestamp(stamp,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS&&stamp.nanoTime>0)time=stamp.nanoTime/1000+(frames-stamp.framePosition)*1_000_000L/RATE;frames+=n;short[] data=n==pcm.length?pcm:Arrays.copyOf(pcm,n);for(Sink sink:sinks.values())sink.pcm(time,data);}}
  catch(Exception e){if(current(source,token))for(Sink sink:sinks.values())sink.error(e);}
 }
 private synchronized boolean current(AudioRecord source,int token){return source==record&&token==generation;}
 synchronized void remove(Object key){sinks.remove(key);if(sinks.isEmpty())stop();}
 private void stop(){generation++;AudioRecord old=record;record=null;if(old!=null){try{old.stop();}catch(Exception ignored){}old.release();}}
 synchronized void close(){sinks.clear();stop();}
}
