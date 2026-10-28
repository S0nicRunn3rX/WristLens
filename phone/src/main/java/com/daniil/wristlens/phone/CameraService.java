package com.daniil.wristlens.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.OrientationEventListener;
import com.google.android.gms.wearable.*;
import com.daniil.wristlens.shared.Wire;
import com.daniil.wristlens.core.*;
import org.json.*;
import java.util.*;

public final class CameraService extends Service implements MessageClient.OnMessageReceivedListener,CameraEngine.Listener {
    interface Ui {void state(JSONObject s);void frame(FrameCodec.Frame f);void screenLight(boolean on,Runnable ready);boolean visible();}
    static volatile CameraService instance;
    volatile Ui ui;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final String session=UUID.randomUUID().toString();
    private final CommandLedger ledger=new CommandLedger();
    private final PresenceOrder presence=new PresenceOrder();
    private final IdleSession idleSession=new IdleSession();
    private MessageClient messages;
    private ChannelClient channels;
    private CameraEngine engine;
    private volatile FrameSender sender;private AudioSender audioSender;private long audioGeneration;private String audioError="";private Notification foregroundNotification;
    private String owner="",knownRevision="";
    private long lastSeen,started;
    private volatile boolean wasConnected,stopping;
    private boolean idleClosePending;
    private PowerManager.WakeLock wake;
    private OrientationEventListener orientation;
    volatile JSONObject state=Wire.object("phase","opening","message",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_135));

    @Override public void onCreate(){super.onCreate();instance=this;messages=Wearable.getMessageClient(this);channels=Wearable.getChannelClient(this);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"STOP".equals(intent.getAction())){stopSession();return START_NOT_STICKY;}
        if(engine!=null)return START_NOT_STICKY;
        boolean audio=new AudioOptions(this).record()&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("camera",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_136),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,PhoneActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CameraService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"camera").setSmallIcon(com.daniil.wristlens.phone.R.drawable.ic_camera)
                .setContentTitle(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_137)).setContentText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_138))
                .setOngoing(true).setContentIntent(open).addAction(new Notification.Action.Builder(null,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_139),stop).build()).build();
        foregroundNotification=notification;try{
            if(Build.VERSION.SDK_INT>=30)startForeground(7,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA|(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:0));
            else startForeground(7,notification);
        }
        catch(Exception e){state=Wire.object("phase","error","message",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_140)+e.getMessage());if(ui!=null)ui.state(state);stopSelf();return START_NOT_STICKY;}
        messages.addListener(this);channels.registerChannelCallback(channelCallback);
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WristLens:camera");wake.acquire(10*60*1000L);
        started=lastSeen=SystemClock.elapsedRealtime();engine=new CameraEngine(this,audio,this);
        orientation=new OrientationEventListener(this){@Override public void onOrientationChanged(int angle){if(angle!=ORIENTATION_UNKNOWN&&engine!=null)engine.orientation(((angle+45)/90*90)%360);}};
        if(orientation.canDetectOrientation())orientation.enable();main.post(tick);return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(stopping||engine==null)return;
        long now=SystemClock.elapsedRealtime();
        if(wasConnected&&now-lastSeen>8000){wasConnected=false;engine.connectionLost();closeStream();}
        if(audioSender!=null&&audioSender.inFlightSince>0&&now-audioSender.inFlightSince>3000)closeAudio();
        if(sender!=null&&sender.inFlightSince>0&&now-sender.inFlightSince>5000)closeStream();
        if(now-started>5*60*1000L){if(wake!=null&&wake.isHeld())wake.release();if(wake!=null)wake.acquire(10*60*1000L);started=now;}
        checkIdle(now);
        // Sessions used only from the phone retain their existing inactivity policy.
        if(owner.isEmpty()&&now-lastSeen>15*60*1000L&&!captureInProgress(state)&&(ui==null||!ui.visible())){stopSession();return;}
        engine.refreshState();main.postDelayed(this,1000);
    }};
    @Override public void onMessageReceived(MessageEvent event){
        if(stopping||engine==null)return;
        String node=event.getSourceNodeId(),path=event.getPath();byte[] bytes=event.getData();
        main.post(()->{
            if(stopping||engine==null)return;
            if(Wire.HELLO.equals(path)){
                if(bytes.length>1024)return;
                // Nearby nodes only: no camera preview over the cloud fallback.
                Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes->{
                    boolean nearby=nodes.stream().anyMatch(n->n.getId().equals(node)&&n.isNearby());
                    if(stopping||engine==null||!nearby||(!owner.isEmpty()&&!owner.equals(node)))return;
                    try{JSONObject hello=bytes.length==0?new JSONObject():Wire.parse(bytes);if(!presence.hello(hello.optString("client"),hello.optLong("sequence",-1)))return;engine.watchSize(hello.optInt("width",480),hello.optInt("height",480));knownRevision=hello.optString("revision");}catch(Exception ignored){}
                    owner=node;engine.watchVisible();lastSeen=SystemClock.elapsedRealtime();wasConnected=true;idleSession.update(lastSeen,true,false);sendState();
                });return;
            }
            if(Wire.VISIBILITY.equals(path)&&node.equals(owner)){try{JSONObject j=Wire.parse(bytes);if(session.equals(j.optString("session"))&&!j.optBoolean("visible",true)&&presence.hidden(j.optString("client"),j.optLong("sequence",-1))){wasConnected=false;engine.connectionLost();closeStream();checkIdle(SystemClock.elapsedRealtime());}}catch(Exception ignored){}return;}
            if(Wire.HOLD.equals(path)&&node.equals(owner)&&wasConnected){try{JSONObject j=Wire.parse(bytes);if(session.equals(j.optString("session")))engine.holdSignal(j.optString("hold"),j.optBoolean("stop"));}catch(Exception ignored){}return;}
            if(!Wire.COMMAND.equals(path)||!node.equals(owner)||!wasConnected)return;
            try{
                JSONObject command=Wire.parse(bytes);String id=command.getString("id");
                if(id.length()>100||!session.equals(command.optString("session")))return;
                lastSeen=SystemClock.elapsedRealtime();
                if(ledger.contains(id)){send(node,Wire.ACK,ledger.get(id).getBytes(java.nio.charset.StandardCharsets.UTF_8));return;}
                long age=SystemClock.elapsedRealtime()-command.optLong("stateTime",0);
                if(age<0||age>8000){
                    JSONObject expired=Wire.object("id",id,"session",session,"status","error","message",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_141));
                    ledger.put(id,expired.toString());send(node,Wire.ACK,Wire.bytes(expired));return;
                }
                JSONObject pending=Wire.object("id",id,"status","accepted","session",session);
                ledger.put(id,pending.toString());send(node,Wire.ACK,Wire.bytes(pending));
                engine.command(command,error->main.post(()->{
                    JSONObject ack=Wire.object("id",id,"session",session,"status",error.isEmpty()?"ok":"error","message",error);
                    ledger.put(id,ack.toString());send(node,Wire.ACK,Wire.bytes(ack));sendState();
                }));
            }catch(JSONException ignored){}
        });
    }
    private final ChannelClient.ChannelCallback channelCallback=new ChannelClient.ChannelCallback(){
        @Override public void onChannelOpened(ChannelClient.Channel channel){main.post(()->{
            if(channel.getPath().startsWith(Wire.FILE))return;
            if(channel.getPath().startsWith(Wire.AUDIO)){
                if(stopping||engine==null||!wasConnected||!channel.getNodeId().equals(owner)||!channel.getPath().equals(Wire.AUDIO+session)||!new AudioOptions(CameraService.this).preview()||checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){channels.close(channel);return;}
                closeAudio();long token=audioGeneration;try{audioSender=new AudioSender(engine.microphone,channels,channel,e->main.post(()->{if(token!=audioGeneration)return;audioError=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.audio_unavailable);closeAudio();}));audioError="";}catch(Exception e){channels.close(channel);audioError=com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.audio_unavailable);}return;
            }
            if(stopping||!wasConnected||!channel.getNodeId().equals(owner)||!channel.getPath().equals(Wire.PREVIEW+session)){channels.close(channel);return;}
            closeStream();sender=new FrameSender(channels,channel,()->{});
        });}
    };
    private void send(String node,String path,byte[] bytes){if(!node.isEmpty())messages.sendMessage(node,path,bytes).addOnFailureListener(e->{});}
    private void sendState(){if(wasConnected)try{JSONObject out=new JSONObject(state.toString());String revision=Integer.toHexString(out.optJSONArray("cameras")==null?0:out.optJSONArray("cameras").toString().hashCode());out.put("revision",revision);if(revision.equals(knownRevision))out.remove("cameras");send(owner,Wire.STATE,Wire.bytes(out));}catch(Exception ignored){}}
    @Override public void state(JSONObject next){main.post(()->{
        if(stopping)return;
        try{next.put("session",session);next.put("connected",wasConnected);next.put("phoneTime",SystemClock.elapsedRealtime());}catch(JSONException ignored){}
        if(!next.optBoolean("previewAudio")||!next.optBoolean("microphoneGranted"))closeAudio();try{next.put("previewAudioError",audioError);}catch(JSONException ignored){}state=next;checkIdle(SystemClock.elapsedRealtime());Ui view=ui;if(view!=null)view.state(next);sendState();
    });}
    @Override public void frame(FrameCodec.Frame f){FrameSender s=sender;if(s!=null)s.offer(f);Ui view=ui;if(view!=null&&view.visible())view.frame(f);}
    @Override public boolean screenAvailable(){Ui view=ui;return view!=null&&view.visible();}
    @Override public void screenLight(boolean on,Runnable ready){main.post(()->{Ui view=ui;if(view!=null&&view.visible())view.screenLight(on,ready);else ready.run();});}
    void local(JSONObject command,java.util.function.Consumer<String> reply){main.post(()->{if(engine!=null&&!stopping){lastSeen=SystemClock.elapsedRealtime();engine.command(command,e->main.post(()->reply.accept(e)));}else reply.accept(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_142));});}
    void disconnect(String node,String expectedSession,java.util.function.Consumer<String> reply){main.post(()->{
        if((!owner.isEmpty()&&!owner.equals(node))||(!expectedSession.isEmpty()&&!session.equals(expectedSession))){reply.accept(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_143));return;}
        stopSession();reply.accept("");
    });}
    void screenHidden(){if(engine!=null)engine.screenHidden();}
    static boolean captureInProgress(JSONObject s){return s.optBoolean("recording")||s.optBoolean("paused")||s.optBoolean("busy")||s.optBoolean("holding")||s.optInt("countdown")>0||!s.optString("timerAction").isEmpty()||s.optInt("burstRemaining")>0;}
    private void checkIdle(long now){
        if(stopping||engine==null||owner.isEmpty())return;
        if(!idleSession.update(now,wasConnected,captureInProgress(state))||idleClosePending)return;
        idleClosePending=true;
        // Recheck on the camera thread: a just-started capture must never be closed by an old state snapshot.
        engine.closeIfIdle(()->!wasConnected&&!stopping,closed->main.post(()->{
            idleClosePending=false;if(closed){engine=null;stopSession("idle");}
        }));
    }
    void microphoneGranted(){if(foregroundNotification==null)return;if(Build.VERSION.SDK_INT>=30)startForeground(7,foregroundNotification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA|ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(7,foregroundNotification);}
    private void closeAudio(){audioGeneration++;AudioSender s=audioSender;audioSender=null;if(s!=null)s.close();}
    private void closeStream(){closeAudio();FrameSender s=sender;sender=null;if(s!=null)s.close();}
    void stopSession(){stopSession("manual");}
    private void stopSession(String reason){
        if(stopping)return;stopping=true;send(owner,Wire.STATE,Wire.bytes(Wire.object("phase","off","session",session,"reason",reason,"message",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_144),"connected",false)));wasConnected=false;closeStream();main.removeCallbacks(tick);
        if(engine!=null){CameraEngine e=engine;engine=null;e.close(()->main.post(this::stopSelf));}else stopSelf();
    }
    @Override public void onDestroy(){
        stopping=true;main.removeCallbacksAndMessages(null);messages.removeListener(this);channels.unregisterChannelCallback(channelCallback);closeStream();
        if(engine!=null){engine.close(()->{});engine=null;}
        if(orientation!=null)orientation.disable();if(wake!=null&&wake.isHeld())wake.release();
        Ui view=ui;if(view!=null){view.screenLight(false,()->{});view.state(Wire.object("phase","off","message",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_144)));}ui=null;
        instance=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
