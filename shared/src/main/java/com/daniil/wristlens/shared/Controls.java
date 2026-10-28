package com.daniil.wristlens.shared;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** Native, scrollable settings with generous round-screen insets. Values come from the phone. */
public final class Controls {
    public static final int GREEN=0xff82b1ff,BG=0xff080c12,CARD=0xff1c2531;
    public static int dp(android.content.Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static TextView text(android.content.Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    public static Button button(android.content.Context c,String s,Runnable click){
        Button b=new Button(c){@Override public boolean performClick(){if(!isEnabled())return false;ActionFeedback.press(this);return super.performClick();}};b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
        GradientDrawable d=new GradientDrawable();d.setColor(CARD);d.setCornerRadius(dp(c,24));b.setBackground(d);b.setOnClickListener(v->click.run());return b;
    }
    public static CheckBox checkBox(android.content.Context c){return new CheckBox(c){@Override public boolean performClick(){if(!isEnabled())return false;ActionFeedback.press(this);return super.performClick();}};}
    private static JSONObject active(JSONObject state){JSONArray all=state.optJSONArray("cameras");if(all!=null)for(int i=0;i<all.length();i++){JSONObject c=all.optJSONObject(i);if(c!=null&&c.optString("id").equals(state.optString("camera")))return c;}return new JSONObject();}
    private static void add(LinearLayout l,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(l.getContext(),8);l.addView(v,p);}
    public static void choose(Activity a,boolean round,String title,List<String> labels,Consumer<Integer> choice){choose(a,round,title,labels,true,choice);}
    private static void choose(Activity a,boolean round,String title,List<String> labels,boolean closeOnChoice,Consumer<Integer> choice){
        Dialog d=new Dialog(a);LinearLayout col=column(a,round,title);ScrollView s=new ScrollView(a);s.addView(col);
        for(int i=0;i<labels.size();i++){final int index=i;add(col,button(a,labels.get(i),()->{if(closeOnChoice)d.dismiss();choice.accept(index);}));}
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_160),d::dismiss));show(a,d,s);
    }
    private static LinearLayout column(Activity a,boolean round,String title){
        LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);int side=dp(a,round?30:22);l.setPadding(side,dp(a,round?12:24),side,dp(a,round?36:24));l.setBackgroundColor(BG);
        TextView heading=text(a,title,round?18:24,ThemeColors.accent(a));heading.setGravity(Gravity.CENTER);heading.setTypeface(null,Typeface.BOLD);add(l,heading);return l;
    }
    public static void show(Activity a,Dialog d,View content){
        if(a.getResources().getConfiguration().isScreenRound()){
            RoundMenuFrame frame=new RoundMenuFrame(a,content);frame.onBack(d::dismiss);content=frame;
        }
        ActionFeedback.apply(content);d.setContentView(content);d.show();if(d.getWindow()!=null){d.getWindow().setLayout(-1,-1);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);}
    }
    public static void confirm(Activity a,String title,String message,Runnable action){
        Dialog d=new Dialog(a);LinearLayout col=column(a,a.getResources().getConfiguration().isScreenRound(),title);
        add(col,text(a,message,13,Color.LTGRAY));
        add(col,button(a,Strings.tr(R.string.wl_216),()->{d.dismiss();action.run();}));
        add(col,button(a,Strings.tr(R.string.wl_218),d::dismiss));ScrollView scroll=new ScrollView(a);scroll.addView(col);show(a,d,scroll);
    }
    private static void folderPrompt(Activity a,Consumer<JSONObject> apply){
        Dialog d=new Dialog(a);LinearLayout col=column(a,a.getResources().getConfiguration().isScreenRound(),Strings.tr(R.string.wl_309));
        EditText input=new EditText(a);input.setSingleLine();input.setText("DCIM/");input.setTextColor(Color.WHITE);add(col,input);
        add(col,button(a,Strings.tr(R.string.wl_310),()->{apply.accept(Wire.object("op","storage","key","folder","value",input.getText().toString()));d.dismiss();}));
        add(col,button(a,Strings.tr(R.string.wl_218),d::dismiss));ScrollView scroll=new ScrollView(a);scroll.addView(col);show(a,d,scroll);
    }
    public static void notice(android.content.Context c,String message){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->Toast.makeText(c,message,Toast.LENGTH_SHORT).show());}
    public static void saved(android.content.Context c){notice(c,Strings.tr(R.string.setting_saved));}
    public static boolean isSetting(String op){return java.util.Set.of("camera","flash","zoom","focus_auto","focus_manual","timers","burst_config","photo_size","video_mode","stream","audio","preview_audio","capture_settings").contains(op);}
    public static void flashSettings(Activity a,JSONObject state,boolean round,Consumer<JSONObject> send){
        List<String> values=new ArrayList<>(),labels=new ArrayList<>();JSONArray modes=active(state).optJSONArray("flashModes");
        if(modes!=null)for(int i=0;i<modes.length();i++){String value=modes.optString(i);if(state.optBoolean("recording")&&(value.equals("auto")||value.equals("on")))continue;values.add(value);labels.add((value.equals(state.optString("flash"))?"✓ ":"")+flashLabel(value));}
        choose(a,round,Strings.tr(R.string.wl_149),labels,i->send.accept(Wire.object("op","flash","value",values.get(i))));
    }
    public static void cameraSettings(Activity a,JSONObject state,boolean round,Consumer<JSONObject> send){
        List<String> ids=new ArrayList<>(),labels=new ArrayList<>();JSONArray cameras=state.optJSONArray("cameras");
        if(cameras!=null)for(int i=0;i<cameras.length();i++){JSONObject camera=cameras.optJSONObject(i);if(camera!=null){ids.add(camera.optString("id"));labels.add((camera.optString("id").equals(state.optString("camera"))?"✓ ":"")+camera.optString("label"));}}
        choose(a,round,Strings.tr(R.string.wl_259),labels,i->send.accept(Wire.object("op","camera","value",ids.get(i))));
    }
    public static void timerSettings(Activity a,JSONObject state,boolean round,boolean video,Consumer<JSONObject> send){
        int[] seconds={0,2,3,5,10,15,30,60};List<String> labels=new ArrayList<>();for(int n:seconds)labels.add((state.optInt(video?"videoTimer":"photoTimer")==n?"✓ ":"")+(n==0?Strings.tr(R.string.wl_271):n+Strings.tr(R.string.wl_111)));
        choose(a,round,Strings.tr(video?R.string.timer_video_title:R.string.timer_photo_title),labels,i->send.accept(Wire.object("op","timers",video?"video":"photo",seconds[i])));
    }
    private static void previewSettings(Activity a,JSONObject state,boolean round,Consumer<JSONObject> apply){
        Dialog d=new Dialog(a);LinearLayout col=column(a,round,Strings.tr(R.string.wl_295));
        String[] keys={"eco","auto","detail"};int[] labels={R.string.preview_eco,R.string.preview_auto,R.string.preview_detail};
        for(int i=0;i<keys.length;i++){String key=keys[i];add(col,button(a,(state.optString("streamMode","auto").equals(key)?"✓ ":"")+Strings.tr(labels[i]),()->{apply.accept(Wire.object("op","stream","value",key));d.dismiss();}));}
        add(col,button(a,Strings.tr(R.string.wl_160),d::dismiss));ScrollView scroll=new ScrollView(a);scroll.addView(col);show(a,d,scroll);
    }
    public static void settings(Activity a,JSONObject state,boolean round,Consumer<JSONObject> send){settings(a,()->state,round,send);}
    public static void settings(Activity a,java.util.function.Supplier<JSONObject> source,boolean round,Consumer<JSONObject> send){SettingsPage.live(a,source,(state,apply,close)->cameraPage(a,state,round,apply,close),send);}
    private static View cameraPage(Activity a,JSONObject state,boolean round,Consumer<JSONObject> apply,Runnable close){
        JSONObject camera=active(state);LinearLayout col=column(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_257));
        for(boolean monitor:new boolean[]{false,true}){CheckBox b=new CheckBox(a);b.setText(Strings.tr(monitor?R.string.preview_audio:R.string.record_audio));b.setTextColor(Color.WHITE);b.setChecked(state.optBoolean(monitor?"previewAudio":"audio",!monitor));b.setEnabled(monitor||!state.optBoolean("recording")||state.optBoolean("microphoneSwitchable"));b.setOnCheckedChangeListener((v,on)->apply.accept(Wire.object("op",monitor?"preview_audio":"audio","value",on)));add(col,b);}
        if(!state.optBoolean("microphoneGranted"))add(col,button(a,Strings.tr(R.string.mic_permission),()->apply.accept(Wire.object("op","microphone_permission"))));
        TextView note=text(a,state.optString("photoSize")+" · "+state.optString("videoMode"),12,0xffa9b5c7);note.setGravity(Gravity.CENTER);add(col,note);
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_258)+camera.optString("label"),()->{
            JSONArray all=state.optJSONArray("cameras");List<String> names=new ArrayList<>(),ids=new ArrayList<>();if(all!=null)for(int i=0;i<all.length();i++){JSONObject c=all.optJSONObject(i);if(c!=null){names.add(c.optString("label"));ids.add(c.optString("id"));}}
            choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_259),names,i->apply.accept(Wire.object("op","camera","value",ids.get(i))));
        }));
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_260)+flashLabel(state.optString("flash")),()->{
            List<String> modes=new ArrayList<>(),labels=new ArrayList<>();JSONArray m=camera.optJSONArray("flashModes");
            if(m!=null)for(int i=0;i<m.length();i++){String v=m.optString(i);if(state.optBoolean("recording")&&(v.equals("auto")||v.equals("on")))continue;modes.add(v);labels.add(flashLabel(v));}
            choose(a,round,camera.optBoolean("screenFlash")?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_261):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_149),labels,i->apply.accept(Wire.object("op","flash","value",modes.get(i))));
        }));
        add(col,button(a,String.format(Locale.ROOT,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_262),state.optDouble("zoom",1)),()->slider(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_263),1,(float)camera.optDouble("maxZoom",1),(float)state.optDouble("zoom",1),v->apply.accept(Wire.object("op","zoom","value",v)))));
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_264),()->apply.accept(Wire.object("op","focus_auto"))));
        if(camera.optDouble("minFocusDiopters")>0)add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_265),()->slider(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_266),0,(float)camera.optDouble("minFocusDiopters"),(float)state.optDouble("focusDistance"),v->apply.accept(Wire.object("op","focus_manual","value",v)))));
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_267),()->sizeMenu(a,round,camera.optJSONArray("photos"),false,apply)));
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_268),()->sizeMenu(a,round,compatible(camera.optJSONArray("videos"),state.optJSONObject("storage")),true,apply)));
        for(boolean video:new boolean[]{false,true})add(col,button(a,(video?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_269):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_270))+state.optInt(video?"videoTimer":"photoTimer")+com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_111),()->{
            List<String> options=List.of(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_271),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_272),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_273),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_274),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_275),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_276),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_277),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_278));int[] times={0,2,3,5,10,15,30,60};
            choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_279),options,i->apply.accept(Wire.object("op","timers",video?"video":"photo",times[i])));
        }));
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_280)+state.optInt("burstCount",50)+com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_281),()->choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_282),List.of(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_283),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_284),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_285),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_286),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_287),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_288)),i->apply.accept(Wire.object("op","burst_config","count",new int[]{3,5,10,20,30,50}[i])))));
        List<String> intervals=new ArrayList<>();for(int ms:com.daniil.wristlens.core.BurstSettings.INTERVALS)intervals.add(ms+Strings.tr(R.string.wl_290));
        add(col,button(a,Strings.tr(R.string.wl_289)+state.optInt("burstInterval",com.daniil.wristlens.core.BurstSettings.DEFAULT_INTERVAL_MS)+Strings.tr(R.string.wl_290),()->choose(a,round,Strings.tr(R.string.wl_291),intervals,i->apply.accept(Wire.object("op","burst_config","interval",com.daniil.wristlens.core.BurstSettings.INTERVALS[i])))));
        add(col,button(a,Strings.tr(R.string.wl_295),()->previewSettings(a,state,round,apply)));
        CaptureSettingsUi.addSettings(a,col,state.optJSONObject("capture"),round,apply);
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_301),close));return col;
    }
    private static void sizeMenu(Activity a,boolean round,JSONArray modes,boolean video,Consumer<JSONObject> send){
        List<String> ratios=new ArrayList<>();if(modes!=null)for(int i=0;i<modes.length();i++){String ratio=modes.optJSONObject(i).optString("ratio");if(!ratios.contains(ratio))ratios.add(ratio);}
        choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_302),ratios,false,index->{
            List<String> labels=new ArrayList<>(),keys=new ArrayList<>();for(int i=0;i<modes.length();i++){JSONObject m=modes.optJSONObject(i);if(!m.optString("ratio").equals(ratios.get(index)))continue;
                labels.add(m.optInt("width")+" × "+m.optInt("height")+(video?" · "+m.optInt("fps")+" FPS"+(m.optBoolean("highSpeed")?" · HS":""):(m.optBoolean("maximum")?" · MAX":m.optBoolean("highResolution")?" · HR":"")));keys.add(m.optString("key"));}
            choose(a,round,video?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_303):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_304),labels,i->send.accept(Wire.object("op",video?"video_mode":"photo_size","value",keys.get(i))));
        });
    }
    private static JSONArray compatible(JSONArray all,JSONObject settings){JSONArray out=new JSONArray();String type=settings==null?"avc":settings.optString("videoType","avc");if(all!=null)for(int i=0;i<all.length();i++){JSONObject m=all.optJSONObject(i);JSONArray codecs=m.optJSONArray("codecs");if(codecs==null||codecs.toString().contains("\""+type+"\""))out.put(m);}return out;}
    public static void storage(Activity a,JSONObject settings,boolean round,SettingsPage.Sender send,Runnable picker){SettingsPage.edit(a,settings,(state,apply,close)->storagePage(a,state,round,apply,picker,close),send);}
    private static View storagePage(Activity a,JSONObject settings,boolean round,Consumer<JSONObject> apply,Runnable picker,Runnable close){LinearLayout l=column(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_305));
        storageFields(a,l,settings,round,apply,picker);add(l,button(a,Strings.tr(R.string.wl_160),close));return l;
    }
    public static void storageFields(Activity a,LinearLayout l,JSONObject settings,boolean round,Consumer<JSONObject> apply,Runnable picker){
        JSONObject state=settings==null?new JSONObject():settings;add(l,text(a,state.optString("folder","DCIM/Camera"),12,Color.LTGRAY));
        add(l,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_306),()->choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_307),List.of("DCIM/Camera","DCIM/WristLens",com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_308)),false,i->{if(i<2)apply.accept(Wire.object("op","storage","key","folder","value",i==0?"DCIM/Camera":"DCIM/WristLens"));else{folderPrompt(a,apply);}})));
        add(l,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_311),picker));
        add(l,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_312),()->choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_313),List.of("IMG/VID_YYYYMMDD_HHMMSS","…_HHMMSS_mmm","IMG/VID_YYYY-MM-DD_HH-mm-ss","WristLens_IMG/VID_YYYYMMDD_HHMMSS"),i->apply.accept(Wire.object("op","storage","key","pattern","value",new String[]{"standard","millis","iso","wristlens"}[i])))));
        add(l,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_314)+state.optString("photoType","jpeg"),()->choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_315),List.of(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_316),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_317),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_318)),i->apply.accept(Wire.object("op","storage","key","photoType","value",new String[]{"jpeg","png","webp"}[i])))));
        add(l,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_319)+state.optString("videoType","avc"),()->choose(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_320),List.of("MP4 · H.264","MP4 · HEVC","WebM · VP8"),i->apply.accept(Wire.object("op","storage","key","videoType","value",new String[]{"avc","hevc","webm"}[i])))));
    }
    public static String flashLabel(String s){return switch(s){case "auto"->com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_322);case "on"->com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_323);case "torch"->com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_324);default->com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_271);};}
    private static void slider(Activity a,boolean round,String title,float min,float max,float value,Consumer<Float> apply){
        Dialog d=new Dialog(a);LinearLayout col=column(a,round,title);TextView number=text(a,"",24,Color.WHITE);number.setGravity(Gravity.CENTER);add(col,number);
        SeekBar bar=new SeekBar(a);int steps=Math.max(1,Math.round((max-min)/0.05f));bar.setMax(steps);bar.setProgress(max<=min?0:Math.round((value-min)/(max-min)*steps));add(col,bar);
        Runnable update=()->number.setText(String.format(Locale.ROOT,"%.2f",min+(max-min)*bar.getProgress()/bar.getMax()));update.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean from){update.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_325),()->{float v=min+(max-min)*bar.getProgress()/bar.getMax();d.dismiss();apply.accept(v);}));add(col,button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_160),d::dismiss));ScrollView s=new ScrollView(a);s.addView(col);show(a,d,s);
    }
}
