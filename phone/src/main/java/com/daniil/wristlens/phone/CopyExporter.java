package com.daniil.wristlens.phone;
import android.content.Context;import android.graphics.*;import android.media.MediaMetadataRetriever;import android.net.Uri;import android.os.*;
import androidx.media3.common.*;import androidx.media3.effect.*;import androidx.media3.transformer.*;
import com.daniil.wristlens.core.*;import com.daniil.wristlens.shared.*;
import java.io.*;import java.util.List;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Temporary phone-side derivatives. The camera originals are only opened for reading. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class CopyExporter {
 private static final Semaphore VIDEO_SLOT=new Semaphore(1);
 record Prepared(Uri uri,String name,String mime,long bytes,File temporary) implements AutoCloseable {public void close(){if(temporary!=null)temporary.delete();}}
 static Prepared prepare(Context c,MediaFiles.Entry e,CopyProfile profile,CopyHandshake.Pulse heartbeat)throws Exception{
  if(profile.original())return new Prepared(e.uri(),e.name(),e.mime(),e.bytes(),null);
  boolean video=e.mime().startsWith("video/");File dir=new File(c.getCacheDir(),"watch-copies");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create copy cache");
  File[] old=dir.listFiles();if(old!=null)for(File f:old)if(f.lastModified()<System.currentTimeMillis()-86_400_000L)f.delete();
  File output=File.createTempFile("copy-",video?".mp4":".jpg",dir);boolean complete=false;
  try{heartbeat.run();if(video)video(c,e.uri(),output,profile,heartbeat);else photo(c,e.uri(),output,profile);
   if(output.length()<=0||output.length()>OriginalTransfer.MAX)throw new IOException(Strings.tr(com.daniil.wristlens.shared.R.string.copy_export_failed));
   complete=true;return new Prepared(Uri.fromFile(output),profile.copyName(e.name(),video),video?"video/mp4":"image/jpeg",output.length(),output);
  }finally{if(!complete)output.delete();}
 }
 static void photo(Context c,Uri source,File out,CopyProfile profile)throws IOException{
  Bitmap image=ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),source),(decoder,info,s)->{int[] size=profile.dimensions(info.getSize().getWidth(),info.getSize().getHeight(),false);decoder.setTargetSize(size[0],size[1]);decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));});
  try(OutputStream stream=new FileOutputStream(out)){if(!image.compress(Bitmap.CompressFormat.JPEG,profile.jpegQuality(),stream))throw new IOException(Strings.tr(com.daniil.wristlens.shared.R.string.copy_export_failed));}finally{image.recycle();}
 }
 static DefaultEncoderFactory encoder(Context c,CopyProfile p){return new DefaultEncoderFactory.Builder(c).setEnableFallback(true).setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(p.videoBitrate()).build()).setRequestedAudioEncoderSettings(new AudioEncoderSettings.Builder().setBitrate(96_000).build()).build();}
 private static void video(Context c,Uri source,File output,CopyProfile p,CopyHandshake.Pulse heartbeat)throws Exception{
  long deadline=SystemClock.elapsedRealtime()+TimeUnit.HOURS.toMillis(6);boolean slot=false;
  Handler main=new Handler(Looper.getMainLooper());AtomicReference<Transformer> running=new AtomicReference<>();AtomicBoolean cancelled=new AtomicBoolean();CompletableFuture<Void> done=new CompletableFuture<>();
  try{while(!(slot=VIDEO_SLOT.tryAcquire(2,TimeUnit.SECONDS))){heartbeat.run();if(SystemClock.elapsedRealtime()>deadline)throw new TimeoutException();}
   int width,height,rotation;try(MediaMetadataRetriever meta=new MediaMetadataRetriever()){meta.setDataSource(c,source);width=Integer.parseInt(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));height=Integer.parseInt(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));String r=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);rotation=r==null?0:Integer.parseInt(r);}
   if(rotation==90||rotation==270){int t=width;width=height;height=t;}int[] size=p.dimensions(width,height,true);
   main.post(()->{if(cancelled.get())return;try{
    Transformer transformer=new Transformer.Builder(c).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder(c,p)).addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition composition,ExportResult result){done.complete(null);}
     @Override public void onError(Composition composition,ExportResult result,ExportException error){done.completeExceptionally(error);}
    }).build();running.set(transformer);
    EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(source)).setEffects(new Effects(List.of(),List.of(Presentation.createForWidthAndHeight(size[0],size[1],Presentation.LAYOUT_SCALE_TO_FIT),FrameDropEffect.createDefaultFrameDropEffect(30)))).build();
    transformer.start(item,output.getAbsolutePath());
   }catch(Exception e){done.completeExceptionally(e);}});
   for(;;){try{done.get(2,TimeUnit.SECONDS);break;}catch(TimeoutException wait){heartbeat.run();if(new StatFs(output.getParent()).getAvailableBytes()<16*1024*1024L||output.length()>OriginalTransfer.MAX)throw new IOException(Strings.tr(com.daniil.wristlens.shared.R.string.copy_export_space));if(SystemClock.elapsedRealtime()>deadline)throw wait;}}
  }finally{cancelled.set(true);CompletableFuture<Void> stopped=new CompletableFuture<>();main.post(()->{try{Transformer t=running.getAndSet(null);if(t!=null)t.cancel();}finally{stopped.complete(null);}});try{stopped.get(5,TimeUnit.SECONDS);}catch(Exception ignored){}if(slot)VIDEO_SLOT.release();}
 }
}
