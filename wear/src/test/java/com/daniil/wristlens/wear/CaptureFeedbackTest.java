package com.daniil.wristlens.wear;

import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import android.graphics.*;
import com.daniil.wristlens.shared.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;import java.util.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,qualifiers="en-rUS-w240dp-h240dp-round-xhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class CaptureFeedbackTest {
 Activity a;
 @Before public void before(){a=Robolectric.buildActivity(Activity.class).setup().get();Strings.init(a);ActionFeedback.enabled(a,true);CaptureFeedback.enabled(a,true);}
 @After public void after(){for(Dialog d:new ArrayList<>(ShadowDialog.getShownDialogs()))d.dismiss();a.finish();}
 private JSONObject state(String session,int n,String kind){return Wire.object("session",session,"phoneTime",n*200,"events",n==0?new JSONArray():new JSONArray().put(Wire.object("id",n,"time",n*200,"kind",kind)));}
 @Test public void burstFramesGiveHapticsAndAnimationWithoutReplayingSnapshots(){View target=new View(a);ShutterEffect effect=new ShutterEffect(a);try(CaptureFeedback f=new CaptureFeedback(a,target,effect)){f.state(state("s",0,""));f.state(state("s",1,"BURST_PHOTO"));assertEquals(HapticFeedbackConstants.CLOCK_TICK,Shadows.shadowOf(target).lastHapticFeedbackPerformed());Object animation=ReflectionHelpers.getField(effect,"animator");assertNotNull(animation);f.state(state("s",1,"BURST_PHOTO"));assertSame(animation,ReflectionHelpers.getField(effect,"animator"));f.state(state("s",2,"BURST_PHOTO"));assertNotSame(animation,ReflectionHelpers.getField(effect,"animator"));f.reset();assertNull(ReflectionHelpers.getField(effect,"animator"));f.state(state("s",2,"BURST_PHOTO"));assertNull(ReflectionHelpers.getField(effect,"animator"));}}
 @Test public void mutedVibrationDoesNotSuppressVisualFeedback(){ActionFeedback.enabled(a,false);CaptureFeedback.enabled(a,false);View target=new View(a);ShutterEffect effect=new ShutterEffect(a);try(CaptureFeedback f=new CaptureFeedback(a,target,effect)){f.state(state("s",0,""));f.state(state("s",1,"PHOTO"));assertEquals(-1,Shadows.shadowOf(target).lastHapticFeedbackPerformed());assertNotNull(ReflectionHelpers.getField(effect,"animator"));}}
 @Test public void successfulEventsPlayDistinctSoundsOnce(){
  try(CaptureFeedback f=new CaptureFeedback(a,new View(a),new ShutterEffect(a))){
   android.media.SoundPool pool=ReflectionHelpers.getField(f,"sounds");ShadowSoundPool shadow=Shadows.shadowOf(pool);
   int[] resources={R.raw.shutter,R.raw.burst_shutter,R.raw.record_start,R.raw.record_pause,R.raw.record_resume,R.raw.record_stop};
   String[] kinds={"PHOTO","BURST_PHOTO","START","PAUSE","RESUME","STOP"};
   for(int id:resources)shadow.notifyResourceLoaded(id,true);
   f.state(state("s",0,""));
   for(int i=0;i<kinds.length;i++){f.state(state("s",i+1,kinds[i]));f.state(state("s",i+1,kinds[i]));assertEquals(kinds[i],1,shadow.getResourcePlaybacks(resources[i]).size());}
  }
 }
 @Test public void soundSwitchMutesWithoutQueuingOldEvents(){
  try(CaptureFeedback f=new CaptureFeedback(a,new View(a),new ShutterEffect(a))){
   android.media.SoundPool pool=ReflectionHelpers.getField(f,"sounds");ShadowSoundPool shadow=Shadows.shadowOf(pool);shadow.notifyResourceLoaded(R.raw.shutter,true);
   f.state(state("s",0,""));CaptureFeedback.enabled(a,false);f.state(state("s",1,"PHOTO"));assertFalse(shadow.wasResourcePlayed(R.raw.shutter));
   CaptureFeedback.enabled(a,true);f.state(state("s",1,"PHOTO"));assertFalse(shadow.wasResourcePlayed(R.raw.shutter));
   f.state(state("s",2,"PHOTO"));assertEquals(1,shadow.getResourcePlaybacks(R.raw.shutter).size());
   f.reset();f.state(state("s",2,"PHOTO"));assertEquals(1,shadow.getResourcePlaybacks(R.raw.shutter).size());
  }
 }
 @Test public void failedSoundLoadingDoesNotBlockPhotoFeedback(){
  ShutterEffect effect=new ShutterEffect(a);View target=new View(a);
  try(CaptureFeedback f=new CaptureFeedback(a,target,effect)){
   android.media.SoundPool pool=ReflectionHelpers.getField(f,"sounds");ShadowSoundPool shadow=Shadows.shadowOf(pool);shadow.notifyResourceLoaded(R.raw.shutter,false);
   f.state(state("s",0,""));f.state(state("s",1,"PHOTO"));assertFalse(shadow.wasResourcePlayed(R.raw.shutter));assertNotNull(ReflectionHelpers.getField(effect,"animator"));assertEquals(HapticFeedbackConstants.CONFIRM,Shadows.shadowOf(target).lastHapticFeedbackPerformed());
  }
 }
 @Test public void releaseStopsFeedbackAndDoesNotRestartAnimation(){ShutterEffect effect=new ShutterEffect(a);CaptureFeedback f=new CaptureFeedback(a,new View(a),effect);f.state(state("s",0,""));f.close();f.close();f.state(state("s",1,"PHOTO"));assertNull(ReflectionHelpers.getField(effect,"animator"));}
 @Test public void soundSwitchIsInlineIndependentAndPersists(){try(var ctl=Robolectric.buildActivity(WatchActivity.class).create()){WatchActivity watch=ctl.get();View more=ReflectionHelpers.getField(watch,"more");more.performClick();Dialog menu=ShadowDialog.getLatestDialog();GallerySettingsTest.text(menu,"Capture sounds").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(CaptureFeedback.enabled(a));assertTrue(ActionFeedback.enabled(a));assertEquals("Saved",ShadowToast.getTextOfLatestToast());menu.dismiss();more.performClick();assertFalse(((CheckBox)GallerySettingsTest.text(ShadowDialog.getLatestDialog(),"Capture sounds")).isChecked());}}
 @Test public void onlySettingsOriginShowsSavedEvenWhenAckArrivesAfterNavigation()throws Exception{try(var ctl=Robolectric.buildActivity(WatchActivity.class).create()){WatchActivity watch=ctl.get();Class<?> pendingType=Class.forName(WatchActivity.class.getName()+"$Pending");Constructor<?> ctor=pendingType.getDeclaredConstructors()[0];ctor.setAccessible(true);Map<String,Object> pending=ReflectionHelpers.getField(watch,"pending");for(String op:List.of("zoom","flash","timers","camera")){ShadowToast.reset();pending.put("quick",ctor.newInstance(Wire.object("op",op),0L,false,false));((View)ReflectionHelpers.getField(watch,"more")).performClick();ReflectionHelpers.callInstanceMethod(watch,"acknowledge",ReflectionHelpers.ClassParameter.from(JSONObject.class,Wire.object("id","quick","status","ok")));Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(ShadowToast.getTextOfLatestToast());ShadowDialog.getLatestDialog().dismiss();pending.put("settings",ctor.newInstance(Wire.object("op",op),0L,false,true));ReflectionHelpers.callInstanceMethod(watch,"acknowledge",ReflectionHelpers.ClassParameter.from(JSONObject.class,Wire.object("id","settings","status","ok")));Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("Saved",ShadowToast.getTextOfLatestToast());}}}
 @Test public void burstPickerOffers200msAndSendsIt(){AtomicReference<JSONObject> sent=new AtomicReference<>();Controls.settings(a,MenuInputTest.state(),true,sent::set);Dialog parent=ShadowDialog.getLatestDialog();GallerySettingsTest.views(parent).stream().filter(v->v instanceof Button b&&b.getText().toString().equals(Strings.tr(com.daniil.wristlens.shared.R.string.wl_289)+"200"+Strings.tr(com.daniil.wristlens.shared.R.string.wl_290))).findFirst().orElseThrow().performClick();GallerySettingsTest.text(ShadowDialog.getLatestDialog(),"200 ms").performClick();assertEquals(200,sent.get().optInt("interval"));assertTrue(parent.isShowing());}
 @Test public void shutterAnimationIsTransparentAtRestAndNeverInterceptsControls()throws Exception{ShutterEffect effect=new ShutterEffect(a);RoundMenuTest.layout(effect,480);assertFalse(effect.isClickable());assertFalse(effect.isFocusable());Bitmap b=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);effect.draw(new Canvas(b));assertEquals(0,b.getPixel(30,240));effect.fire();android.animation.ValueAnimator animator=ReflectionHelpers.getField(effect,"animator");animator.setCurrentPlayTime(90);effect.draw(new Canvas(b));assertTrue(Color.alpha(b.getPixel(30,240))>80);assertEquals(0,Color.alpha(b.getPixel(240,240)));RoundMenuTest.save(effect,"shutter-aperture");effect.clear();b.eraseColor(Color.TRANSPARENT);effect.draw(new Canvas(b));assertEquals(0,b.getPixel(30,240));b.recycle();}
 @Test public void timerCancelTextHasNoBurstInBothLanguages(){for(String locale:new String[]{"en-rUS","ru-rRU"}){RuntimeEnvironment.setQualifiers(locale+"-w240dp-h240dp-round-xhdpi");Strings.init(a);assertEquals(locale.startsWith("ru")?"Таймер отменён":"Timer cancelled",Strings.tr(com.daniil.wristlens.shared.R.string.wl_025));}}
}
