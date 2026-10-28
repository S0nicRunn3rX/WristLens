package com.daniil.wristlens.shared;
import android.app.*;import android.os.*;import android.view.View;import android.widget.ScrollView;import org.json.JSONObject;import java.util.function.*;
public final class SettingsPage {
 public interface Sender{void send(JSONObject command,Consumer<JSONObject> confirmed);}
 public interface Renderer{View render(JSONObject state,Consumer<JSONObject> apply,Runnable close);}
 public static void edit(Activity a,JSONObject initial,Renderer renderer,Sender sender){
  JSONObject[] state={initial==null?new JSONObject():initial};Dialog d=new Dialog(a);ScrollView scroll=new ScrollView(a);boolean[] pending={false};Runnable[] paint={null};
  Consumer<JSONObject> apply=c->{if(pending[0])return;pending[0]=true;sender.send(c,result->a.runOnUiThread(()->{pending[0]=false;if(result!=null){state[0]=result;Controls.saved(a);}if(d.isShowing())paint[0].run();}));};
  paint[0]=()->replace(scroll,renderer.render(state[0],apply,d::dismiss));paint[0].run();Controls.show(a,d,scroll);
 }
 public static void live(Activity a,Supplier<JSONObject> source,Renderer renderer,Consumer<JSONObject> send){
  Dialog d=new Dialog(a);ScrollView scroll=new ScrollView(a);Handler h=new Handler(Looper.getMainLooper());String[] last={""};
  Runnable tick=new Runnable(){public void run(){JSONObject s=source.get();if(s==null)s=new JSONObject();StringBuilder key=new StringBuilder();for(String k:new String[]{"camera","cameras","flash","zoom","focus","focusDistance","photoSize","videoMode","photoTimer","videoTimer","burstCount","burstInterval","streamMode","recording","paused","busy","audio","previewAudio","microphoneGranted","microphoneSwitchable","storage","capture"})key.append(s.opt(k)).append('|');if(!key.toString().equals(last[0])){last[0]=key.toString();replace(scroll,renderer.render(s,c->{send.accept(c);last[0]="";},d::dismiss));}if(d.isShowing())h.postDelayed(this,400);}};
  tick.run();Controls.show(a,d,scroll);h.postDelayed(tick,400);d.setOnDismissListener(x->h.removeCallbacks(tick));
 }
 private static void replace(ScrollView s,View v){int y=s.getScrollY();s.removeAllViews();s.addView(v);s.post(()->s.scrollTo(0,y));}
}
