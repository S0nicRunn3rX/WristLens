package com.daniil.wristlens.phone;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.daniil.wristlens.shared.*;
import com.daniil.wristlens.core.FrameCodec;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PhoneActivity extends Activity implements CameraService.Ui {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean drawing=new AtomicBoolean();
    private PreviewView preview;
    private TextView status,details;
    private Button start,record,pause,photo,stop;
    private CheckBox microphone;private boolean updatingMicrophone;
    private View light;
    private volatile boolean visible;
    private JSONObject current=new JSONObject();
    private float previousBrightness=-1;
    private boolean lit;private android.net.Uri pendingLink;
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);pendingLink=getIntent().getData();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Controls.BG);ScrollView scroll=new ScrollView(this);root.addView(scroll);
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(22),dp(20),dp(22),dp(24));scroll.addView(col);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title=Controls.text(this,"WristLens",32,ThemeColors.accent(this));title.setTypeface(null,Typeface.BOLD);add(col,title);
        add(col,Controls.text(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_087),15,0xffa9b5c7));
        preview=new PreviewView(this);col.addView(preview,new LinearLayout.LayoutParams(-1,dp(260)));preview.onFocus((x,y)->send(Wire.object("op","focus","x",x,"y",y)));
        status=Controls.text(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_088),18,Color.WHITE);add(col,status);
        details=Controls.text(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_089),13,0xffa9b5c7);add(col,details);
        microphone=new CheckBox(this);microphone.setText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.record_audio));microphone.setTextColor(Color.WHITE);microphone.setChecked(new AudioOptions(this).record());microphone.setOnCheckedChangeListener((v,on)->{if(updatingMicrophone)return;if(CameraService.instance!=null)send(Wire.object("op","audio","value",on));else new AudioOptions(this).record(on);});add(col,microphone);
        start=Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_091),()->{if(CameraService.instance!=null)CameraService.instance.stopSession();else requestStart();});add(col,start);
        LinearLayout row=row(col);photo=Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_092),()->send(Wire.object("op","photo")));record=Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_093),()->send(Wire.object("op","record_start")));button(row,photo);button(row,record);
        row=row(col);pause=Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_094),()->send(Wire.object("op",current.optBoolean("paused")?"record_resume":"record_pause")));stop=Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_095),()->send(Wire.object("op","record_stop")));button(row,pause);button(row,stop);
        row=row(col);button(row,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_096),()->send(Wire.object("op","burst"))));button(row,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_097),()->send(Wire.object("op","cancel"))));
        add(col,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_098),()->Controls.settings(this,()->{try{JSONObject s=new JSONObject(current.toString()).put("capture",new CaptureOptions(this).json()).put("microphoneGranted",checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);if(CameraService.instance==null)s.put("audio",new AudioOptions(this).record()).put("previewAudio",new AudioOptions(this).preview());return s;}catch(JSONException e){return current;}},false,c->{if("capture_settings".equals(c.optString("op")))captureSettings(c,result->{if(result!=null)Controls.saved(this);});else send(c);})));
        add(col,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_099),()->Controls.storage(this,new MediaFiles(this).settings(),false,this::storage,this::pickFolder)));
        add(col,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_101),()->{CameraService camera=CameraService.instance;if(camera!=null)camera.stopSession();else toast(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_102));}));
        add(col,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_103),()->CaptureSettingsUi.help(this,false)));
        add(col,Controls.button(this,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_104),this::shareDiagnostics));
        AppCredits.add(this,col);
        light=new View(this);light.setBackgroundColor(Color.WHITE);light.setVisibility(View.GONE);light.setClickable(false);root.addView(light,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    }
    private int dp(int n){return Controls.dp(this,n);}
    private void add(LinearLayout l,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);l.addView(v,p);}
    private LinearLayout row(LinearLayout l){LinearLayout r=new LinearLayout(this);add(l,r);return r;}
    private void button(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),0,dp(3),0);row.addView(b,p);}
    private void requestStart(){
        ArrayList<String> missing=new ArrayList<>();if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.CAMERA);
        if((new AudioOptions(this).record()||new AudioOptions(this).preview())&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.RECORD_AUDIO);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),1);return;}startCamera();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);
        if(request==91){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){CameraService s=CameraService.instance;if(s!=null){s.microphoneGranted();s.local(Wire.object("op","audio","value",new AudioOptions(this).record()),this::toast);}}return;}
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)microphone.setChecked(false);startCamera();}
        else status.setText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_106));
    }
    private void startCamera(){try{startForegroundService(new Intent(this,CameraService.class).putExtra("audio",microphone.isChecked()));}catch(Exception e){status.setText(e.getMessage());}}
    private void requestMicrophone(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},91);else if(CameraService.instance!=null)CameraService.instance.microphoneGranted();}
    private void send(JSONObject command){if("microphone_permission".equals(command.optString("op"))){requestMicrophone();return;}CameraService s=CameraService.instance;if(s==null){String op=command.optString("op");if(op.equals("audio")||op.equals("preview_audio")){boolean on=command.optBoolean("value");AudioOptions a=new AudioOptions(this);if(op.equals("audio"))a.record(on);else a.preview(on);if(on&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestMicrophone();Controls.saved(this);return;}toast(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_107));return;}s.local(command,error->{if(!error.isEmpty())toast(error);else if(Controls.isSetting(command.optString("op")))Controls.saved(this);});}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final Runnable connect=new Runnable(){@Override public void run(){if(!visible)return;CameraService s=CameraService.instance;
        if(s!=null){s.ui=PhoneActivity.this;state(s.state);start.setText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_108));microphone.setEnabled(!current.optBoolean("busy")&&(!current.optBoolean("recording")||current.optBoolean("microphoneSwitchable")));}
        else{start.setText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_091));microphone.setEnabled(true);}
        main.postDelayed(this,500);
    }};
    @Override protected void onResume(){super.onResume();visible=true;main.post(connect);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(connect);CameraService s=CameraService.instance;if(s!=null&&s.ui==this){s.ui=null;s.screenHidden();}screenLight(false,()->{});super.onPause();}
    @Override public boolean visible(){return visible;}
    @Override public void state(JSONObject s){current=s;updatingMicrophone=true;microphone.setChecked(s.optBoolean("audio",new AudioOptions(this).record()));updatingMicrophone=false;String phase=s.optString("phase");String clock=String.format(Locale.ROOT,"%02d:%02d",s.optLong("elapsedMs")/60000,(s.optLong("elapsedMs")/1000)%60);
        status.setText(s.optBoolean("recording")?(s.optBoolean("paused")?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_109):"● REC · ")+clock:s.optInt("countdown")>0?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_110)+s.optInt("countdown")+com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_111):s.optString("message"));
        details.setText(String.format(Locale.getDefault(),"%s · %s · %s\n%s",s.optBoolean("connected")?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_112):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_113),s.optString("photoSize"),s.optString("videoMode"),s.optString("message")));
        pause.setText(s.optBoolean("paused")?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_114):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_094));pause.setEnabled(s.optBoolean("recording"));stop.setEnabled(s.optBoolean("recording")||s.optInt("countdown")>0);
        record.setEnabled(!s.optBoolean("recording")&&!s.optBoolean("busy"));photo.setEnabled(!s.optBoolean("busy")&&!phase.equals("off"));
    }
    @Override public void frame(FrameCodec.Frame f){if(!drawing.compareAndSet(false,true))return;Bitmap b=BitmapFactory.decodeByteArray(f.jpeg(),0,f.jpeg().length);main.post(()->{if(visible)preview.frame(b,f.rotation(),f.mirror());drawing.set(false);});}
    @Override public void screenLight(boolean on,Runnable ready){
        if(on&&!lit){previousBrightness=getWindow().getAttributes().screenBrightness;lit=true;}else if(!on)lit=false;
        light.setVisibility(on?View.VISIBLE:View.GONE);WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=on?1:previousBrightness;getWindow().setAttributes(p);
        main.postDelayed(ready,on?180:0);
    }
    private void captureSettings(JSONObject j,java.util.function.Consumer<JSONObject> done){CameraService camera=CameraService.instance;if(camera!=null){camera.local(j,error->{if(!error.isEmpty()){toast(error);done.accept(null);}else done.accept(new CaptureOptions(this).json());});return;}try{new CaptureOptions(this).apply(j);}catch(Exception e){toast(e.getMessage());done.accept(null);return;}done.accept(new CaptureOptions(this).json());}
    private void storage(JSONObject j,java.util.function.Consumer<JSONObject> done){CameraService camera=CameraService.instance;if(camera!=null){camera.local(j,error->{if(!error.isEmpty())toast(error);done.accept(new MediaFiles(this).settings());});return;}try{new MediaFiles(this).setting(j);}catch(Exception e){toast(e.getMessage());}done.accept(new MediaFiles(this).settings());}
    private void pickFolder(){if(current.optBoolean("recording")||current.optBoolean("busy")||current.optBoolean("holding")||current.optInt("countdown")>0||current.optInt("burstRemaining")>0){toast(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_115));return;}startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),44);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==44&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{android.net.Uri uri=data.getData();int grants=0;if((data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)grants|=Intent.FLAG_GRANT_READ_URI_PERMISSION;if((data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)grants|=Intent.FLAG_GRANT_WRITE_URI_PERMISSION;getContentResolver().takePersistableUriPermission(uri,grants);new MediaFiles(this).tree(uri);toast(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_116));}catch(Exception e){toast(e.getMessage());}}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);pendingLink=intent.getData();}
    @Override protected void onPostResume(){super.onPostResume();android.net.Uri uri=pendingLink;pendingLink=null;if(uri==null||!"wristlens".equals(uri.getScheme())||!"phone".equals(uri.getHost()))return;String token=uri.getQueryParameter("ticket");if(token==null)return;try{String value=Tickets.consume(this,token,"launch","").getString("value");if(value.equals("start")){if(CameraService.instance==null)requestStart();}else if(value.equals("microphone"))requestMicrophone();else if(value.equals("folder"))pickFolder();else if(value.startsWith("share:")){shareFiles(new JSONArray(value.substring(6)));}else if(value.startsWith("media:")){MediaFiles.Entry e=new MediaFiles(this).find(value.substring(6));startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(e.uri(),e.mime()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}}catch(Exception e){toast(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_117)+e.getMessage());}}
    private void shareFiles(JSONArray ids)throws Exception{
        ArrayList<android.net.Uri> uris=new ArrayList<>();String type=null;MediaFiles files=new MediaFiles(this);for(int i=0;i<ids.length();i++){MediaFiles.Entry e=files.find(ids.getString(i));uris.add(e.uri());String family=e.mime().startsWith("video/")?"video/*":"image/*";type=type==null?family:type.equals(family)?type:"*/*";}
        if(uris.isEmpty())return;Intent intent=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE).setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(uris.size()==1)intent.putExtra(Intent.EXTRA_STREAM,uris.get(0));else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);ClipData clip=ClipData.newUri(getContentResolver(),"WristLens",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));intent.setClipData(clip);startActivity(Intent.createChooser(intent,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_extra_22)));
    }
    private void shareDiagnostics(){
        JSONObject report=Wire.object("app","WristLens "+AppCredits.version(this),"manufacturer",Build.MANUFACTURER,"model",Build.MODEL,"android",Build.VERSION.RELEASE,"sdk",Build.VERSION.SDK_INT,"state",current,"storage",new MediaFiles(this).settings());
        try{report.put("cameraApi",new JSONArray(getSharedPreferences("diagnostics",0).getString("cameraApi","[]")));}catch(Exception ignored){}
        try{JSONObject copy=new JSONObject(report.toString());JSONObject s=copy.optJSONObject("state");if(s!=null)s.remove("session");
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_extra_50)).putExtra(Intent.EXTRA_TEXT,copy.toString(2));startActivity(Intent.createChooser(send,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_118)));
        }catch(Exception e){toast(e.getMessage());}
    }
}
