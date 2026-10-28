package com.daniil.wristlens.wear;
import android.app.*;import android.content.*;import android.graphics.Color;import android.widget.*;import com.daniil.wristlens.shared.*;import com.daniil.wristlens.core.CopyProfile;import java.util.*;
final class CopySettings {
 record Options(String folder,String photo,String video){}
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("watch_copies",0);}
 static Options read(Context c){SharedPreferences p=prefs(c);return new Options(p.getString("folder","DCIM/Camera"),p.getString("photo","original"),p.getString("video","original"));}
 static void add(Activity a,LinearLayout col){
  Button folder=Controls.button(a,"",()->{});Runnable refresh=()->folder.setText(t(com.daniil.wristlens.shared.R.string.copy_folder)+"\n"+read(a).folder());refresh.run();folder.setOnClickListener(v->folder(a,refresh));col.addView(folder);
  for(String kind:List.of("photo","video")){Button quality=Controls.button(a,"",()->{});Runnable update=()->quality.setText(t(kind.equals("photo")?com.daniil.wristlens.shared.R.string.copy_photo:com.daniil.wristlens.shared.R.string.copy_video)+"\n"+label(a,kind,prefs(a).getString(kind,"original")));update.run();quality.setOnClickListener(v->Controls.choose(a,true,t(kind.equals("photo")?com.daniil.wristlens.shared.R.string.copy_photo:com.daniil.wristlens.shared.R.string.copy_video),List.of(label(a,kind,"original"),label(a,kind,"balanced"),label(a,kind,"compact")),i->{prefs(a).edit().putString(kind,List.of("original","balanced","compact").get(i)).apply();update.run();Controls.saved(a);}));col.addView(quality);}
 }
 static String label(Context c,String kind,String value){return t(value.equals("original")?com.daniil.wristlens.shared.R.string.copy_original:kind.equals("photo")?value.equals("compact")?com.daniil.wristlens.shared.R.string.copy_photo_compact:com.daniil.wristlens.shared.R.string.copy_photo_balanced:value.equals("compact")?com.daniil.wristlens.shared.R.string.copy_video_compact:com.daniil.wristlens.shared.R.string.copy_video_balanced);}
 private static String t(int id){return Strings.tr(id);}
 static void saveFolder(Context c,String value){prefs(c).edit().putString("folder",CopyProfile.folder(value)).apply();}
 private static void folder(Activity a,Runnable refresh){Controls.choose(a,true,t(com.daniil.wristlens.shared.R.string.copy_folder),List.of("DCIM/Camera","DCIM/WristLens",t(com.daniil.wristlens.shared.R.string.copy_custom_folder)),i->{if(i<2){saveFolder(a,i==0?"DCIM/Camera":"DCIM/WristLens");refresh.run();Controls.saved(a);return;}
  Dialog d=new Dialog(a);LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.addView(Controls.text(a,t(com.daniil.wristlens.shared.R.string.copy_folder),18,ThemeColors.accent(a)));EditText input=new EditText(a);input.setTextColor(Color.WHITE);input.setSingleLine();input.setText(read(a).folder());col.addView(input);input.setHint("DCIM/Camera");
  col.addView(Controls.button(a,t(com.daniil.wristlens.shared.R.string.wl_310),()->{try{saveFolder(a,input.getText().toString());refresh.run();Controls.saved(a);d.dismiss();}catch(IllegalArgumentException e){input.setError(t(com.daniil.wristlens.shared.R.string.copy_folder_error));}}));col.addView(Controls.button(a,t(com.daniil.wristlens.shared.R.string.wl_160),d::dismiss));ScrollView scroll=new ScrollView(a);scroll.addView(col);Controls.show(a,d,scroll);
 });}
}
