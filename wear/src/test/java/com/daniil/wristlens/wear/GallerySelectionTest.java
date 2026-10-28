package com.daniil.wristlens.wear;
import android.app.*;import android.content.Context;import android.os.Looper;import android.view.View;
import com.daniil.wristlens.shared.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import java.util.*;import java.util.function.Consumer;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,qualifiers="en-rUS-w240dp-h240dp-round-xhdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class GallerySelectionTest {
 Activity activity;PagedMedia media;Gallery gallery;
 static class PagedMedia extends GallerySettingsTest.FakeMedia {
  int indexCalls,deleteCalls;boolean delayIndex;Runnable pendingIndex;
  PagedMedia(Context c){super(c);entries.clear();for(int i=1;i<=105;i++)entries.add(Wire.object("id",""+i,"name","IMG_"+i+".jpg","mime","image/jpeg"));}
  @Override void request(JSONObject j,Consumer<JSONObject> done,Consumer<String> fail){
   String op=j.optString("op");if(op.equals("list")){last=j;listCalls++;int start=Math.min(entries.size(),j.optInt("offset"));done.accept(Wire.object("items",new JSONArray(entries.subList(start,Math.min(entries.size(),start+20))),"total",entries.size(),"cursor",105));}
   else if(op.equals("index")){indexCalls++;JSONArray found=new JSONArray();long before=j.optLong("before",Long.MAX_VALUE),last=0;for(int i=entries.size()-1;i>=0&&found.length()<100;i--){JSONObject item=entries.get(i);long id=Long.parseLong(item.optString("id"));if(id<before){found.put(item);last=id;}}JSONObject reply=Wire.object("items",found,"next",found.length()==100?last:0,"snapshot",105);if(delayIndex)pendingIndex=()->done.accept(reply);else done.accept(reply);}
   else{if(op.equals("delete_many")){deleteCalls++;assertTrue(j.optJSONArray("ids").length()<=50);}super.request(j,done,fail);}
  }
 }
 @Before public void before(){activity=Robolectric.buildActivity(Activity.class).setup().get();Strings.init(activity);media=new PagedMedia(activity);gallery=new Gallery(activity,media);}
 @After public void after(){gallery.destroy();media.close();for(Dialog d:new ArrayList<>(ShadowDialog.getShownDialogs()))d.dismiss();activity.finish();}
 @Test public void countIncludesUnloadedFilesAndSelectAllIncludesAllPages(){gallery.open();Dialog d=ShadowDialog.getLatestDialog();assertTrue(GallerySettingsTest.has(d,"Files: 105"));assertFalse(GallerySettingsTest.has(d,"IMG_105.jpg"));((View)GallerySettingsTest.text(d,"IMG_1.jpg").getParent()).performLongClick();GallerySettingsTest.text(d,"Select all").performClick();assertTrue(GallerySettingsTest.has(d,"Selected: 105"));assertEquals(2,media.indexCalls);GallerySettingsTest.text(d,"Share on phone").performClick();assertEquals(105,media.last.optJSONArray("ids").length());GallerySettingsTest.text(d,"Clear selection").performClick();assertFalse(GallerySettingsTest.has(d,"Selected: 105"));}
 @Test public void deletingAllUsesBatchesAndRefreshesCount(){gallery.open();Dialog d=ShadowDialog.getLatestDialog();((View)GallerySettingsTest.text(d,"IMG_1.jpg").getParent()).performLongClick();GallerySettingsTest.text(d,"Select all").performClick();GallerySettingsTest.text(d,"Delete selected").performClick();GallerySettingsTest.text(ShadowDialog.getLatestDialog(),"Delete").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(3,media.deleteCalls);assertTrue(media.entries.isEmpty());assertTrue(GallerySettingsTest.has(d,"Files: 0"));assertEquals("Deleted: 105",ShadowToast.getTextOfLatestToast());}
 @Test public void lateSelectAllCannotOverrideClearSelection(){media.delayIndex=true;gallery.open();Dialog d=ShadowDialog.getLatestDialog();((View)GallerySettingsTest.text(d,"IMG_1.jpg").getParent()).performLongClick();GallerySettingsTest.text(d,"Select all").performClick();GallerySettingsTest.text(d,"Clear selection").performClick();media.pendingIndex.run();assertFalse(GallerySettingsTest.has(d,"Selected: 100"));assertFalse(GallerySettingsTest.has(d,"Select all"));}
}
