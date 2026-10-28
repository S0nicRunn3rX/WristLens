package com.daniil.wristlens.wear;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.gms.wearable.*;
import com.google.android.gms.tasks.Tasks;
import com.daniil.wristlens.shared.*;
import com.daniil.wristlens.core.FrameCodec;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class WatchActivity extends Activity implements MessageClient.OnMessageReceivedListener {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService streams=Executors.newCachedThreadPool();
    private MessageClient messages;
    private ChannelClient channels;
    private ChannelClient.Channel channel;
    private String node="",session="";
    private final String clientId=UUID.randomUUID().toString();private long clientSequence;
    private JSONObject state=new JSONObject();private LiveAudio liveAudio;private String audioError="";
    private PreviewView preview;
    private ShutterEffect shutterEffect;private CaptureFeedback captureFeedback;
    private TextView status,countdown;private RoundHud hud;private IconButton flashButton,timerButton,flipButton,phoneButton;private MediaClient media;private Gallery gallery;private String holdId="";
    private IconButton mode,shutter,more;
    private boolean videoMode=false,connected=false,opening=false,choosingPhone=false;
    private boolean manualDisconnected,autoLaunchAttempted;private int resumeGeneration;
    private volatile boolean active=false;
    private volatile int streamGeneration;
    private long lastState=0,lastFrame=0,lastDiscover=0,noticeUntil=0;
    private String notice="";
    private String lastMessage="";
    private final Map<String,Pending> pending=new HashMap<>();
    private record Pending(JSONObject command,long sent,boolean retried,boolean confirmSetting){}

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);messages=Wearable.getMessageClient(this);channels=Wearable.getChannelClient(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        liveAudio=new LiveAudio(this,this::showNotice);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);media=new MediaClient(this,()->node,this::showNotice);gallery=new Gallery(this,media);
        WatchLayout root=new WatchLayout(this);
        root.setBackgroundColor(Color.BLACK);preview=new PreviewView(this);preview.fillScreen(true);preview.viewportGestures(true);root.preview(preview);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));preview.onFocus((x,y)->command(Wire.object("op","focus","x",x,"y",y)));
        shutterEffect=new ShutterEffect(this);root.addView(shutterEffect,new FrameLayout.LayoutParams(-1,-1));
        captureFeedback=new CaptureFeedback(this,root,shutterEffect);
        hud=new RoundHud(this);root.addView(hud,new FrameLayout.LayoutParams(-1,-1));
        status=Controls.text(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_145),11,Color.WHITE);status.setGravity(Gravity.CENTER);status.setMaxLines(3);status.setShadowLayer(3,0,1,Color.BLACK);FrameLayout.LayoutParams text=new FrameLayout.LayoutParams(-1,dp(48),Gravity.CENTER);text.setMargins(dp(32),0,dp(32),0);root.addView(status,text);
        status.setAutoSizeTextTypeUniformWithConfiguration(dp(10),Math.max(dp(11),Math.round(status.getTextSize())),1,android.util.TypedValue.COMPLEX_UNIT_PX);
        countdown=Controls.text(this,"",42,ThemeColors.accent(this));countdown.setGravity(Gravity.CENTER);root.addView(countdown,new FrameLayout.LayoutParams(-1,dp(60),Gravity.CENTER));countdown.setVisibility(View.GONE);
        countdown.setMaxLines(1);countdown.setAutoSizeTextTypeUniformWithConfiguration(dp(24),Math.max(dp(42),Math.round(countdown.getTextSize())),1,android.util.TypedValue.COMPLEX_UNIT_PX);
        mode=new IconButton(this,"video",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_146),()->{if(state.optBoolean("recording"))command(Wire.object("op",state.optBoolean("paused")?"record_resume":"record_pause"));else{videoMode=!videoMode;render();}});
        shutter=new IconButton(this,"photo",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_147),()->{if(!connected){connectPhone();return;}if(state.optInt("countdown")>0||state.optInt("burstRemaining")>0)command(Wire.object("op","cancel"));else command(Wire.object("op",state.optBoolean("recording")||!videoMode?"photo":"record_start"));});shutter.hold(this::startHold,this::endHold);shutter.accessibilityAction(this::accessibleCapture);
        more=new IconButton(this,"menu",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_148),()->{if(connected&&state.optBoolean("recording"))command(Wire.object("op","record_stop"));else actions();});more.setOnLongClickListener(v->{actions();return true;});mode.setOnLongClickListener(v->{cameraSettings();return true;});
        flashButton=new IconButton(this,"flash_off",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_149),this::cycleFlash);timerButton=new IconButton(this,"timer0",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_043),this::cycleTimer);flipButton=new IconButton(this,"flip",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_150),()->{if(state.optBoolean("recording"))command(Wire.object("op","audio","value",!state.optBoolean("audio")));else cycleCamera();});phoneButton=new IconButton(this,"phone",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_151),this::connectPhone);
        flashButton.setOnLongClickListener(v->{Controls.flashSettings(this,state,true,this::settingsCommand);return true;});timerButton.setOnLongClickListener(v->{Controls.timerSettings(this,state,true,videoMode,this::settingsCommand);return true;});flipButton.setOnLongClickListener(v->{if(state.optBoolean("recording"))cameraSettings();else Controls.cameraSettings(this,state,true,this::settingsCommand);return true;});
        root.addControl(mode,.23f,.73f,48);root.addControl(shutter,.5f,.79f,56);root.addControl(more,.77f,.73f,48);
        root.addControl(flashButton,.25f,.35f,48);root.addControl(timerButton,.5f,.31f,48);root.addControl(flipButton,.75f,.35f,48);root.addControl(phoneButton,.5f,.59f,56);
        root.setFocusableInTouchMode(true);root.requestFocus();root.setOnGenericMotionListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_SCROLL&&e.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)){
                JSONObject c=activeCamera();double zoom=state.optDouble("zoom",1)-e.getAxisValue(MotionEvent.AXIS_SCROLL)*0.1;
                double next=Math.max(1,Math.min(c.optDouble("maxZoom",1),zoom));if(connected&&Math.abs(next-state.optDouble("zoom",1))>.001){ActionFeedback.tick(root);command(Wire.object("op","zoom","value",next));}return true;
            }return false;
        });setContentView(root);render();
    }
    private int dp(int n){return Controls.dp(this,n);}
    @Override protected void onResume(){super.onResume();captureFeedback.reset();active=true;manualDisconnected=false;autoLaunchAttempted=false;resumeGeneration++;media.resume();messages.addListener(this);AutoSave.enqueue(this);discover();main.post(heartbeat);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&liveAudio!=null)liveAudio.focusReturned();if(active&&focus)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    @Override protected void onPause(){liveAudio.stop();captureFeedback.reset();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);shutter.release();endHold();if(connected&&!session.isEmpty())messages.sendMessage(node,Wire.VISIBILITY,Wire.bytes(Wire.object("session",session,"visible",false,"client",clientId,"sequence",++clientSequence)));gallery.close();media.pause();active=false;resumeGeneration++;connected=false;main.removeCallbacks(heartbeat);messages.removeListener(this);closeStream();pending.clear();super.onPause();}
    @Override protected void onDestroy(){liveAudio.close();captureFeedback.close();gallery.destroy();media.close();closeStream();streams.shutdownNow();super.onDestroy();}
    private final Runnable heartbeat=new Runnable(){@Override public void run(){
        if(!active)return;long now=SystemClock.elapsedRealtime();
        if(!manualDisconnected&&!node.isEmpty())messages.sendMessage(node,Wire.HELLO,helloBytes()).addOnFailureListener(e->{});
        if(!manualDisconnected&&now-lastState>7000){connected=false;if(now-lastDiscover>5000)discover();}
        if(connected&&!session.isEmpty()&&channel==null&&!opening)openStream();updateAudio();
        if(channel!=null&&lastFrame>0&&now-lastFrame>7000){closeStream();preview.clear();}
        for(String id:new ArrayList<>(pending.keySet())){
            Pending p=pending.get(id);if(p==null)continue;
            if(now-p.sent>5000){pending.remove(id);showNotice(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_152));}
            else if(now-p.sent>1800&&!p.retried){pending.put(id,new Pending(p.command,p.sent,true,p.confirmSetting));send(p.command);}
        }
        render();main.postDelayed(this,1000);
    }};
    private void discover(){
        lastDiscover=SystemClock.elapsedRealtime();
        Wearable.getCapabilityClient(this).getCapability("wristlens_phone",CapabilityClient.FILTER_REACHABLE).addOnSuccessListener(info->{
            if(!active||manualDisconnected)return;List<Node> near=new ArrayList<>();for(Node n:info.getNodes())if(n.isNearby())near.add(n);
            if(near.isEmpty()){connected=false;Wearable.getNodeClient(this).getConnectedNodes().addOnSuccessListener(nodes->{
                if(!active||manualDisconnected)return;List<Node> paired=nodes.stream().filter(Node::isNearby).collect(java.util.stream.Collectors.toList());
                if(paired.size()==1){node=paired.get(0).getId();hello();}else{node="";render();}
            });return;}
            for(Node n:near)if(n.getId().equals(node)){hello();return;}
            if(near.size()==1){node=near.get(0).getId();hello();}
            else if(!choosingPhone){choosingPhone=true;String[] labels=near.stream().map(Node::getDisplayName).toArray(String[]::new);
                new AlertDialog.Builder(this).setTitle(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_153)).setItems(labels,(d,i)->{node=near.get(i).getId();hello();}).setOnDismissListener(d->choosingPhone=false).show();}
        }).addOnFailureListener(e->{if(active)showNotice(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_154));});
    }
    private void hello(){
        if(manualDisconnected)return;
        messages.sendMessage(node,Wire.HELLO,helloBytes()).addOnFailureListener(e->{});
        if(!autoLaunchAttempted){autoLaunchAttempted=true;int token=resumeGeneration;main.postDelayed(()->{
            if(active&&!connected&&!manualDisconnected&&token==resumeGeneration)media.launch("start","");
        },1200);}
    }
    private void connectPhone(){manualDisconnected=false;autoLaunchAttempted=true;media.launch("start","");hello();}
    private boolean accessibleCapture(){
        if(!active||!hasWindowFocus())return false;
        String op=com.daniil.wristlens.core.CapturePolicy.primaryAction(connected,videoMode,state.optBoolean("recording"),state.optBoolean("busy"),state.optInt("countdown"),shutter.holding());
        if(op.isEmpty()||!pending.isEmpty())return false;command(Wire.object("op",op));return true;
    }
    private void disconnected(){
        liveAudio.stop();captureFeedback.reset();manualDisconnected=true;autoLaunchAttempted=true;connected=false;session="";shutter.release();endHold();pending.clear();closeStream();preview.clear();render();
    }
    private void disconnect(){
        endHold();media.request(Wire.object("op","disconnect","session",session),reply->{disconnected();showNotice(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_155));});
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)ActionFeedback.apply(getWindow().getDecorView());return super.dispatchTouchEvent(event);}
    @Override public void onMessageReceived(MessageEvent event){
        String source=event.getSourceNodeId(),path=event.getPath();byte[] bytes=event.getData();main.post(()->{
            if(!active||!source.equals(node))return;
            try{
                JSONObject j=Wire.parse(bytes);
                if(Wire.STATE.equals(path)){
                    if("off".equals(j.optString("phase"))){if(session.equals(j.optString("session"))){disconnected();if("idle".equals(j.optString("reason"))){manualDisconnected=false;autoLaunchAttempted=false;hello();}}return;}
                    if(manualDisconnected)return;
                    String next=j.optString("session");if(next.isEmpty())return;
                    if(!next.equals(session)){closeStream();pending.clear();session=next;notice="";noticeUntil=0;lastMessage="";preview.clear();}
                    if(!j.has("cameras")&&j.optString("revision").equals(state.optString("revision")))j.put("cameras",state.optJSONArray("cameras"));
                    state=j;lastState=SystemClock.elapsedRealtime();connected=true;captureFeedback.state(j);updateAudio();
                    String msg=Strings.message(j.optString("message"));
                    if(!msg.equals(lastMessage)){
                        lastMessage=msg;
                        String lower=msg.toLowerCase(Locale.ROOT);
                        if(lower.contains("сохран")||lower.contains("ошиб")||lower.contains("не удалось")||lower.contains("отмен")||lower.contains("понижен")||lower.contains("кадр предпросмотра")||lower.contains("не поддерж")||lower.contains("saved")||lower.contains("error")||lower.contains("could not")||lower.contains("cancel")||lower.contains("reduced")||lower.contains("unsupported")){
                            notice=msg;noticeUntil=SystemClock.elapsedRealtime()+4000;
                        }
                    }
                    render();
                    if(channel==null&&!opening)openStream();
                }else if(Wire.ACK.equals(path)&&session.equals(j.optString("session"))){
                    acknowledge(j);
                }
            }catch(Exception ignored){}
        });
    }
    private void acknowledge(JSONObject reply){
        String id=reply.optString("id");if(!pending.containsKey(id)||"accepted".equals(reply.optString("status")))return;
        Pending completed=pending.remove(id);
        if("error".equals(reply.optString("status")))showNotice(reply.optString("message"));
        else if(completed.confirmSetting&&Controls.isSetting(completed.command.optString("op")))Controls.saved(this);
    }
    private void command(JSONObject c){command(c,false);}
    private void updateAudio(){liveAudio.update(active&&connected,state.optBoolean("previewAudio")&&state.optBoolean("microphoneGranted"),node,session);String e=state.optString("previewAudioError");if(!e.isEmpty()&&!e.equals(audioError))showNotice(e);audioError=e;}
    private void settingsCommand(JSONObject c){if("microphone_permission".equals(c.optString("op"))){media.launch("microphone","");return;}command(c,true);}
    private void command(JSONObject c,boolean confirmSetting){
        if(!connected||session.isEmpty()){showNotice(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_168));return;}
        // Avoid accidental double captures while an operation is still awaiting its ACK.
        String op=c.optString("op");if(pending.values().stream().anyMatch(p->p.command.optString("op").equals(op)))return;
        try{String id=UUID.randomUUID().toString();c.put("id",id);c.put("session",session);c.put("stateTime",state.optLong("phoneTime"));pending.put(id,new Pending(c,SystemClock.elapsedRealtime(),false,confirmSetting));send(c);}
        catch(JSONException e){showNotice(e.getMessage());}
    }
    private void send(JSONObject c){messages.sendMessage(node,Wire.COMMAND,Wire.bytes(c)).addOnFailureListener(e->showNotice(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_169)));}
    private void openStream(){
        if(!active||node.isEmpty()||session.isEmpty())return;opening=true;int token=++streamGeneration;
        channels.openChannel(node,Wire.PREVIEW+session).addOnSuccessListener(ch->{
            if(token!=streamGeneration||!active){channels.close(ch);return;}channel=ch;opening=false;lastFrame=SystemClock.elapsedRealtime();
            streams.execute(()->receive(ch,token));
        }).addOnFailureListener(e->{if(token==streamGeneration)opening=false;});
    }
    private void receive(ChannelClient.Channel ch,int token){
        try(DataInputStream in=new DataInputStream(Tasks.await(channels.getInputStream(ch),10,TimeUnit.SECONDS));
            DataOutputStream ack=new DataOutputStream(Tasks.await(channels.getOutputStream(ch),10,TimeUnit.SECONDS))){
            while(active&&token==streamGeneration){
                FrameCodec.Frame f=FrameCodec.read(in);
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(f.jpeg(),0,f.jpeg().length,bounds);
                if(bounds.outWidth!=f.width()||bounds.outHeight!=f.height())throw new IOException("JPEG bounds mismatch");
                Bitmap b=BitmapFactory.decodeByteArray(f.jpeg(),0,f.jpeg().length);if(b==null)throw new IOException("Invalid JPEG");
                CountDownLatch shown=new CountDownLatch(1);
                main.post(()->{if(active&&token==streamGeneration){preview.frame(b,f.rotation(),f.mirror());lastFrame=SystemClock.elapsedRealtime();}shown.countDown();});
                if(!shown.await(3,TimeUnit.SECONDS))throw new IOException("Display not responding");ack.writeLong(f.sequence());ack.flush();
            }
        }catch(Exception ignored){}finally{channels.close(ch);main.post(()->{if(token==streamGeneration){channel=null;opening=false;}});}
    }
    private void closeStream(){streamGeneration++;opening=false;if(channel!=null){channels.close(channel);channel=null;}}
    private JSONObject activeCamera(){JSONArray cams=state.optJSONArray("cameras");if(cams!=null)for(int i=0;i<cams.length();i++){JSONObject c=cams.optJSONObject(i);if(c!=null&&c.optString("id").equals(state.optString("camera")))return c;}return new JSONObject();}
    private byte[] helloBytes(){return Wire.bytes(Wire.object("client",clientId,"sequence",++clientSequence,"width",getResources().getDisplayMetrics().widthPixels,"height",getResources().getDisplayMetrics().heightPixels,"revision",state.optString("revision")));}
    private void render(){
        boolean rec=connected&&state.optBoolean("recording"),paused=state.optBoolean("paused"),busy=state.optBoolean("busy");int seconds=connected?state.optInt("countdown"):0;
        String clock=rec?String.format(Locale.ROOT,"%02d:%02d",state.optLong("elapsedMs")/60000,state.optLong("elapsedMs")/1000%60):new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());hud.update(connected?(rec?(paused?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_170):"● REC"):videoMode?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_171):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_172))+" · "+clock+String.format(Locale.ROOT," · %.1f×",state.optDouble("zoom",1)):"WRISTLENS · "+clock,rec);
        String text=SystemClock.elapsedRealtime()<noticeUntil?notice:!connected?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_173):state.optString("phase").equals("error")?state.optString("message"):SystemClock.elapsedRealtime()-lastFrame>3000?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_174):"";
        status.setText(text);status.setTranslationY(connected?0:-dp(34));status.setVisibility(seconds>0?View.GONE:View.VISIBLE);countdown.setText(String.format(Locale.getDefault(),"%d",seconds));countdown.setVisibility(seconds>0?View.VISIBLE:View.GONE);
        mode.icon(rec?(paused?"play":"pause"):(videoMode?"photo":"video"),rec?(paused?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_175):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_176)):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_177));more.icon(rec?"stop":"menu",rec?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_178):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_148));shutter.icon(seconds>0||state.optInt("burstRemaining")>0?"close":rec||!videoMode?"photo":"record",seconds>0?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_097):rec?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_179):videoMode?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_180):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_181));shutter.tint(videoMode&&!rec?0xffff7878:ThemeColors.accent(this));
        shutter.setEnabled(shutter.holding()||!busy||seconds>0);shutter.allowHold=shutter.holding()||(connected&&!busy&&!rec&&!videoMode&&seconds==0);mode.setEnabled(!busy&&seconds==0&&!shutter.holding());more.setEnabled(true);phoneButton.setVisibility(connected?View.GONE:View.VISIBLE);
        flashButton.icon("flash_"+state.optString("flash","off"),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_182)+Controls.flashLabel(state.optString("flash")));timerButton.icon("timer"+state.optInt(videoMode?"videoTimer":"photoTimer"),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_183)+state.optInt(videoMode?"videoTimer":"photoTimer")+com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_184));flashButton.setEnabled(connected&&!busy);timerButton.setEnabled(connected&&!rec&&seconds==0);flipButton.icon(rec?(state.optBoolean("audio")?"mic_on":"mic_off"):"flip",Strings.tr(rec?(state.optBoolean("audio")?com.daniil.wristlens.shared.R.string.mic_on:com.daniil.wristlens.shared.R.string.mic_off):com.daniil.wristlens.shared.R.string.wl_150));flipButton.setEnabled(connected&&!busy&&seconds==0&&!shutter.holding()&&(!rec||state.optBoolean("microphoneSwitchable")));
        if(!connected&&shutter.holding())shutter.release();
        for(View control:new View[]{mode,shutter,flashButton,timerButton,flipButton})control.setVisibility(connected?View.VISIBLE:View.GONE);
        preview.empty("");if(!connected)preview.clear();
    }
    private void cycleFlash(){JSONArray a=activeCamera().optJSONArray("flashModes");if(a==null)return;List<String> values=new ArrayList<>();for(int i=0;i<a.length();i++){String v=a.optString(i);if(state.optBoolean("recording")&&(v.equals("on")||v.equals("auto")))continue;values.add(v);}if(!values.isEmpty())command(Wire.object("op","flash","value",values.get((values.indexOf(state.optString("flash"))+1)%values.size())));}
    private void cycleTimer(){int[] values={0,2,3,5,10};int old=state.optInt(videoMode?"videoTimer":"photoTimer"),next=0;for(int i=0;i<values.length-1;i++)if(old==values[i])next=values[i+1];command(Wire.object("op","timers",videoMode?"video":"photo",next));}
    private void cycleCamera(){JSONArray a=state.optJSONArray("cameras");if(a==null||a.length()<2)return;for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optString("id").equals(state.optString("camera"))){command(Wire.object("op","camera","value",a.optJSONObject((i+1)%a.length()).optString("id")));return;}}
    private void startHold(){holdId=UUID.randomUUID().toString();command(Wire.object("op","hold_start","hold",holdId));main.post(holdPulse);}
    private void endHold(){if(holdId.isEmpty())return;String ended=holdId;holdId="";main.removeCallbacks(holdPulse);holdMessage(ended,true);main.postDelayed(()->holdMessage(ended,true),120);}
    private void holdMessage(String id,boolean stop){if(node.isEmpty()||session.isEmpty())return;messages.sendMessage(node,Wire.HOLD,Wire.bytes(Wire.object("session",session,"hold",id,"stop",stop)));}
    private final Runnable holdPulse=new Runnable(){public void run(){if(holdId.isEmpty())return;holdMessage(holdId,false);main.postDelayed(this,250);}};
    private void showNotice(String message){main.post(()->{if(!active)return;notice=Strings.message(message);noticeUntil=SystemClock.elapsedRealtime()+6000;render();Toast.makeText(this,Strings.message(message),Toast.LENGTH_LONG).show();});}
    private void cameraSettings(){
        Controls.settings(this,()->state,true,c->{
            if(!"capture_settings".equals(c.optString("op"))){settingsCommand(c);return;}
            media.request(c,r->{try{state.put("capture",r.optJSONObject("capture"));}catch(JSONException ignored){}Controls.saved(this);});
        });
        media.request(Wire.object("op","capture_settings"),r->{try{state.put("capture",r.optJSONObject("capture"));}catch(JSONException ignored){}},e->{});
    }
    private void actions(){
        Dialog d=new Dialog(this);ScrollView scroll=new ScrollView(this);LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setBackgroundColor(Controls.BG);scroll.addView(col);
        TextView title=Controls.text(this,Strings.tr(com.daniil.wristlens.shared.R.string.wl_148),18,ThemeColors.accent(this));title.setGravity(Gravity.CENTER);col.addView(title);
        ImageView image=new ImageView(this);image.setVisibility(View.GONE);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(Strings.tr(com.daniil.wristlens.shared.R.string.wl_186));col.addView(image,new LinearLayout.LayoutParams(-1,dp(72)));gallery.preview(image);
        col.addView(Controls.button(this,Strings.tr(com.daniil.wristlens.shared.R.string.wl_187),gallery::open));
        col.addView(Controls.button(this,Strings.tr(com.daniil.wristlens.shared.R.string.wl_098),this::cameraSettings));
        col.addView(Controls.button(this,Strings.tr(com.daniil.wristlens.shared.R.string.files_and_saving),()->SavingSettings.show(this,media,node,state.optJSONObject("storage"))));
        CheckBox vibration=Controls.checkBox(this);vibration.setText(Strings.tr(com.daniil.wristlens.shared.R.string.action_vibration));vibration.setTextColor(Color.WHITE);vibration.setChecked(ActionFeedback.enabled(this));col.addView(vibration);
        vibration.setOnCheckedChangeListener((v,on)->{ActionFeedback.enabled(this,on);ActionFeedback.apply(col);Controls.saved(this);});
        CheckBox sounds=Controls.checkBox(this);sounds.setText(Strings.tr(com.daniil.wristlens.shared.R.string.capture_sounds));sounds.setTextColor(Color.WHITE);sounds.setChecked(CaptureFeedback.enabled(this));col.addView(sounds);
        sounds.setOnCheckedChangeListener((v,on)->{CaptureFeedback.enabled(this,on);if(!on)captureFeedback.mute();Controls.saved(this);});
        col.addView(Controls.button(this,Strings.tr(com.daniil.wristlens.shared.R.string.wl_103),()->CaptureSettingsUi.help(this,true)));
        Button connection=Controls.button(this,"",()->{});Runnable[] refresh={null};refresh[0]=()->{if(!d.isShowing())return;connection.setText(Strings.tr(connected?com.daniil.wristlens.shared.R.string.wl_101:com.daniil.wristlens.shared.R.string.wl_189));main.postDelayed(refresh[0],500);};
        connection.setText(Strings.tr(connected?com.daniil.wristlens.shared.R.string.wl_101:com.daniil.wristlens.shared.R.string.wl_189));connection.setOnClickListener(v->{d.dismiss();if(connected)disconnect();else connectPhone();});col.addView(connection);
        AppCredits.add(this,col);col.addView(Controls.button(this,Strings.tr(com.daniil.wristlens.shared.R.string.wl_160),d::dismiss));
        Controls.show(this,d,scroll);main.post(refresh[0]);d.setOnDismissListener(x->main.removeCallbacks(refresh[0]));
    }
}
