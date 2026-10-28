package com.daniil.wristlens.wear;
import android.app.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
import com.daniil.wristlens.shared.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;
import java.util.*;import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,qualifiers="en-rUS-w240dp-h240dp-round-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class RoundMenuTest {
 Activity activity;GallerySettingsTest.FakeMedia media;Gallery gallery;
 @Before public void before()throws Exception{activity=Robolectric.buildActivity(Activity.class).setup().get();Strings.init(activity);media=new GallerySettingsTest.FakeMedia(activity);gallery=new Gallery(activity,media);Bitmap thumb=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(thumb);Paint paint=new Paint();paint.setColor(0xff296d8a);canvas.drawRect(0,0,160,160,paint);paint.setColor(0xffe8b979);canvas.drawCircle(112,42,21,paint);paint.setColor(0xff264238);canvas.drawRect(0,100,160,160,paint);ByteArrayOutputStream bytes=new ByteArrayOutputStream();thumb.compress(Bitmap.CompressFormat.PNG,100,bytes);media.thumb=android.util.Base64.encodeToString(bytes.toByteArray(),0);thumb.recycle();media.entries.get(0).put("name","IMG_20261023_183551.jpg").put("bytes",2940000).put("time",1792779751000L).put("width",3968).put("height",2976);media.entries.get(1).put("name","VID_20261023_184313.mp4").put("bytes",32140000).put("time",1792780093000L).put("width",2560).put("height",1440).put("durationMs",31000);}
 @After public void after(){gallery.destroy();media.close();for(Dialog d:new ArrayList<>(ShadowDialog.getShownDialogs()))d.dismiss();activity.finish();}
 static RoundMenuFrame frame(Dialog d){return (RoundMenuFrame)GallerySettingsTest.views(d).stream().filter(v->v instanceof RoundMenuFrame).findFirst().orElseThrow();}
 static void layout(View v,int pixels){int spec=View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY);v.measure(spec,spec);v.layout(0,0,pixels,pixels);}
 static void save(View v,String name)throws Exception{Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.BLACK);Path clip=new Path();clip.addCircle(v.getWidth()/2f,v.getHeight()/2f,v.getWidth()/2f,Path.Direction.CW);canvas.clipPath(clip);v.draw(canvas);File dir=new File("build/ui-verification-1.8");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 @Test public void fullScreenScrollUsesInsetsAtEverySizeAndFontScale()throws Exception{
  for(int pixels:new int[]{384,416,480})for(float scale:new float[]{1,1.5f,2}){
   RuntimeEnvironment.setFontScale(scale);CaptureSettingsUi.settings(activity,Wire.object("background",true),true,(c,done)->done.accept(c));Dialog d=ShadowDialog.getLatestDialog();RoundMenuFrame root=frame(d);layout(root,pixels);ScrollView scroll=(ScrollView)root.getChildAt(0);
   assertEquals(0,scroll.getTop());assertEquals(pixels,scroll.getHeight());assertEquals(pixels,scroll.getWidth());
   assertFalse(scroll.isVerticalFadingEdgeEnabled());assertTrue(scroll.getChildAt(0).getPaddingTop()>=pixels*.18f);
   assertTrue(scroll.getChildAt(0).getPaddingLeft()>=Controls.dp(activity,24));
   scroll.scrollTo(0,10000);layout(root,pixels);assertTrue("Settings remain scrollable",scroll.getScrollY()>0);
   if(pixels==480)save(root,"settings-bottom-font-"+scale);d.dismiss();
  }RuntimeEnvironment.setFontScale(1);
 }
 @Test public void galleryListHasSmallLeftPreviewAndRightMetadataAndGridIsDifferent()throws Exception{
  activity.getPreferences(0).edit().putBoolean("gallery_tiles",false).commit();gallery.open();Dialog d=ShadowDialog.getLatestDialog();RoundMenuFrame root=frame(d);layout(root,480);
  View row=GallerySettingsTest.views(d).stream().filter(v->"gallery_row".equals(v.getTag())).findFirst().orElseThrow();assertTrue(row instanceof LinearLayout);assertEquals(LinearLayout.HORIZONTAL,((LinearLayout)row).getOrientation());
  ImageView thumbnail=(ImageView)((LinearLayout)row).getChildAt(0);View info=((LinearLayout)row).getChildAt(1);assertTrue(info.getLeft()>=thumbnail.getRight());assertEquals(Controls.dp(activity,44),thumbnail.getWidth());
  TextView properties=(TextView)GallerySettingsTest.views(row).stream().filter(v->"gallery_properties".equals(v.getTag())).findFirst().orElseThrow();assertTrue(properties.getText().toString().contains("3968×2976"));assertTrue(properties.getText().toString().contains("JPEG"));
  ScrollView scroll=(ScrollView)root.getChildAt(0);ViewGroup column=(ViewGroup)scroll.getChildAt(0);View body=column.getChildAt(column.getChildCount()-1);save(root,"gallery-list-top");scroll.scrollTo(0,body.getTop());save(root,"gallery-list");
  GallerySettingsTest.text(d,"View: list / tiles").performClick();GallerySettingsTest.text(ShadowDialog.getLatestDialog(),"Tiles").performClick();layout(root,480);View tile=GallerySettingsTest.views(d).stream().filter(v->"gallery_tile".equals(v.getTag())).findFirst().orElseThrow();assertEquals(LinearLayout.VERTICAL,((LinearLayout)tile).getOrientation());assertTrue(((LinearLayout)tile).getChildAt(0).getWidth()>thumbnail.getWidth());save(root,"gallery-grid");
 }
 @Test public void replacingSettingKeepsSafePaddingAndScrollPosition()throws Exception{
  CaptureSettingsUi.settings(activity,Wire.object("background",true),true,(c,done)->done.accept(Wire.object("background",false)));
  Dialog d=ShadowDialog.getLatestDialog();RoundMenuFrame root=frame(d);layout(root,480);ScrollView scroll=(ScrollView)root.getChildAt(0);
  GallerySettingsTest.text(d,"Background capture").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();layout(root,480);
  assertTrue(d.isShowing());assertTrue(scroll.getChildAt(0).getPaddingLeft()>=Controls.dp(activity,24));save(root,"settings-top");
 }
 @Test public void longRussianInstructionFitsViewport()throws Exception{
  RuntimeEnvironment.setQualifiers("ru-rRU-w240dp-h240dp-round-xhdpi");Strings.init(activity);CaptureSettingsUi.settings(activity,Wire.object("background",true),true,(c,done)->done.accept(c));Dialog d=ShadowDialog.getLatestDialog();RoundMenuFrame root=frame(d);layout(root,480);save(root,"settings-ru");
  for(View view:GallerySettingsTest.views(d))if(view instanceof TextView text&&text.getLayout()!=null)for(int line=0;line<text.getLineCount();line++)assertTrue("Text must wrap: "+text.getText()+" line="+line+" measured="+text.getLayout().getLineMax(line)+" available="+(text.getWidth()-text.getTotalPaddingLeft()-text.getTotalPaddingRight()),text.getLayout().getLineMax(line)<=text.getWidth()-text.getTotalPaddingLeft()-text.getTotalPaddingRight()+2);
 }
}
