package com.daniil.wristlens.wear;

import static org.junit.Assert.*;
import android.graphics.*;
import android.os.Looper;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import com.daniil.wristlens.shared.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

/** Android measure/layout/draw regression tests. Camera/transport are not simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, qualifiers="ru-rRU-w240dp-h240dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class WatchLayoutTest {
    private static <T> T field(WatchActivity activity,String name) { return ReflectionHelpers.getField(activity,name); }
    private static ViewGroup root(WatchActivity activity) {
        return (ViewGroup)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
    }
    private static void traverse(View view,int size) {
        view.measure(View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY));
        view.layout(0,0,size,size); // Only one traversal: a second pass could mask the original bug.
    }
    private static JSONObject ready() { return Wire.object("phase","ready","zoom",1,"flash","off","photoTimer",0,"videoTimer",0); }
    private static void render(WatchActivity activity,boolean connected,JSONObject state) {
        ReflectionHelpers.setField(activity,"connected",connected);
        ReflectionHelpers.setField(activity,"state",state);
        ReflectionHelpers.setField(activity,"lastFrame",SystemClock.elapsedRealtime());
        ReflectionHelpers.callInstanceMethod(activity,"render");
    }
    private static List<View> buttons(ViewGroup root) {
        List<View> out=new ArrayList<>();
        for(int i=0;i<root.getChildCount();i++) {
            View v=root.getChildAt(i);
            if(v instanceof IconButton && v.getVisibility()==View.VISIBLE) out.add(v);
        }
        return out;
    }
    private static void geometry(ViewGroup root,int count) {
        List<View> views=buttons(root);assertEquals(count,views.size());
        for(View v:views) {
            assertTrue(v.getWidth()>0);
            assertTrue("Control was measured as the whole screen: "+v.getWidth(),v.getWidth()<=Controls.dp(root.getContext(),56));
            assertEquals(v.getLayoutParams().width,v.getWidth());
            assertEquals(v.getMeasuredWidth(),v.getWidth());assertEquals(v.getWidth(),v.getHeight());
            double x=(v.getLeft()+v.getRight()-root.getWidth())/2.0,y=(v.getTop()+v.getBottom()-root.getHeight())/2.0;
            assertTrue("Button clips the round screen",Math.hypot(x,y)+v.getWidth()/2.0<=root.getWidth()/2.0+1);
        }
        for(int i=0;i<views.size();i++) for(int j=i+1;j<views.size();j++) {
            Rect a=new Rect(),b=new Rect();views.get(i).getHitRect(a);views.get(j).getHitRect(b);
            assertFalse("Overlapping touch targets: "+a+" / "+b,Rect.intersects(a,b));
        }
    }
    private static Bitmap capture(View view,String name) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);
        Path circle=new Path();circle.addCircle(view.getWidth()/2f,view.getHeight()/2f,Math.min(view.getWidth(),view.getHeight())/2f,Path.Direction.CW);
        canvas.clipPath(circle);view.draw(canvas);
        File dir=new File("build/ui-verification");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        return bitmap;
    }
    private static void frame(WatchActivity activity) {
        Bitmap b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(0xff344e61);
        Paint p=new Paint();p.setColor(0xff546e60);c.drawRect(0,280,640,480,p);
        p.setColor(0xffcba46b);c.drawRect(260,160,380,350,p);
        ((PreviewView)field(activity,"preview")).frame(b,0,false); // Synthetic test image, not a camera stream.
    }
    private static void tap(View root,float x,float y) {
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,x,y,0);
        root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);down.recycle();up.recycle();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void offlineFirstTraversalHasOnlyOpenPhoneAndMenu() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);traverse(r,480);capture(r,"offline-480");geometry(r,2);
            for(String n:new String[]{"mode","shutter","flashButton","timerButton","flipButton"})assertEquals(View.GONE,((View)field(a,n)).getVisibility());
            View phone=field(a,"phoneButton");assertEquals(View.VISIBLE,phone.getVisibility());
            TextView status=field(a,"status");assertTrue(status.getText().toString().contains("Открыть на телефоне"));
            assertTrue(status.getY()+status.getHeight()<=phone.getTop());
        }
    }
    @Test public void onlineFirstTraversalHasSmallNonOverlappingControls() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);render(a,true,ready());frame(a);traverse(r,480);capture(r,"photo-480");geometry(r,6);
            assertEquals(96,((View)field(a,"flashButton")).getWidth());assertEquals(112,((View)field(a,"shutter")).getWidth());
            PreviewView preview=field(a,"preview");assertEquals(480,preview.getWidth());assertEquals(480,preview.getHeight());
            assertEquals(View.GONE,((View)field(a,"phoneButton")).getVisibility());
            ShutterEffect effect=field(a,"shutterEffect");
            assertTrue(r.indexOfChild(effect)>r.indexOfChild(preview));
            assertTrue(r.indexOfChild(effect)<r.indexOfChild(field(a,"hud")));
            effect.fire();android.animation.ValueAnimator animation=ReflectionHelpers.getField(effect,"animator");
            for(int ms:new int[]{0,45,90,135,180}){animation.setCurrentPlayTime(ms);capture(r,"shutter-"+ms+"ms").recycle();geometry(r,6);}
            effect.clear();
        }
    }
    @Test public void allButtonsAndPreviewReceiveTheirOwnTouches() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);ctl.visible(); // Attach before Android posts click callbacks.
            render(a,true,ready());frame(a);traverse(r,480);capture(r,"touch-targets-480");
            List<View> views=buttons(r);List<AtomicInteger> clicks=new ArrayList<>();
            for(View v:views){AtomicInteger n=new AtomicInteger();clicks.add(n);v.setOnClickListener(w->n.incrementAndGet());}
            AtomicInteger focus=new AtomicInteger();PreviewView preview=field(a,"preview");
            preview.onFocus((x,y)->{assertEquals(.5f,x,.01f);assertEquals(.5f,y,.01f);focus.incrementAndGet();});
            for(View v:views)tap(r,v.getX()+v.getWidth()/2f,v.getY()+v.getHeight()/2f);
            for(int i=0;i<views.size();i++)assertEquals(views.get(i).getContentDescription().toString(),1,clicks.get(i).get());
            assertEquals(0,focus.get());tap(r,240,240);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout()+50));assertEquals(1,focus.get());
        }
    }
    @Test public void reconnectRecordPauseCountdownAndDisconnectKeepLayout() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);traverse(r,480);
            render(a,true,ready());frame(a);traverse(r,480);geometry(r,6);
            JSONObject rec=ready().put("recording",true).put("elapsedMs",75000);
            render(a,true,rec);traverse(r,480);capture(r,"recording-480");geometry(r,6);
            assertEquals("Пауза записи",((View)field(a,"mode")).getContentDescription().toString());
            render(a,true,rec.put("paused",true));traverse(r,480);capture(r,"paused-480");
            assertEquals("Продолжить запись",((View)field(a,"mode")).getContentDescription().toString());
            render(a,true,ready().put("countdown",3));traverse(r,480);capture(r,"countdown-480");
            assertEquals(View.VISIBLE,((View)field(a,"countdown")).getVisibility());
            render(a,false,rec.put("countdown",3));traverse(r,480);geometry(r,2);
            assertEquals("Меню",((View)field(a,"more")).getContentDescription().toString());
            assertEquals(View.GONE,((View)field(a,"countdown")).getVisibility());
        }
    }
    @Test public void resizeRepositionsControlsBeforeMeasuring() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);render(a,true,ready());frame(a);
            for(int size:new int[]{480,416,384,480}){traverse(r,size);capture(r,"resize-"+size);geometry(r,6);}
        }
    }
    @Test @Config(qualifiers="ru-rRU-w240dp-h240dp-round-hdpi")
    public void lowerDensityRoundScreenWorks() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);render(a,true,ready());frame(a);traverse(r,360);
            capture(r,"photo-360-hdpi");geometry(r,6);assertEquals(72,((View)field(a,"timerButton")).getWidth());
        }
    }
    @Test public void curvedTextIsCenteredAboveDial() throws Exception {
        RoundHud hud=new RoundHud(RuntimeEnvironment.getApplication());hud.update("ФОТО · 12:34 · 1.0×",false);traverse(hud,480);
        Bitmap b=capture(hud,"arc-480");int target=ThemeColors.accent(hud.getContext()),min=480,max=-1;
        for(int y=0;y<200;y++)for(int x=0;x<480;x++)if(b.getPixel(x,y)==target){min=Math.min(min,x);max=Math.max(max,x);}
        assertTrue("Text missing or stuck at right end: "+min+".."+max,min<200&&max>280);
        assertEquals("Arc label must be centered",240,(min+max)/2,24);
    }
    @Test public void largeFontKeepsHudStatusAndCountdownVisible() throws Exception {
        RuntimeEnvironment.setFontScale(2f);
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();ViewGroup r=root(a);traverse(r,480);capture(r,"offline-large-font");
            TextView status=field(a,"status");
            assertEquals(status.getText().length(),status.getLayout().getLineEnd(status.getLineCount()-1));
            assertTrue(status.getLayout().getLineBottom(status.getLineCount()-1)<=status.getHeight());
            render(a,true,ready().put("zoom",10));frame(a);traverse(r,480);capture(r,"photo-large-font");geometry(r,6);
            assertTrue(((RoundHud)field(a,"hud")).getContentDescription().toString().contains("10.0×"));
            render(a,true,ready().put("countdown",10));traverse(r,480);capture(r,"countdown-large-font");
            TextView countdown=field(a,"countdown");assertEquals(1,countdown.getLineCount());assertTrue(countdown.getLayout().getLineBottom(0)<=countdown.getHeight());
        } finally {RuntimeEnvironment.setFontScale(1f);}
    }
    @Test public void offlineMenuOpensAfterRecordingConnectionIsLost() throws Exception {
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()) {
            WatchActivity a=ctl.get();render(a,false,ready().put("recording",true));((View)field(a,"more")).performClick();
            assertNotNull(ShadowDialog.getLatestDialog());assertTrue(ShadowDialog.getLatestDialog().isShowing());ShadowDialog.getLatestDialog().dismiss();
        }
    }
    private static List<View> descendants(View view){
        List<View> out=new ArrayList<>();out.add(view);if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)out.addAll(descendants(g.getChildAt(i)));return out;
    }
    @Test public void hudLeavesPreviewTransparentOutsideGlyphs()throws Exception{
        RoundHud hud=new RoundHud(RuntimeEnvironment.getApplication());hud.update("ФОТО · 12:34 · 1.0×",false);traverse(hud,480);
        Bitmap b=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);hud.draw(new Canvas(b));int painted=0;
        for(int y=0;y<240;y++)for(int x=0;x<480;x++)if(Color.alpha(b.getPixel(x,y))>0)painted++;
        assertTrue("Text absent",painted>100);assertTrue("Background arc must not be painted",painted<6500);
        assertEquals(0,Color.alpha(b.getPixel(90,83)));assertEquals(0,Color.alpha(b.getPixel(390,83)));
    }
    @Test public void menuHasClockHelpDisconnectAndNoCancelButton()throws Exception{
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()){
            WatchActivity a=ctl.get();((View)field(a,"more")).performClick();android.app.Dialog d=ShadowDialog.getLatestDialog();
            ViewGroup content=d.findViewById(android.R.id.content);List<View> views=descendants(content);
            assertEquals(1,views.stream().filter(v->v instanceof CurvedClock).count());
            List<String> labels=views.stream().filter(v->v instanceof android.widget.Button).map(v->((TextView)v).getText().toString()).toList();
            assertTrue(labels.contains("Инструкция"));assertTrue(labels.contains(Strings.tr(com.daniil.wristlens.shared.R.string.wl_189)));assertTrue(labels.contains("Файлы и сохранение"));assertTrue(labels.contains("Вибрация действий"));
            assertFalse(labels.stream().anyMatch(v->v.contains("Отмена таймера")||v.contains("Жесты")||v.equals("Настройки часов")||v.equals("Настройки съёмки")));traverse(content,480);capture(content,"menu-480");d.dismiss();render(a,true,ready());((View)field(a,"more")).performClick();android.app.Dialog online=ShadowDialog.getLatestDialog();assertTrue(GallerySettingsTest.has(online,"Отключить соединение"));assertFalse(GallerySettingsTest.has(online,Strings.tr(com.daniil.wristlens.shared.R.string.wl_189)));online.dismiss();
        }
    }
    @Test public void explicitDisconnectClearsSessionAndBlocksAutomaticReconnect(){
        try(ActivityController<WatchActivity> ctl=Robolectric.buildActivity(WatchActivity.class).create()){
            WatchActivity a=ctl.get();render(a,true,ready());ReflectionHelpers.setField(a,"session","old-session");
            ReflectionHelpers.callInstanceMethod(a,"disconnected");
            assertEquals("",field(a,"session"));assertEquals(Boolean.FALSE,field(a,"connected"));assertEquals(Boolean.TRUE,field(a,"manualDisconnected"));
            assertEquals(Boolean.TRUE,field(a,"autoLaunchAttempted"));assertEquals(View.VISIBLE,((View)field(a,"phoneButton")).getVisibility());
        }
    }

}

