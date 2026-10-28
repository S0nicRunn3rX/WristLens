package com.daniil.wristlens.phone;
import android.content.*;import android.content.pm.ProviderInfo;import android.database.*;import android.database.sqlite.*;import android.net.Uri;import android.os.*;import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import org.robolectric.util.ReflectionHelpers;import java.io.*;import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaJournalTest {
 Context context;MediaFiles files;
 public static class Store extends ContentProvider {
  Map<String,ContentValues> rows=new HashMap<>();Map<String,File> paths=new HashMap<>();int next;
  public boolean onCreate(){return true;}public String getType(Uri u){return "image/jpeg";}
  public Uri insert(Uri u,ContentValues v){Uri result=u.buildUpon().appendPath(""+(++next)).build();rows.put(result.toString(),new ContentValues(v));return result;}
  public int update(Uri u,ContentValues v,String s,String[] args){ContentValues old=rows.get(u.toString());if(old==null)return 0;old.putAll(v);return 1;}
  public int delete(Uri u,String s,String[] args){return rows.remove(u.toString())==null?0:1;}
  public Cursor query(Uri u,String[] projection,String s,String[] args,String sort){MatrixCursor c=new MatrixCursor(projection);ContentValues row=rows.get(u.toString());if(row!=null){Object[] values=new Object[projection.length];for(int i=0;i<values.length;i++)values[i]=projection[i].equals("_size")?128L:row.get(projection[i]);c.addRow(values);}return c;}
  public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{try{File f=paths.get(u.toString());if(f==null){f=File.createTempFile("media-",".bin",getContext().getCacheDir());paths.put(u.toString(),f);}return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_WRITE);}catch(IOException e){throw new FileNotFoundException(e.toString());}}
 }
 @Before public void before(){context=RuntimeEnvironment.getApplication();SQLiteDatabase old=ReflectionHelpers.getStaticField(MediaFiles.class,"database");if(old!=null)old.close();ReflectionHelpers.setStaticField(MediaFiles.class,"database",null);context.deleteDatabase("gallery.db");context.getSharedPreferences("files",0).edit().clear().putBoolean("migrated",true).commit();Store store=new Store();ProviderInfo info=new ProviderInfo();info.authority="media";info.exported=true;store.attachInfo(context,info);ShadowContentResolver.registerProviderInternal("media",store);files=new MediaFiles(context);}
 @After public void after(){SQLiteDatabase old=ReflectionHelpers.getStaticField(MediaFiles.class,"database");if(old!=null)old.close();ReflectionHelpers.setStaticField(MediaFiles.class,"database",null);}
 @Test public void completedVideoAfterSnapshotIsNotSkipped()throws Exception{MediaFiles.Video video=files.video();Uri photo=files.save(new byte[]{1,2,3},false);long cursor=files.latest();assertEquals(1,files.since(0).length());video.fd().close();files.publish(video.uri());assertEquals(1,files.since(cursor).length());assertEquals("video/mp4",files.since(cursor).getJSONObject(0).getString("mime"));assertTrue(files.latest()>cursor);}
 @Test public void deletingNewestFileDoesNotReuseSyncCursor()throws Exception{files.save(new byte[]{1},false);long cursor=files.latest();files.deleteEntry(files.list(0).getJSONObject(0).getString("id"));assertEquals(0,files.list(0).length());assertEquals(cursor,files.latest());files.save(new byte[]{2},false);assertTrue(files.latest()>cursor);assertEquals(1,files.since(cursor).length());}
 @Test public void failedOrPendingVideoIsAbsentFromSync()throws Exception{MediaFiles.Video video=files.video();assertEquals(0,files.since(0).length());video.fd().close();files.delete(video.uri());assertEquals(0,files.list(0).length());}
 @Test public void malformedMetadataDoesNotHideIndexedPhoto()throws Exception{files.save(new byte[]{1,2,3},false);org.json.JSONObject item=files.list(0).getJSONObject(0);assertEquals("image/jpeg",item.getString("mime"));assertEquals(0,item.getInt("width"));assertEquals(1,files.since(0).length());}
 @Test public void galleryReadsDimensionsFromSavedFile()throws Exception{android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(320,240,android.graphics.Bitmap.Config.ARGB_8888);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,bytes);bitmap.recycle();files.save(bytes.toByteArray(),false);org.json.JSONObject item=files.list(0).getJSONObject(0);assertEquals(320,item.getInt("width"));assertEquals(240,item.getInt("height"));}

 @Test public void indexCountsWholeGalleryAndPaginatesAcrossDeletion()throws Exception{
  for(int i=0;i<105;i++)files.save(new byte[]{1,2,3},false);
  assertEquals(105,files.count());long snapshot=files.latest();org.json.JSONObject first=files.index(Long.MAX_VALUE,snapshot);assertEquals(100,first.getJSONArray("items").length());long before=first.getLong("next");assertTrue(before>0);
  files.deleteEntry(first.getJSONArray("items").getJSONObject(0).getString("id"));files.save(new byte[]{4},false);
  org.json.JSONObject second=files.index(before,snapshot);assertEquals(5,second.getJSONArray("items").length());assertEquals(0,second.getLong("next"));
  org.json.JSONArray again=files.index(Long.MAX_VALUE,snapshot).getJSONArray("items");assertEquals(100,again.length());for(int i=0;i<again.length();i++)assertTrue(again.getJSONObject(i).getLong("sequence")<=snapshot);
 }

}
