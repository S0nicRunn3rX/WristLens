package com.daniil.wristlens.phone;

import android.content.Context;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.Surface;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import com.daniil.wristlens.core.*;
import com.daniil.wristlens.shared.Wire;

/** Every camera transition is serialized on cameraThread; media I/O and JPEG encoding are separate. */
final class CameraEngine {
    interface Listener {
        void state(JSONObject state);
        void frame(FrameCodec.Frame frame);
        boolean screenAvailable();
        void screenLight(boolean on,Runnable ready);
    }
    private final Context context;
    private final Listener listener;
    private final CameraManager manager;
    private final MediaFiles files;
    private final CaptureOptions options;
    private boolean watchAway;private int sessionGeneration;
    private final HandlerThread cameraThread=new HandlerThread("camera"),imageThread=new HandlerThread("preview-encode");
    final Handler h;
    private final Handler imageHandler;
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,Capabilities> cameras=new LinkedHashMap<>();
    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder repeating;
    private ImageReader yuvReader,jpegReader;
    private GlPreview gl;private Surface previewSurface;private volatile FrameCodec.Frame glFrame;
    private final HoldBurst hold=new HoldBurst();private final Set<Size> rejectedPhotos=new HashSet<>();
    private VideoRecorder recorder;
    final MicrophoneHub microphone;private final AudioOptions audioOptions;
    private MediaFiles.Video video;
    private Capabilities caps;
    private Size photoSize,previewSize;
    private Capabilities.VideoMode videoMode;
    private String phase="opening",message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_006),flash="off",focusMode="auto";
    private float zoom=1,focusDistance=0;
    private long lastMarkedFrames;
    private int generation=0,photoRotation=0,lockedRotation=0,photoTimer=0,videoTimer=0,burstCount=50,burstInterval=BurstSettings.DEFAULT_INTERVAL_MS;
    private long recordStart=0,pauseStart=0,pausedTotal=0,countdownDeadline=0;
    private String timerAction="";
    private boolean audio,closed=false,recording=false,paused=false,configuring=false,photoBusy=false,waiting3a=false,videoJpeg=false;
    private boolean photoInVideo=false,photoBurst=false,screenLit=false;
    private final CaptureEvents captureEvents=new CaptureEvents();
    private void feedback(CaptureEvents.Kind kind){captureEvents.add(SystemClock.elapsedRealtime(),kind);}
    private int burstRemaining=0,burstSaved=0,photoTicket=0,threeAFrames=0;
    private long threeAStart=0;
    private MeteringRectangle[] focusRegions;
    private volatile int deviceDegrees=0,frameRotation=90,streamWidth=0,streamQuality=60,streamInterval=67,watchW=480,watchH=480;
    private String streamMode="auto";
    private volatile boolean front=false;
    private volatile Raw raw;
    private volatile long lastEncoded=0;
    private long sequence=0;
    private record Raw(byte[] data,int w,int h,int rotation,long time){}

    CameraEngine(Context c,boolean audio,Listener listener) {
        context=c;audioOptions=new AudioOptions(c);microphone=new MicrophoneHub(c);this.audio=audio&&audioOptions.record();this.listener=listener;manager=c.getSystemService(CameraManager.class);files=new MediaFiles(c);options=new CaptureOptions(c);
        applyPreview(context.getSharedPreferences("preview",0).getString("mode","auto"));
        cameraThread.start();imageThread.start();h=new Handler(cameraThread.getLooper());imageHandler=new Handler(imageThread.getLooper());
        h.post(()->{
            try {
                for(String id:manager.getCameraIdList()) {
                    Capabilities candidate=new Capabilities(id,manager.getCameraCharacteristics(id));
                    if(!candidate.photos.isEmpty() && candidate.map.getOutputSizes(ImageFormat.YUV_420_888)!=null) cameras.put(id,candidate);
                }
                caps=cameras.values().stream().filter(it->!it.front).findFirst().orElse(cameras.values().stream().findFirst().orElseThrow());
                JSONArray diagnostic=new JSONArray();for(Capabilities cam:cameras.values())diagnostic.put(cam.diagnostic());context.getSharedPreferences("diagnostics",0).edit().putString("cameraApi",diagnostic.toString()).apply();
                defaults();open();h.post(holdTick);
            } catch(Exception e) { configuring=false;phase="error";error(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_007),e); }
        });
    }
    void orientation(int degrees){deviceDegrees=degrees;h.post(this::updateOrientation);}
    private void updateOrientation(){ if(caps!=null)frameRotation=CameraMath.jpegOrientation(caps.value(CameraCharacteristics.SENSOR_ORIENTATION),deviceDegrees,caps.front);syncGl(); }
    private void defaults() {
        rejectedPhotos.clear();photoSize=caps.photos.get(0);
        videoMode=caps.videos.stream().filter(m->Capabilities.supports(m,files.videoType())).findFirst().orElse(null);
        zoom=1;flash="off";focusMode="auto";focusDistance=0;focusRegions=null;front=caps.front;updateOrientation();
    }
    private void open() throws CameraAccessException {
        if(context.checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_008));
        int token=++generation;phase="opening";configuring=true;publish();
        manager.openCamera(caps.id,new CameraDevice.StateCallback(){
            @Override public void onOpened(CameraDevice device) {
                if(closed||token!=generation){device.close();return;}camera=device;configure(false,false);
            }
            @Override public void onDisconnected(CameraDevice device){device.close();if(token==generation)cameraFailed(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_009));}
            @Override public void onError(CameraDevice device,int code){device.close();if(token==generation)cameraFailed(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_010)+code);}
        },h);
    }
    private void cameraFailed(String text) {
        if(recording) finishRecording(false);
        releaseSession();if(camera!=null){camera.close();camera=null;}
        configuring=false;phase="error";message=text;publish();
    }
    private void configure(boolean forVideo,boolean withoutJpeg) {
        if(camera==null||closed)return;
        configuring=true;phase=forVideo?"starting":"configuring";publish();
        releaseSession();final int token=generation,configuration=sessionGeneration;
        try {
            double aspect=forVideo?(double)videoMode.width()/videoMode.height():(double)photoSize.getWidth()/photoSize.getHeight();
            boolean marked=forVideo&&options.watermark().enabled();
            boolean special=forVideo&&(videoMode.fps()>30||marked);
            previewSize=marked?new Size(videoMode.width(),videoMode.height()):special?(videoMode.highSpeed()?new Size(videoMode.width(),videoMode.height()):caps.privateSize(aspect,videoMode.fps())):caps.previewSize(aspect,forVideo?videoMode.fps():30);
            if(special){
                GlPreview.Sink sink=(jpeg,w,he)->{FrameCodec.Frame f=new FrameCodec.Frame(++sequence,w,he,recording?lockedRotation:frameRotation,front,jpeg);glFrame=f;listener.frame(f);};
                if(marked)gl=new GlPreview(imageHandler,previewSize.getWidth(),previewSize.getHeight(),sink,recorder.getSurface(),options.watermark(),lockedRotation,e->h.post(()->{
                    if(closed||configuration!=sessionGeneration)return;
                    if(recording)finishRecording(true);else{releaseSession();cleanupRecorder(false);configure(false,false);}
                    message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_011)+e.getMessage();publish();
                }));else gl=new GlPreview(imageHandler,previewSize.getWidth(),previewSize.getHeight(),sink);
                syncGl();previewSurface=gl.surface;
            }
            else{yuvReader=ImageReader.newInstance(previewSize.getWidth(),previewSize.getHeight(),ImageFormat.YUV_420_888,3);yuvReader.setOnImageAvailableListener(this::onPreview,imageHandler);previewSurface=yuvReader.getSurface();}
            List<Surface> surfaces=new ArrayList<>();surfaces.add(previewSurface);
            if(!withoutJpeg&&!(forVideo&&videoMode.highSpeed())){
                Size size=photoSize;
                if(forVideo) size=caps.photos.stream().filter(s->s.getWidth()<=videoMode.width()&&s.getHeight()<=videoMode.height())
                        .min(Comparator.comparingDouble(s->Math.abs(s.getWidth()/(double)s.getHeight()-aspect)*100-(long)s.getWidth()*s.getHeight()/1e7))
                        .orElse(caps.photos.get(caps.photos.size()-1));
                jpegReader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.JPEG,2);
                jpegReader.setOnImageAvailableListener(this::onJpeg,h);surfaces.add(jpegReader.getSurface());
            }
            if(forVideo&&!marked)surfaces.add(recorder.getSurface());
            CameraCaptureSession.StateCallback callback=new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession session) {
                    if(closed||token!=generation||configuration!=sessionGeneration){session.close();return;}
                    captureSession=session;
                    try{
                        repeating=camera.createCaptureRequest(forVideo?CameraDevice.TEMPLATE_RECORD:CameraDevice.TEMPLATE_PREVIEW);
                        repeating.addTarget(previewSurface);if(forVideo&&!marked)repeating.addTarget(recorder.getSurface());
                        apply(repeating,false);repeat();
                        configuring=false;videoJpeg=forVideo&&jpegReader!=null;
                        if(forVideo){if(watchAway&&!options.background())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_012));recorder.start();recording=true;feedback(CaptureEvents.Kind.START);if(gl!=null&&gl.compositing())gl.recording(true);syncGl();paused=false;recordStart=SystemClock.elapsedRealtime();pausedTotal=0;phase="recording";message=videoJpeg?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_013):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_014);}
                        else{phase="ready";message=rejectedPhotos.isEmpty()?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_015):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_016)+photoSize;}
                        publish();
                    }catch(Exception e){ if(forVideo){recording=false;releaseSession();cleanupRecorder(false);configure(false,false);} else configuring=false;error(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_017),e); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();if(closed||token!=generation||configuration!=sessionGeneration)return;
                    if(forVideo&&!withoutJpeg){configure(true,true);return;}
                    releaseSession();cleanupRecorder(false);configuring=false;
                    if(!forVideo&&fallbackPhoto())return;
                    phase="error";message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_018);publish();if(forVideo)configure(false,false);
                }
            };
            List<OutputConfiguration> outputs=new ArrayList<>();
            for(Surface surface:surfaces){OutputConfiguration output=new OutputConfiguration(surface);
                if(Build.VERSION.SDK_INT>=33)output.setMirrorMode(OutputConfiguration.MIRROR_MODE_NONE);
                if(Build.VERSION.SDK_INT>=31&&!forVideo&&caps.maximumPhotos.contains(photoSize)&&jpegReader!=null&&surface==jpegReader.getSurface())output.addSensorPixelModeUsed(CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
                outputs.add(output);
            }
            camera.createCaptureSession(new SessionConfiguration(forVideo&&videoMode.highSpeed()?SessionConfiguration.SESSION_HIGH_SPEED:SessionConfiguration.SESSION_REGULAR,outputs,r->h.post(r),callback));
        }catch(Exception e){
            if(forVideo&&!withoutJpeg){configure(true,true);return;}
            releaseSession();cleanupRecorder(false);configuring=false;if(!forVideo&&fallbackPhoto())return;phase="error";error(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_019),e);if(forVideo)configure(false,false);
        }
    }
    private void onPreview(ImageReader reader) {
        try(Image image=reader.acquireLatestImage()) {
            if(image==null||closed)return;
            long now=SystemClock.elapsedRealtime();if(now-lastEncoded<streamInterval)return;lastEncoded=now;
            int w=image.getWidth(),height=image.getHeight();byte[] nv=Yuv.nv21(image);
            int rotation=recording?lockedRotation:frameRotation;raw=new Raw(nv,w,height,rotation,now);
            int requestedWidth=StreamSizing.fit(w,height,rotation,watchW,watchH,streamWidth)[0],quality=streamQuality;
            byte[] jpeg=Yuv.jpeg(nv,w,height,requestedWidth,quality);
            int outputWidth=Math.min(w,requestedWidth)&~1;
            listener.frame(new FrameCodec.Frame(++sequence,outputWidth,(height*outputWidth/w)&~1,rotation,front,jpeg));
        }catch(IllegalStateException ignored){}catch(Exception e){ android.util.Log.w("WristLens","Preview encode",e); }
    }
    private final CameraCaptureSession.CaptureCallback captureCallback=new CameraCaptureSession.CaptureCallback(){
        @Override public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){
            if(!waiting3a)return;
            threeAFrames++;
            Integer ae=result.get(CaptureResult.CONTROL_AE_STATE),awb=result.get(CaptureResult.CONTROL_AWB_STATE),af=result.get(CaptureResult.CONTROL_AF_STATE);
            boolean aeReady=ae==null||ae==CaptureResult.CONTROL_AE_STATE_CONVERGED||ae==CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED||ae==CaptureResult.CONTROL_AE_STATE_LOCKED;
            boolean awbReady=awb==null||awb==CaptureResult.CONTROL_AWB_STATE_CONVERGED||awb==CaptureResult.CONTROL_AWB_STATE_LOCKED;
            boolean afReady=af==null||af==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED||af==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED||af==CaptureResult.CONTROL_AF_STATE_INACTIVE||focusMode.equals("manual");
            if((threeAFrames>=3 && aeReady && awbReady && afReady)||SystemClock.elapsedRealtime()-threeAStart>1800)captureStill();
        }
    };
    private void apply(CaptureRequest.Builder b,boolean still) {
        b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        int af;
        if(focusMode.equals("manual")){af=CaptureRequest.CONTROL_AF_MODE_OFF;b.set(CaptureRequest.LENS_FOCUS_DISTANCE,focusDistance);}
        else if(focusRegions!=null&&caps.af(CaptureRequest.CONTROL_AF_MODE_AUTO))af=CaptureRequest.CONTROL_AF_MODE_AUTO;
        else if(caps.af(recording||recorder!=null?CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO:CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))
            af=recording||recorder!=null?CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO:CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        else af=caps.af(CaptureRequest.CONTROL_AF_MODE_AUTO)?CaptureRequest.CONTROL_AF_MODE_AUTO:CaptureRequest.CONTROL_AF_MODE_OFF;
        b.set(CaptureRequest.CONTROL_AF_MODE,af);
        if(focusRegions!=null&&!(still&&caps.maximumPhotos.contains(photoSize))){
            if(caps.value(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)>0)b.set(CaptureRequest.CONTROL_AF_REGIONS,focusRegions);
            if(caps.value(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)>0)b.set(CaptureRequest.CONTROL_AE_REGIONS,focusRegions);
        }
        if(Build.VERSION.SDK_INT>=31&&still&&!photoInVideo&&caps.maximumPhotos.contains(photoSize)){b.set(CaptureRequest.SENSOR_PIXEL_MODE,CaptureRequest.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);Rect a=caps.c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION);if(a!=null)b.set(CaptureRequest.SCALER_CROP_REGION,zoomCrop(a));}
        else b.set(CaptureRequest.SCALER_CROP_REGION,crop());
        int ae=CaptureRequest.CONTROL_AE_MODE_ON;
        boolean hardwarePhoto=caps.flash&&!recording&&recorder==null;
        if(hardwarePhoto&&flash.equals("auto")&&caps.ae(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH))ae=CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
        if(hardwarePhoto&&flash.equals("on")&&caps.ae(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH))ae=CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
        if(screenLit&&caps.ae(CaptureRequest.CONTROL_AE_MODE_ON_EXTERNAL_FLASH))ae=CaptureRequest.CONTROL_AE_MODE_ON_EXTERNAL_FLASH;
        b.set(CaptureRequest.CONTROL_AE_MODE,ae);
        b.set(CaptureRequest.FLASH_MODE,caps.flash&&flash.equals("torch")?CaptureRequest.FLASH_MODE_TORCH:CaptureRequest.FLASH_MODE_OFF);
        int fps=recorder!=null&&videoMode!=null?videoMode.fps():30;
        Range<Integer> range=recorder!=null&&videoMode.highSpeed()?new Range<>(fps,fps):caps.fpsRange(fps);if(range!=null)b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,range);
        if(still){b.set(CaptureRequest.JPEG_ORIENTATION,photoRotation);b.set(CaptureRequest.JPEG_QUALITY,(byte)95);}
    }
    private Rect crop() {
        Rect active=caps.c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        return zoomCrop(active);
    }
    private Rect zoomCrop(Rect active){
        int w=((int)(active.width()/zoom))&~1,h=((int)(active.height()/zoom))&~1;
        int left=active.left+(active.width()-w)/2,top=active.top+(active.height()-h)/2;
        return new Rect(left,top,left+w,top+h);
    }
    private void refresh() throws CameraAccessException {
        if(repeating!=null&&captureSession!=null){apply(repeating,false);repeat();}
    }
    void command(JSONObject cmd,Consumer<String> result) {
        h.post(()->{try{handle(cmd);result.accept("");}catch(Exception e){result.accept(e.getMessage()==null?e.toString():e.getMessage());}});
    }
    private void handle(JSONObject cmd) throws Exception {
        if(closed||caps==null)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_020));
        String op=cmd.getString("op");
        switch(op){
            case "photo" -> schedule("photo",photoTimer);
            case "burst" -> {idle();schedule("burst",photoTimer);}
            case "hold_start" -> {idle();if(hold.start(cmd.getString("hold"),SystemClock.elapsedRealtime(),burstCount))schedule("hold",photoTimer);}
            case "hold_stop" -> {hold.stop(cmd.getString("hold"));if(timerAction.equals("hold"))cancelTimer();}
            case "storage" -> {idle();files.setting(cmd);if(cmd.optString("key").equals("videoType"))videoMode=caps.videos.stream().filter(m->Capabilities.supports(m,files.videoType())).findFirst().orElse(null);}
            case "capture_settings" -> {if(!cmd.optString("key").equals("background"))idle();options.apply(cmd);if(watchAway&&!options.background())watchHidden();}
            case "record_start" -> {if(recording)return;idle();schedule("video",videoTimer);}
            case "record_pause" -> {if(!recording)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_021));if(!paused){if(gl!=null&&gl.compositing())gl.recording(false);try{recorder.pause();}catch(Exception e){if(gl!=null&&gl.compositing())gl.recording(true);throw e;}paused=true;pauseStart=SystemClock.elapsedRealtime();phase="paused";feedback(CaptureEvents.Kind.PAUSE);message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_022);}}
            case "record_resume" -> {if(!recording)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_021));if(paused){recorder.resume();if(gl!=null&&gl.compositing())gl.recording(true);pausedTotal+=SystemClock.elapsedRealtime()-pauseStart;paused=false;phase="recording";feedback(CaptureEvents.Kind.RESUME);message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_023);}}
            case "record_stop" -> {cancelTimer();if(recording){if(photoBusy)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_024));finishRecording(true);}}
            case "cancel" -> {boolean timed=countdownDeadline>0;hold.stop();cancelTimer();burstRemaining=0;message=com.daniil.wristlens.shared.Strings.tr(timed?com.daniil.wristlens.shared.R.string.wl_025:com.daniil.wristlens.shared.R.string.capture_cancelled);}
            case "zoom" -> {ready();zoom=(float)CameraMath.clamp(cmd.getDouble("value"),1,caps.maxZoom);focusRegions=null;refresh();}
            case "focus" -> {
                ready();if(caps.value(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)==0&&caps.value(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)==0)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_026));
                focusMode="auto";Rect crop=crop();int[] r=CameraMath.focusRect(crop.left,crop.top,crop.width(),crop.height(),previewSize.getWidth()/(double)previewSize.getHeight(),cmd.getDouble("x"),cmd.getDouble("y"));
                focusRegions=new MeteringRectangle[]{new MeteringRectangle(new Rect(r[0],r[1],r[2],r[3]),MeteringRectangle.METERING_WEIGHT_MAX)};
                refresh();if(!(captureSession instanceof CameraConstrainedHighSpeedCaptureSession)&&caps.af(CaptureRequest.CONTROL_AF_MODE_AUTO)){repeating.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);captureSession.capture(repeating.build(),captureCallback,h);repeating.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);}
            }
            case "focus_auto" -> {ready();focusMode="auto";focusRegions=null;refresh();}
            case "focus_manual" -> {ready();if(captureSession instanceof CameraConstrainedHighSpeedCaptureSession)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_027));if(caps.minFocus<=0)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_028));focusMode="manual";focusRegions=null;focusDistance=(float)CameraMath.clamp(cmd.getDouble("value"),0,caps.minFocus);refresh();}
            case "flash" -> {
                ready();String value=cmd.getString("value");if(!List.of("off","auto","on","torch").contains(value))throw new IllegalArgumentException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_029));
                if(!value.equals("off")&&!caps.flash&&!caps.front)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_030));
                if(recording&&(value.equals("auto")||value.equals("on")))throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_031));
                if(caps.front&&!caps.flash&&!value.equals("off")&&!listener.screenAvailable())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_032));
                flash=value;screenLit=caps.front&&!caps.flash&&flash.equals("torch");listener.screenLight(screenLit,()->{});refresh();
            }
            case "camera" -> {idle();Capabilities next=cameras.get(cmd.getString("value"));if(next==null)throw new IllegalArgumentException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_007));
                releaseSession();if(camera!=null)camera.close();camera=null;listener.screenLight(false,()->{});screenLit=false;caps=next;defaults();open();}
            case "photo_size" -> {idle();Size next=Size.parseSize(cmd.getString("value"));if(!caps.photos.contains(next))throw new IllegalArgumentException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_033));photoSize=next;configure(false,false);}
            case "video_mode" -> {idle();String key=cmd.getString("value");videoMode=caps.videos.stream().filter(m->m.key().equals(key)&&Capabilities.supports(m,files.videoType())).findFirst().orElseThrow(()->new IllegalArgumentException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_034)));}
            case "timers" -> {if(countdownDeadline>0)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_035));photoTimer=(int)CameraMath.clamp(cmd.optInt("photo",photoTimer),0,60);videoTimer=(int)CameraMath.clamp(cmd.optInt("video",videoTimer),0,60);}
            case "burst_config" -> {idle();burstCount=(int)CameraMath.clamp(cmd.optInt("count",burstCount),2,50);burstInterval=BurstSettings.interval(cmd.optInt("interval",burstInterval));}
            case "stream" -> {String key=cmd.optString("value","auto");if(!List.of("eco","auto","detail").contains(key))throw new IllegalArgumentException("Unknown preview profile");applyPreview(key);context.getSharedPreferences("preview",0).edit().putString("mode",streamMode).apply();syncGl();}
            case "audio" -> {boolean on=cmd.getBoolean("value");if(on&&!microphoneGranted())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.mic_required));if(configuring)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_037));if(recording&&!recorder.hasAudio())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.mic_next_video));if(on&&files.videoType().equals("webm")&&!Capabilities.vorbis())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_056));audio=on;audioOptions.record(on);if(recording)recorder.muted(!on);}
            case "preview_audio" -> {boolean on=cmd.getBoolean("value");if(on&&!microphoneGranted())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.mic_required));audioOptions.preview(on);}
            default -> throw new IllegalArgumentException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_036));
        }
        publish();
    }
    private void ready(){if(configuring||camera==null||captureSession==null)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_037));if(photoBusy)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_038));}
    private void idle(){ready();if(recording||countdownDeadline>0||burstRemaining>0||hold.active(SystemClock.elapsedRealtime()))throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_039));}
    private void schedule(String action,int seconds) throws Exception {
        ready();if(countdownDeadline>0||burstRemaining>0)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_040));
        if(action.equals("video")&&videoMode==null)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_041));
        if(!action.equals("photo")&&recording)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_042));
        if(recording)seconds=0; // snapshots during recording are immediate
        if(seconds<=0){perform(action);return;}
        timerAction=action;countdownDeadline=SystemClock.elapsedRealtime()+seconds*1000L;message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_043);h.post(timerTick);publish();
    }
    private final Runnable timerTick=new Runnable(){@Override public void run(){
        if(closed||countdownDeadline==0)return;
        if(SystemClock.elapsedRealtime()>=countdownDeadline){String action=timerAction;countdownDeadline=0;timerAction="";try{perform(action);}catch(Exception e){error(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_044),e);}}
        else {publish();h.postDelayed(this,200);}
    }};
    private void cancelTimer(){countdownDeadline=0;timerAction="";h.removeCallbacks(timerTick);}
    void watchVisible(){h.post(()->watchAway=false);}
    void connectionLost(){h.post(()->{watchAway=true;watchHidden();});}
    private void watchHidden(){
        if(closed)return;hold.stop();burstRemaining=0;
        if(!CapturePolicy.keepTimer(options.background(),timerAction))cancelTimer();
        if(!options.background()){
            if(recording)finishRecording(true);
            else if(recorder!=null){releaseSession();cleanupRecorder(false);configure(false,false);}
        }
        message=options.background()?(recording?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_045):countdownDeadline>0?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_046):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_047)):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_048);publish();
    }
    void screenHidden(){h.post(()->{
        if(!screenLit)return;
        flash="off";screenLit=false;
        if(photoBusy&&!photoInVideo)photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_049));
        try{refresh();}catch(Exception ignored){}
        message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_050);publish();
    });}
    private void perform(String action) throws Exception {
        switch(action){case "hold" -> holdPhoto();case "video" -> startRecording();case "burst" -> {burstRemaining=burstCount;burstSaved=0;takePhoto();}default -> takePhoto();}
    }
    private void startRecording() throws Exception {
        if(videoMode==null)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_051));
        if(options.watermark().enabled()&&videoMode.highSpeed())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_052));
        if(flash.equals("auto")||flash.equals("on"))throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_053));
        if(screenLit&&!listener.screenAvailable())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_054));
        if(!Capabilities.supports(videoMode,files.videoType()))throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_055));if(files.videoType().equals("webm")&&audio&&!Capabilities.vorbis())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_056));
        lockedRotation=frameRotation;video=files.video();
        try {
            boolean granted=context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean track=granted&&(!files.videoType().equals("webm")||Capabilities.vorbis());
            recorder=new VideoRecorder(video.fd().getFileDescriptor(),videoMode,files.videoType(),lockedRotation,track,audio,microphone,e->h.post(()->{if(recording){finishRecording(true);error(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_057),e);}}));
            recorder.prepare();configure(true,false);
        }catch(Exception e){cleanupRecorder(false);throw e;}
    }
    private void finishRecording(boolean reopen) {
        if(!recording)return;
        configuring=true;phase="stopping";publish();boolean saved=false;
        try{if(captureSession!=null)captureSession.stopRepeating();if(gl!=null&&gl.compositing())gl.recording(false);recorder.stop();lastMarkedFrames=gl==null?0:gl.markedFrames();if(options.watermark().enabled()&&lastMarkedFrames==0)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_extra_21));saved=true;message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_058);}
        catch(Exception e){message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_059)+e.getClass().getSimpleName();}
        recording=false;paused=false;releaseSession();cleanupRecorder(saved);if(saved)feedback(CaptureEvents.Kind.STOP);
        if(reopen&&!closed&&camera!=null)configure(false,false);else configuring=false;
    }
    private void cleanupRecorder(boolean save){
        if(recorder!=null){try{recorder.reset();recorder.release();}catch(Exception ignored){}recorder=null;}
        if(video!=null){try{video.fd().close();}catch(Exception ignored){}if(save){try{files.publish(video.uri());}catch(Exception e){files.delete(video.uri());message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_060)+e.getMessage();}}else files.delete(video.uri());video=null;}
    }
    private void takePhoto() throws Exception {takePhoto(burstRemaining>0);}
    private void takePhoto(boolean burst) throws Exception {
        if(photoBusy)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_061));
        // Keep the origin even if this is the last frame or release arrives while it is saving.
        photoBurst=burst&&!recording;
        if(recording&&jpegReader==null){if(glFrame!=null)snapshotGl();else snapshot();return;}
        if(jpegReader==null)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_062));
        boolean screenPhoto=caps.front&&!caps.flash&&(flash.equals("on")||flash.equals("torch")||(flash.equals("auto")&&dark()));
        if(screenPhoto&&!listener.screenAvailable())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_063));
        photoBusy=true;photoInVideo=recording;photoRotation=frameRotation;int ticket=++photoTicket;
        h.postDelayed(()->{if(photoBusy&&ticket==photoTicket)photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_064));},8000);
        if(recording){captureStill();return;}
        if(screenPhoto){screenLit=true;listener.screenLight(true,()->h.post(()->{if(photoBusy&&ticket==photoTicket)begin3a();}));}
        else begin3a();
        publish();
    }
    private boolean dark(){Raw f=raw;if(f==null)return true;long sum=0;int n=0;for(int i=0;i<f.w*f.h;i+=64){sum+=f.data[i]&255;n++;}return n==0||sum/n<70;}
    private void begin3a(){
        try{
            waiting3a=true;threeAFrames=0;threeAStart=SystemClock.elapsedRealtime();refresh();
            repeating.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            if(!focusMode.equals("manual")&&repeating.get(CaptureRequest.CONTROL_AF_MODE)!=CaptureRequest.CONTROL_AF_MODE_OFF)repeating.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
            captureSession.capture(repeating.build(),captureCallback,h);
            repeating.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);repeating.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            int ticket=photoTicket;h.postDelayed(()->{if(waiting3a&&ticket==photoTicket)captureStill();},2200);
        }catch(Exception e){photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_065)+e.getMessage());}
    }
    private void captureStill(){
        if(!photoBusy)return;waiting3a=false;
        try{
            CaptureRequest.Builder b=camera.createCaptureRequest(photoInVideo?CameraDevice.TEMPLATE_VIDEO_SNAPSHOT:CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpegReader.getSurface());if(photoInVideo&&recorder!=null)b.addTarget(gl!=null&&gl.compositing()?previewSurface:recorder.getSurface());
            apply(b,true);captureSession.capture(b.build(),new CameraCaptureSession.CaptureCallback(){
                @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_066));}
            },h);
        }catch(Exception e){photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_067)+e.getMessage());}
    }
    private void onJpeg(ImageReader reader){
        try(Image image=reader.acquireNextImage()) {
            if(image==null)return;
            byte[] bytes=new byte[image.getPlanes()[0].getBuffer().remaining()];image.getPlanes()[0].getBuffer().get(bytes);
            if(!photoBusy)return;waiting3a=false;resetLightAndFocus();int ticket=photoTicket;
            disk.execute(()->{try{files.save(bytes,false);h.post(()->{if(ticket==photoTicket)photoSaved(false);});}catch(Exception e){h.post(()->photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_068)+e.getMessage()));}});
        }catch(Exception e){photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_069)+e.getMessage());}
    }
    private void snapshot() throws Exception {
        Raw frame=raw;if(frame==null||SystemClock.elapsedRealtime()-frame.time>1500)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_070));
        photoBusy=true;photoInVideo=true;int ticket=++photoTicket;
        disk.execute(()->{
            try{
                byte[] jpeg=Yuv.jpeg(frame.data,frame.w,frame.h,frame.w,95);
                Bitmap b=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);Matrix m=new Matrix();m.postRotate(frame.rotation);
                Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);ByteArrayOutputStream out=new ByteArrayOutputStream();rotated.compress(Bitmap.CompressFormat.JPEG,95,out);
                if(rotated!=b)rotated.recycle();b.recycle();files.save(out.toByteArray(),true);
                h.post(()->{if(ticket==photoTicket)photoSaved(true);});
            }catch(Exception e){h.post(()->photoFailure(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_071)+e.getMessage()));}
        });
    }
    private void photoSaved(boolean snapshot){
        feedback(photoBurst?CaptureEvents.Kind.BURST_PHOTO:CaptureEvents.Kind.PHOTO);
        photoBusy=false;message=snapshot?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_072):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_073);
        if(burstRemaining>0){burstRemaining--;burstSaved++;message=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_074)+burstSaved+" / "+burstCount; if(burstRemaining>0)h.postDelayed(()->{if(burstRemaining>0&&!closed)try{takePhoto();}catch(Exception e){photoFailure(e.getMessage());}},burstInterval);}
        if(hold.active(SystemClock.elapsedRealtime()))h.postDelayed(this::holdPhoto,burstInterval);
        publish();
    }
    private void photoFailure(String text){hold.stop();waiting3a=false;photoBusy=false;photoTicket++;burstRemaining=0;resetLightAndFocus();message=text;publish();}
    private void resetLightAndFocus(){
        screenLit=caps!=null&&caps.front&&!caps.flash&&flash.equals("torch");listener.screenLight(screenLit,()->{});
        try{if(repeating!=null){repeating.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);captureSession.capture(repeating.build(),null,h);repeating.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);refresh();}}catch(Exception ignored){}
    }
    private void releaseSession(){
        sessionGeneration++;waiting3a=false;repeating=null;
        if(captureSession!=null){captureSession.close();captureSession=null;}
        if(gl!=null){gl.close();gl=null;}glFrame=null;previewSurface=null;
        if(yuvReader!=null){yuvReader.close();yuvReader=null;}if(jpegReader!=null){jpegReader.close();jpegReader=null;}raw=null;
    }
    private boolean microphoneGranted(){return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private void publish(){
        JSONArray list=new JSONArray();for(Capabilities c:cameras.values())list.put(c.json());
        JSONArray events=new JSONArray();for(CaptureEvents.Event e:captureEvents.snapshot())events.put(Wire.object("id",e.id(),"time",e.time(),"kind",e.kind().name()));
        long elapsed=recording?((paused?pauseStart:SystemClock.elapsedRealtime())-recordStart-pausedTotal):0;
        listener.state(Wire.object("phase",phase,"message",message,"camera",caps==null?"":caps.id,"cameras",list,
                "events",events,"recording",recording,"paused",paused,"elapsedMs",Math.max(0,elapsed),"audio",audio,"previewAudio",audioOptions.preview(),"microphoneGranted",microphoneGranted(),"microphoneSwitchable",!recording||recorder!=null&&recorder.hasAudio(),"busy",photoBusy||configuring,
                "photoSize",photoSize==null?"":photoSize.toString(),"videoMode",videoMode==null?"":videoMode.key(),
                "zoom",zoom,"flash",flash,"focus",focusMode,"focusDistance",focusDistance,
                "streamMode",streamMode,"streamQuality",streamQuality,"streamIntervalMs",streamInterval,"photoTimer",photoTimer,"videoTimer",videoTimer,"countdown",CameraMath.secondsLeft(SystemClock.elapsedRealtime(),countdownDeadline),
                "timerAction",timerAction,"burstCount",burstCount,"burstInterval",burstInterval,"burstRemaining",burstRemaining,
                "watermarkFrames",gl==null?lastMarkedFrames:gl.markedFrames(),"capture",options.json(),"storage",files.settings(),"holding",hold.active(SystemClock.elapsedRealtime()),"holdCount",hold.count(),"highSpeed",recording&&videoMode.highSpeed(),"watchWidth",watchW,"watchHeight",watchH,"rejectedPhotos",rejectedPhotos.toString(),
                "snapshotMode",recording?(videoJpeg?"jpeg":"preview"):"jpeg","screenAvailable",listener.screenAvailable()));
    }
    private boolean fallbackPhoto(){rejectedPhotos.add(photoSize);for(Size next:caps.photos)if(!rejectedPhotos.contains(next)){photoSize=next;configure(false,false);return true;}return false;}
    private void repeat() throws CameraAccessException {if(captureSession instanceof CameraConstrainedHighSpeedCaptureSession hs)hs.setRepeatingBurst(hs.createHighSpeedRequestList(repeating.build()),captureCallback,h);else captureSession.setRepeatingRequest(repeating.build(),captureCallback,h);}
    void watchSize(int w,int he){h.post(()->{watchW=Math.max(2,Math.min(2048,w));watchH=Math.max(2,Math.min(2048,he));syncGl();});}
    private void applyPreview(String key){com.daniil.wristlens.core.PreviewProfile p=com.daniil.wristlens.core.PreviewProfile.of(key);streamMode=p.key();streamWidth=p.preferredWidth();streamQuality=p.jpegQuality();streamInterval=p.intervalMs();}
    private void syncGl(){if(gl!=null){gl.screenW=watchW;gl.screenH=watchH;gl.rotation=recording?lockedRotation:frameRotation;gl.interval=streamInterval;gl.quality=streamQuality;gl.preferred=streamWidth;}}
    void holdSignal(String id,boolean stop){h.post(()->{if(stop){hold.stop(id);if(timerAction.equals("hold"))cancelTimer();}else hold.renew(id,SystemClock.elapsedRealtime());});}
    private final Runnable holdTick=new Runnable(){public void run(){if(closed)return;if(!hold.active(SystemClock.elapsedRealtime())&&timerAction.equals("hold")){cancelTimer();publish();}h.postDelayed(this,150);}};
    private void holdPhoto(){if(!hold.take(SystemClock.elapsedRealtime())){publish();return;}try{takePhoto(true);}catch(Exception e){photoFailure(e.getMessage());}}
    private void snapshotGl(){FrameCodec.Frame frame=glFrame;photoBusy=true;photoInVideo=true;int ticket=++photoTicket;disk.execute(()->{try{Bitmap b=BitmapFactory.decodeByteArray(frame.jpeg(),0,frame.jpeg().length);Matrix m=new Matrix();m.postRotate(frame.rotation());Bitmap r=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);ByteArrayOutputStream out=new ByteArrayOutputStream();r.compress(Bitmap.CompressFormat.JPEG,95,out);if(r!=b)r.recycle();b.recycle();files.save(out.toByteArray(),true);h.post(()->{if(ticket==photoTicket)photoSaved(true);});}catch(Exception e){h.post(()->photoFailure(e.getMessage()));}});}
    void refreshState(){h.post(()->{if(!closed)publish();});}
    private void error(String label,Exception e){message=label+": "+e.getMessage();android.util.Log.e("WristLens",message,e);publish();}
    void closeIfIdle(java.util.function.BooleanSupplier allowed,java.util.function.Consumer<Boolean> result){h.post(()->{
        if(closed||!watchAway||!allowed.getAsBoolean()||recording||photoBusy||configuring||countdownDeadline>0||burstRemaining>0||hold.active(SystemClock.elapsedRealtime())){result.accept(false);return;}
        closeNow(()->result.accept(true));
    });}
    void close(Runnable done){h.post(()->closeNow(done));}
    private void closeNow(Runnable done){
        closed=true;hold.stop();cancelTimer();burstRemaining=0;photoTicket++;generation++;if(recording)finishRecording(false);
        releaseSession();cleanupRecorder(false);if(camera!=null){camera.close();camera=null;}listener.screenLight(false,()->{});
        microphone.close();disk.shutdown();imageThread.quitSafely();cameraThread.quitSafely();done.run();
    }
}
