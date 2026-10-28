package com.daniil.wristlens.phone;
import android.content.*;
final class AudioOptions {
 private final SharedPreferences p;
 AudioOptions(Context c){p=c.getSharedPreferences("audio_options",0);if(!p.contains("record"))p.edit().putBoolean("record",c.getSharedPreferences("PhoneActivity",0).getBoolean("audio",true)).apply();}
 boolean record(){return p.getBoolean("record",true);}boolean preview(){return p.getBoolean("preview",false);}
 void record(boolean on){p.edit().putBoolean("record",on).apply();}void preview(boolean on){p.edit().putBoolean("preview",on).apply();}
}
