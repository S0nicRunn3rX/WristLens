package com.daniil.wristlens.phone;
import android.app.Service;import android.content.*;import android.os.*;import com.google.android.gms.wearable.*;import com.google.android.gms.tasks.Tasks;import com.daniil.wristlens.core.*;import com.daniil.wristlens.shared.*;import org.json.JSONObject;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;
/** Bound transfer service: prepares a disposable copy, streams it, then removes the cache. */
public final class MediaTransferService extends Service {
 private final ExecutorService io=Executors.newFixedThreadPool(2);private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();private final Set<ChannelClient.Channel> active=ConcurrentHashMap.newKeySet();
 final class Local extends Binder{void send(ChannelClient.Channel channel,String value,Runnable done){io.execute(()->transfer(channel,value,done));}}
 static void start(Context original,ChannelClient.Channel ch,String value){Context c=original.getApplicationContext();ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){((Local)b).send(ch,value,()->c.unbindService(this));}public void onServiceDisconnected(ComponentName n){Wearable.getChannelClient(c).close(ch);}};if(!c.bindService(new Intent(c,MediaTransferService.class),connection,Context.BIND_AUTO_CREATE))Wearable.getChannelClient(c).close(ch);}
 @Override public IBinder onBind(Intent i){return new Local();}
 private void transfer(ChannelClient.Channel channel,String value,Runnable done){
  ChannelClient client=Wearable.getChannelClient(this);active.add(channel);PowerManager.WakeLock wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WristLens:copy");wake.acquire(TimeUnit.HOURS.toMillis(6));AtomicLong progress=new AtomicLong(SystemClock.elapsedRealtime());
  ScheduledFuture<?> watchdog=timer.scheduleWithFixedDelay(()->{if(SystemClock.elapsedRealtime()-progress.get()>30000)client.close(channel);},5,5,TimeUnit.SECONDS);
  CopyExporter.Prepared prepared=null;
  try(DataOutputStream out=new DataOutputStream(Tasks.await(client.getOutputStream(channel),10,TimeUnit.SECONDS));DataInputStream ack=new DataInputStream(Tasks.await(client.getInputStream(channel),10,TimeUnit.SECONDS))){
   boolean version2=value.startsWith("{");boolean ready=false;
   try{JSONObject options=version2?new JSONObject(value):null;MediaFiles.Entry e=new MediaFiles(this).find(version2?options.getString("id"):value);
    CopyProfile profile=CopyProfile.of(version2?options.optString(e.mime().startsWith("video/")?"video":"photo","original"):"original");
    prepared=CopyExporter.prepare(this,e,profile,()->{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(version2)CopyHandshake.waiting(out);progress.set(SystemClock.elapsedRealtime());});
    if(version2)CopyHandshake.ready(out,new CopyHandshake.Metadata(e.id(),prepared.name(),prepared.mime(),prepared.bytes()));ready=true;
    try(InputStream in=getContentResolver().openInputStream(prepared.uri())){if(in==null)throw new IOException(Strings.tr(com.daniil.wristlens.shared.R.string.wl_119));OriginalTransfer.send(in,out,prepared.bytes(),n->progress.set(SystemClock.elapsedRealtime()));if(ack.readInt()!=0x4f4b)throw new IOException(Strings.tr(com.daniil.wristlens.shared.R.string.wl_120));}
   }catch(Exception e){if(version2&&!ready)try{CopyHandshake.error(out,Strings.tr(com.daniil.wristlens.shared.R.string.copy_export_failed));}catch(IOException ignored){}throw e;}
  }catch(Exception e){android.util.Log.w("WristLens","Copy transfer",e);}finally{if(prepared!=null)prepared.close();watchdog.cancel(false);client.close(channel);active.remove(channel);if(wake.isHeld())wake.release();done.run();}
 }
 @Override public void onDestroy(){for(ChannelClient.Channel c:active)Wearable.getChannelClient(this).close(c);io.shutdownNow();timer.shutdownNow();super.onDestroy();}
}
