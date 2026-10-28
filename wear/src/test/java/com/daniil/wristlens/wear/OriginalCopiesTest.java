package com.daniil.wristlens.wear;
import android.content.*;import android.content.pm.ProviderInfo;import android.database.*;import android.net.Uri;import android.os.*;import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import java.io.*;import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class OriginalCopiesTest {
 Context context;Store store;
 public static class Store extends ContentProvider {
  Map<Uri,Integer> pending=new HashMap<>();Map<Uri,File> files=new HashMap<>();int next;boolean reject;
  public boolean onCreate(){return true;}public String getType(Uri u){return "image/jpeg";}
  public Uri insert(Uri u,ContentValues values){return null;}
  public int update(Uri u,ContentValues v,String s,String[] args){if(reject||!files.containsKey(u))return 0;pending.put(u,v.getAsInteger("is_pending"));return 1;}
  public int delete(Uri u,String s,String[] args){pending.remove(u);File f=files.remove(u);if(f!=null)f.delete();return f==null?0:1;}
  public Cursor query(Uri u,String[] projection,String s,String[] args,String sort){MatrixCursor c=new MatrixCursor(projection);if(pending.containsKey(u))c.addRow(new Object[]{pending.get(u)});return c;}
  public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(files.get(u),ParcelFileDescriptor.MODE_READ_ONLY);}
  Uri incoming()throws IOException{Uri u=Uri.parse("content://media/external/images/media/"+(++next));File f=File.createTempFile("copy-",".jpg",getContext().getCacheDir());try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}files.put(u,f);pending.put(u,1);return u;}
 }
 @Before public void before(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("copies",0).edit().clear().commit();store=new Store();ProviderInfo info=new ProviderInfo();info.authority="media";info.exported=true;store.attachInfo(context,info);ShadowContentResolver.registerProviderInternal("media",store);}
 @Test public void simultaneousManualAndAutomaticCopyPublishesOnlyOne()throws Exception{Uri first=store.incoming(),second=store.incoming();assertEquals(first,OriginalCopies.commit(context,"photo",first));assertEquals(first,OriginalCopies.commit(context,"photo",second));assertEquals(1,store.files.size());assertEquals(first,OriginalCopies.find(context,"photo"));}
 @Test public void pendingCrashEntryIsCleanedAndCanBeRetried()throws Exception{Uri broken=store.incoming();context.getSharedPreferences("copies",0).edit().putString("photo",broken.toString()).commit();assertNull(OriginalCopies.find(context,"photo"));assertFalse(store.files.containsKey(broken));Uri retry=store.incoming();assertEquals(retry,OriginalCopies.commit(context,"photo",retry));}
 @Test public void failedPublicationNeverBecomesCompletedCopy()throws Exception{Uri u=store.incoming();store.reject=true;assertThrows(IOException.class,()->OriginalCopies.commit(context,"photo",u));assertNull(OriginalCopies.find(context,"photo"));assertFalse(store.files.containsKey(u));}
 @Test public void deletingWatchCopyAllowsFreshDownload()throws Exception{Uri u=store.incoming();OriginalCopies.commit(context,"photo",u);store.delete(u,null,null);assertNull(OriginalCopies.find(context,"photo"));assertFalse(context.getSharedPreferences("copies",0).contains("photo"));}
 @Test public void automaticCopiesAreOptIn(){context.getSharedPreferences("auto_save",0).edit().clear().commit();assertFalse(AutoSave.enabled(context));}
 @Test public void orphanRecoveryKeepsLiveAndPublishedCopies()throws Exception{Uri abandoned=store.incoming(),live=store.incoming(),published=store.incoming();context.getSharedPreferences("pending_copies",0).edit().putBoolean(abandoned.toString(),true).putBoolean(published.toString(),true).commit();store.pending.put(published,0);OriginalCopies.begin(context,live);OriginalCopies.recover(context);assertFalse(store.files.containsKey(abandoned));assertTrue(store.files.containsKey(published));assertTrue(store.files.containsKey(live));OriginalCopies.end(context,live);}
}
