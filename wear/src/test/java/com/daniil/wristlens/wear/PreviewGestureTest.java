package com.daniil.wristlens.wear;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import com.daniil.wristlens.core.PreviewViewport;
import com.daniil.wristlens.shared.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Android tap recognizer, real multi-pointer MotionEvent dispatch, inverse focus mapping, and native draw. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="en-rUS-w240dp-h240dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class PreviewGestureTest {
    ActivityController<Activity> controller;Activity activity;PreviewView preview;WatchLayout root;
    List<float[]> focuses=new ArrayList<>();long downTime;
    @Before public void before() {
        controller=Robolectric.buildActivity(Activity.class).setup();activity=controller.get();Strings.init(activity);
        root=new WatchLayout(activity);preview=new PreviewView(activity);preview.fillScreen(true);preview.viewportGestures(true);
        root.preview(preview);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(root);
        RoundMenuTest.layout(root,480);preview.frame(frame(640,480),0,false);preview.onFocus((x,y)->focuses.add(new float[]{x,y}));
    }
    @After public void after(){controller.pause().stop().destroy();}
    private PreviewViewport viewport(){return ReflectionHelpers.getField(preview,"viewport");}
    static Bitmap frame(int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff39576a);
        Paint paint=new Paint();paint.setColor(0xff55775e);canvas.drawRect(0,height*.65f,width,height,paint);
        paint.setColor(0xffdbb175);canvas.drawRect(width*.43f,height*.3f,width*.57f,height*.75f,paint);
        int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW};
        for(int i=0;i<4;i++){paint.setColor(colors[i]);canvas.drawRect((i%2==0?0:width-24),(i<2?0:height-24),(i%2==0?24:width),(i<2?24:height),paint);}
        paint.setColor(0xffeeeeee);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);canvas.drawRect(2,2,width-2,height-2,paint);
        return bitmap;
    }
    private void idle(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    private void event(int action,float... xy){eventTo(root,action,xy);}
    private void eventTo(View view,int action,float... xy) {
        if((action&MotionEvent.ACTION_MASK)==MotionEvent.ACTION_DOWN)downTime=SystemClock.uptimeMillis();
        int count=xy.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[i*2];coords[i].y=xy[i*2+1];coords[i].pressure=1;coords[i].size=1;}
        MotionEvent e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        assertTrue(view.dispatchTouchEvent(e));
        e.recycle();idle(20);
    }
    private void tap(float x,float y){event(MotionEvent.ACTION_DOWN,x,y);event(MotionEvent.ACTION_UP,x,y);}
    private void confirm(){idle(ViewConfiguration.getDoubleTapTimeout()+50);}
    private void pinch(float fromSpan,float toSpan,float cx,float cy) {
        event(MotionEvent.ACTION_DOWN,cx-fromSpan/2,cy);
        event(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),cx-fromSpan/2,cy,cx+fromSpan/2,cy);
        for(int i=1;i<=10;i++){float span=fromSpan+(toSpan-fromSpan)*i/10;event(MotionEvent.ACTION_MOVE,cx-span/2,cy,cx+span/2,cy);}
        event(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),cx-toSpan/2,cy,cx+toSpan/2,cy);
        event(MotionEvent.ACTION_UP,cx-toSpan/2,cy);
    }
    @Test public void pinchFitsAllFourFrameCornersInsideCircle() throws Exception {
        pinch(400,90,240,240);assertEquals(.6,viewport().zoom(),.001);assertTrue(focuses.isEmpty());confirm();assertTrue(focuses.isEmpty());
        assertEquals(384,viewport().displayedWidth(),.01);assertEquals(288,viewport().displayedHeight(),.01);
        RoundMenuTest.save(root,"preview-landscape-fit");
        Bitmap output=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);preview.draw(new Canvas(output));
        assertEquals(Color.BLACK,output.getPixel(240,30));
        assertEquals(Color.RED,output.getPixel(55,103));assertEquals(Color.GREEN,output.getPixel(425,103));
        assertEquals(Color.BLUE,output.getPixel(55,377));assertEquals(Color.YELLOW,output.getPixel(425,377));
    }
    @Test public void portraitFrameFitsAndDoesNotResetWithEveryNewFrame() throws Exception {
        preview.frame(frame(480,640),0,false);pinch(400,90,240,240);
        assertEquals(288,viewport().displayedWidth(),.01);assertEquals(384,viewport().displayedHeight(),.01);
        for(int i=0;i<20;i++)preview.frame(frame(240,320),0,false);
        assertEquals(.6,viewport().zoom(),.001);RoundMenuTest.save(root,"preview-portrait-fit");
    }
    @Test public void pinchOutThenDragAndDoubleTapRestoresDefaultWithoutFocus() throws Exception {
        pinch(120,420,240,240);assertTrue(viewport().zoom()>1.5);
        event(MotionEvent.ACTION_DOWN,240,240);event(MotionEvent.ACTION_MOVE,310,275);event(MotionEvent.ACTION_MOVE,360,300);event(MotionEvent.ACTION_UP,360,300);
        assertTrue(viewport().x()>50);assertTrue(viewport().y()>20);confirm();assertTrue(focuses.isEmpty());RoundMenuTest.save(root,"preview-zoom-pan");
        tap(240,240);idle(60);tap(242,241);confirm();
        assertEquals(1,viewport().zoom(),0);assertEquals(0,viewport().x(),0);assertEquals(0,viewport().y(),0);assertTrue(focuses.isEmpty());
        RoundMenuTest.save(root,"preview-reset");
    }
    @Test public void singleFingerPanAtFillShowsLeftOrRightCropWithoutChangingCameraZoom() throws Exception {
        event(MotionEvent.ACTION_DOWN,240,240);event(MotionEvent.ACTION_MOVE,350,240);event(MotionEvent.ACTION_UP,350,240);
        assertEquals(80,viewport().x(),.01);assertEquals(1,viewport().zoom(),0);confirm();assertTrue(focuses.isEmpty());RoundMenuTest.save(root,"preview-fill-pan");
    }
    @Test public void singleTapFocusIsDeferredAndUsesTheVisibleCrop() {
        viewport().pan(80,0);tap(240,240);assertTrue(focuses.isEmpty());confirm();
        assertEquals(1,focuses.size());assertEquals(.375,focuses.get(0)[0],.001);assertEquals(.5,focuses.get(0)[1],.001);
    }
    @Test public void doubleTapFromFittedViewDoesNotFocusOrQuickScale() {
        pinch(400,90,240,240);confirm();tap(240,240);idle(60);
        event(MotionEvent.ACTION_DOWN,240,240);event(MotionEvent.ACTION_MOVE,240,350);event(MotionEvent.ACTION_UP,240,350);confirm();
        assertEquals(1,viewport().zoom(),0);assertEquals(0,viewport().y(),0);assertTrue(focuses.isEmpty());
    }
    @Test public void tapInBlackMarginsOrOutsideCircleDoesNotFocus() {
        pinch(400,90,240,240);confirm();tap(240,30);confirm();tap(12,12);confirm();assertTrue(focuses.isEmpty());
        tap(240,240);confirm();assertEquals(1,focuses.size());
    }
    @Test public void rotatedMirroredPannedFrameDrawsAndFocusesTheSamePoint() {
        Bitmap bitmap=frame(640,480);Canvas c=new Canvas(bitmap);Paint p=new Paint();p.setColor(Color.MAGENTA);c.drawCircle(352,216,5,p);
        preview.frame(bitmap,90,true);viewport().scaleBy(2,240,240);viewport().pan(40,-30);
        Bitmap output=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);preview.draw(new Canvas(output));
        assertEquals(Color.MAGENTA,output.getPixel(232,274));tap(232,274);confirm();
        assertEquals(1,focuses.size());assertEquals(.55,focuses.get(0)[0],.001);assertEquals(.45,focuses.get(0)[1],.001);
    }
    @Test public void changingOrientationOrAspectResetsAndCancelsPendingFocus() {
        viewport().scaleBy(2,240,240);tap(240,240);preview.frame(frame(640,480),90,false);confirm();
        assertTrue(focuses.isEmpty());assertEquals(1,viewport().zoom(),0);
        viewport().scaleBy(2,240,240);preview.frame(frame(1280,720),90,false);assertEquals(1,viewport().zoom(),0);
        viewport().scaleBy(2,240,240);preview.frame(frame(1280,720),90,true);assertEquals(1,viewport().zoom(),0);
    }
    @Test public void windowFocusLossAndDisconnectCancelDelayedFocusAndGestures() {
        tap(240,240);root.dispatchWindowFocusChanged(false);confirm();assertTrue(focuses.isEmpty());
        root.dispatchWindowFocusChanged(true);tap(240,240);preview.clear();confirm();assertTrue(focuses.isEmpty());
        pinch(120,420,240,240);assertEquals(1,viewport().zoom(),0);assertFalse(preview.canAdjustViewport());
    }
    @Test public void liftingOneFingerAndAddingAnotherDoesNotJumpOrFocus() {
        viewport().scaleBy(2,240,240);
        event(MotionEvent.ACTION_DOWN,150,240);event(MotionEvent.ACTION_POINTER_DOWN|256,150,240,330,240);
        event(MotionEvent.ACTION_MOVE,170,240,350,240);assertEquals(20,viewport().x(),.01);
        event(MotionEvent.ACTION_POINTER_UP|256,170,240,350,240);
        event(MotionEvent.ACTION_MOVE,180,240);assertEquals(30,viewport().x(),.01);
        event(MotionEvent.ACTION_POINTER_DOWN|256,180,240,300,240);
        event(MotionEvent.ACTION_MOVE,190,240,310,240);assertEquals(40,viewport().x(),.01);
        event(MotionEvent.ACTION_UP,190,240);confirm();assertTrue(focuses.isEmpty());
    }
    @Test public void pinchStartedOnShutterCancelsItsPendingClickAndHeldBurst() {
        AtomicInteger clicks=new AtomicInteger(),starts=new AtomicInteger(),ends=new AtomicInteger();
        IconButton shutter=new IconButton(activity,"photo","Photo",clicks::incrementAndGet);shutter.allowHold=true;shutter.hold(starts::incrementAndGet,ends::incrementAndGet);
        root.addControl(shutter,.5f,.79f,56);RoundMenuTest.layout(root,480);
        float x=shutter.getX()+shutter.getWidth()/2f,y=shutter.getY()+shutter.getHeight()/2f;
        for(boolean held:new boolean[]{false,true}) {
            event(MotionEvent.ACTION_DOWN,x,y);if(held)idle(360);
            event(MotionEvent.ACTION_POINTER_DOWN|256,x,y,160,150);idle(400);
            event(MotionEvent.ACTION_MOVE,x+20,y-20,140,130);
            event(MotionEvent.ACTION_POINTER_UP|256,x+20,y-20,140,130);event(MotionEvent.ACTION_UP,x+20,y-20);confirm();
            assertFalse(shutter.holding());assertEquals(0,clicks.get());assertTrue(focuses.isEmpty());
        }
        assertEquals(1,starts.get());assertEquals(1,ends.get());
        tap(x,y);assertEquals(1,clicks.get());
    }
    @Test public void pinchStartedOnOrdinaryButtonDoesNotClickOrOpenLongPressSettings() {
        AtomicInteger clicks=new AtomicInteger(),longs=new AtomicInteger();
        IconButton button=new IconButton(activity,"flash_off","Flash",clicks::incrementAndGet);button.setOnLongClickListener(v->{longs.incrementAndGet();return true;});
        root.addControl(button,.25f,.35f,48);RoundMenuTest.layout(root,480);
        event(MotionEvent.ACTION_DOWN,120,168);event(MotionEvent.ACTION_POINTER_DOWN|256,120,168,360,168);idle(600);
        event(MotionEvent.ACTION_MOVE,80,168,400,168);event(MotionEvent.ACTION_MOVE,60,168,420,168);
        event(MotionEvent.ACTION_POINTER_UP|256,60,168,420,168);event(MotionEvent.ACTION_UP,60,168);confirm();
        assertEquals(0,clicks.get());assertEquals(0,longs.get());assertTrue(focuses.isEmpty());assertTrue(viewport().zoom()>1);
        tap(120,168);assertEquals(1,clicks.get());
    }
    @Test public void phonePreviewKeepsImmediateTapFocusAndAspectFit() {
        PreviewView phone=new PreviewView(activity);activity.setContentView(phone);RoundMenuTest.layout(phone,480);phone.frame(frame(640,480),0,false);
        AtomicInteger count=new AtomicInteger();phone.onFocus((x,y)->{assertEquals(.5,x,.001);assertEquals(.5,y,.001);count.incrementAndGet();});
        eventTo(phone,MotionEvent.ACTION_DOWN,240,240);eventTo(phone,MotionEvent.ACTION_UP,240,240);
        assertEquals(1,count.get());assertFalse(phone.canAdjustViewport());
        PreviewViewport v=ReflectionHelpers.getField(phone,"viewport");assertEquals(480,v.displayedWidth(),.01);assertEquals(360,v.displayedHeight(),.01);
    }
    @Test public void watchActivityGesturesDoNotSendCameraCommandsAndControlsStayFixed() throws Exception {
        try(ActivityController<WatchActivity> watch=Robolectric.buildActivity(WatchActivity.class).create().visible()) {
            WatchActivity a=watch.get();
            WatchLayout dial=(WatchLayout)((android.view.ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
            PreviewView display=ReflectionHelpers.getField(a,"preview");
            org.json.JSONObject state=Wire.object("phase","ready","zoom",1,"flash","off","camera","0",
                    "cameras",new org.json.JSONArray().put(Wire.object("id","0","maxZoom",10)));
            ReflectionHelpers.setField(a,"connected",true);ReflectionHelpers.setField(a,"session","test-session");
            ReflectionHelpers.setField(a,"state",state);ReflectionHelpers.setField(a,"lastFrame",SystemClock.elapsedRealtime());
            ReflectionHelpers.callInstanceMethod(a,"render");RoundMenuTest.layout(dial,480);display.frame(frame(640,480),0,false);
            View button=ReflectionHelpers.getField(a,"shutter");Rect before=new Rect();button.getHitRect(before);
            WatchLayout savedRoot=root;PreviewView savedPreview=preview;root=dial;preview=display;
            try {
                pinch(400,90,240,240);RoundMenuTest.save(dial,"watch-preview-fit-controls");
                assertEquals(.6,viewport().zoom(),.001);
                tap(240,240);idle(60);tap(241,241);confirm();assertEquals(1,viewport().zoom(),0);
                event(MotionEvent.ACTION_DOWN,240,240);event(MotionEvent.ACTION_MOVE,320,240);event(MotionEvent.ACTION_UP,320,240);confirm();
                RoundMenuTest.save(dial,"watch-preview-pan-controls");
                Map<?,?> pending=ReflectionHelpers.getField(a,"pending");assertTrue(pending.isEmpty());
                Rect after=new Rect();button.getHitRect(after);assertEquals(before,after);assertEquals(1,state.optDouble("zoom"),0);
                MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;
                MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.setAxisValue(MotionEvent.AXIS_SCROLL,-1);
                MotionEvent rotary=MotionEvent.obtain(0,SystemClock.uptimeMillis(),MotionEvent.ACTION_SCROLL,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,0,0,InputDevice.SOURCE_ROTARY_ENCODER,0);
                assertTrue(dial.dispatchGenericMotionEvent(rotary));rotary.recycle();
                assertEquals(1,pending.size());Object command=pending.values().iterator().next();
                org.json.JSONObject payload=ReflectionHelpers.getField(command,"command");assertEquals("zoom",payload.optString("op"));assertEquals(1.1,payload.optDouble("value"),.001);
                assertEquals(1,viewport().zoom(),0);
            } finally {root=savedRoot;preview=savedPreview;}
        }
    }
}
