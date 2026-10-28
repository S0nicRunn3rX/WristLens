package com.daniil.wristlens.wear;

import android.content.Context;
import android.media.*;
import android.view.*;
import com.daniil.wristlens.core.CaptureEvents;
import com.daniil.wristlens.shared.ActionFeedback;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class CaptureFeedback implements AutoCloseable {
    private final Context context;private final View target;private final ShutterEffect shutter;
    private final CaptureEvents.Cursor cursor=new CaptureEvents.Cursor();
    private final SoundPool sounds;
    private final Map<CaptureEvents.Kind,Integer> samples=new EnumMap<>(CaptureEvents.Kind.class);
    private final Set<Integer> loaded=ConcurrentHashMap.newKeySet();
    private boolean released;
    CaptureFeedback(Context c,View target,ShutterEffect shutter){
        context=c;this.target=target;this.shutter=shutter;
        sounds=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->{if(status==0)loaded.add(id);});
        int[] raw={R.raw.shutter,R.raw.burst_shutter,R.raw.record_start,R.raw.record_pause,R.raw.record_resume,R.raw.record_stop};
        for(CaptureEvents.Kind kind:CaptureEvents.Kind.values())samples.put(kind,sounds.load(c,raw[kind.ordinal()],1));
    }
    static boolean enabled(Context c){return c.getSharedPreferences("watch_ui",0).getBoolean("capture_sounds",true);}
    static void enabled(Context c,boolean on){c.getSharedPreferences("watch_ui",0).edit().putBoolean("capture_sounds",on).apply();}
    void state(JSONObject state){
        if(released)return;JSONArray data=state.optJSONArray("events");List<CaptureEvents.Event> events=new ArrayList<>();
        if(data!=null)for(int i=0;i<Math.min(data.length(),32);i++)try{JSONObject e=data.getJSONObject(i);events.add(new CaptureEvents.Event(e.getLong("id"),e.getLong("time"),CaptureEvents.Kind.valueOf(e.getString("kind"))));}catch(Exception ignored){}
        CaptureEvents.Event event=cursor.accept(state.optString("session"),state.optLong("phoneTime"),events);
        if(event!=null)play(event.kind());
    }
    private void play(CaptureEvents.Kind kind){
        if(kind==CaptureEvents.Kind.PHOTO||kind==CaptureEvents.Kind.BURST_PHOTO){shutter.fire();ActionFeedback.perform(target,kind==CaptureEvents.Kind.BURST_PHOTO?HapticFeedbackConstants.CLOCK_TICK:HapticFeedbackConstants.CONFIRM);}
        else ActionFeedback.perform(target,HapticFeedbackConstants.CONFIRM);
        Integer id=samples.get(kind);
        if(enabled(context)&&id!=null&&loaded.contains(id))sounds.play(id,1,1,1,0,1);
    }
    void reset(){cursor.reset();shutter.clear();if(!released)sounds.autoPause();}
    void mute(){if(!released)sounds.autoPause();}
    @Override public void close(){if(released)return;reset();released=true;sounds.release();loaded.clear();}
}
