package com.daniil.wristlens.wear;
import android.content.Context;import android.media.*;import android.os.*;import com.daniil.wristlens.core.AudioPacket;import com.daniil.wristlens.shared.*;import com.google.android.gms.tasks.Tasks;import com.google.android.gms.wearable.*;import java.io.*;import java.util.concurrent.*;import java.util.function.Consumer;
/** Bounded, acknowledged audio stream. Playback lives only while the watch app is active. */
final class LiveAudio {
 private final Context c;private final Consumer<String> notice;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();private final ChannelClient channels;
 private volatile int generation;private volatile ChannelClient.Channel channel;private volatile AudioTrack track;private volatile long lastPacket;private long retry;private boolean opening,focusLost;private String key="";private AudioFocusRequest focus;
 LiveAudio(Context c,Consumer<String> notice){this.c=c;this.notice=notice;channels=Wearable.getChannelClient(c);}
 void focusReturned(){focusLost=false;}
 void update(boolean active,boolean enabled,String node,String session){if(!active||!enabled||node.isEmpty()||session.isEmpty()){stop();focusLost=false;return;}String next=node+":"+session;if(!key.equals(next)){stop();key=next;}
  if(channel!=null&&lastPacket>0&&SystemClock.elapsedRealtime()-lastPacket>4000)stop();
  if(opening||channel!=null||focusLost||SystemClock.elapsedRealtime()<retry)return;
  AudioManager am=c.getSystemService(AudioManager.class);AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(change->{if(change<0){focusLost=true;stop();}},main).build();if(am.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){focusLost=true;stop();return;}
  opening=true;int token=++generation;worker.execute(()->receive(node,session,token,attrs));
 }
 private void receive(String node,String session,int token,AudioAttributes attrs){ChannelClient.Channel ch=null;AudioTrack output=null;try{
  ch=Tasks.await(channels.openChannel(node,Wire.AUDIO+session),10,TimeUnit.SECONDS);if(token!=generation)return;channel=ch;lastPacket=SystemClock.elapsedRealtime();
  output=new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AudioPacket.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(AudioTrack.getMinBufferSize(AudioPacket.RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),AudioPacket.RATE*2*160/1000)).setTransferMode(AudioTrack.MODE_STREAM).build();track=output;output.play();
  try(DataInputStream in=new DataInputStream(Tasks.await(channels.getInputStream(ch),10,TimeUnit.SECONDS));DataOutputStream ack=new DataOutputStream(Tasks.await(channels.getOutputStream(ch),10,TimeUnit.SECONDS))){long previous=-1;while(token==generation){AudioPacket.Frame f=AudioPacket.read(in);if(f.sequence()<=previous)throw new IOException("Old audio packet");previous=f.sequence();lastPacket=SystemClock.elapsedRealtime();int offset=0;while(offset<f.pcm().length&&token==generation){int n=output.write(f.pcm(),offset,f.pcm().length-offset,AudioTrack.WRITE_BLOCKING);if(n<=0)throw new IOException("Audio playback failed");offset+=n;}if(token!=generation)break;ack.writeLong(previous);ack.flush();}}
 }catch(Exception e){if(token==generation)main.post(()->{if(token==generation)notice.accept(Strings.tr(com.daniil.wristlens.shared.R.string.audio_unavailable));});}
 finally{if(ch!=null)channels.close(ch);if(output!=null){try{output.stop();}catch(Exception ignored){}output.release();}main.post(()->{if(token==generation){channel=null;track=null;opening=false;retry=SystemClock.elapsedRealtime()+3000;abandon();}});}}
 private void abandon(){if(focus!=null){c.getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);focus=null;}}
 void stop(){generation++;opening=false;key="";ChannelClient.Channel ch=channel;channel=null;if(ch!=null)channels.close(ch);AudioTrack t=track;track=null;if(t!=null)try{t.pause();t.flush();}catch(Exception ignored){}abandon();retry=SystemClock.elapsedRealtime()+500;}
 void close(){stop();worker.shutdownNow();}
}
