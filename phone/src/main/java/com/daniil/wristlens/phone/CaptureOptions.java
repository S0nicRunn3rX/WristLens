package com.daniil.wristlens.phone;

import android.content.Context;
import android.content.SharedPreferences;
import com.daniil.wristlens.core.WatermarkSpec;
import com.daniil.wristlens.shared.Wire;
import org.json.JSONObject;

final class CaptureOptions {
    private final SharedPreferences prefs;
    CaptureOptions(Context context){prefs=context.getSharedPreferences("capture",Context.MODE_PRIVATE);}
    boolean background(){return prefs.getBoolean("background",true);}
    WatermarkSpec watermark(){return new WatermarkSpec(prefs.getBoolean("watermark",false),
            prefs.getString("corner","bottom_right"),prefs.getString("color","white"),prefs.getString("size","small"),
            prefs.getBoolean("date",true),prefs.getBoolean("time",true),
            prefs.getString("dateFormat","dd.MM.yyyy"),prefs.getString("timeFormat","HH:mm:ss"));}
    JSONObject json(){WatermarkSpec w=watermark();return Wire.object("background",background(),"watermark",w.enabled(),
            "corner",w.corner(),"color",w.color(),"size",w.size(),"date",w.date(),"time",w.time(),
            "dateFormat",w.dateFormat(),"timeFormat",w.timeFormat());}
    synchronized void apply(JSONObject command)throws Exception{
        String key=command.getString("key");JSONObject next=json();Object value=command.get("value");
        boolean bool=java.util.List.of("background","watermark","date","time").contains(key);
        if(!next.has(key)|| (bool?!(value instanceof Boolean):!(value instanceof String)))throw new IllegalArgumentException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_121));
        next.put(key,value);
        new WatermarkSpec(next.getBoolean("watermark"),next.getString("corner"),next.getString("color"),next.getString("size"),
                next.getBoolean("date"),next.getBoolean("time"),next.getString("dateFormat"),next.getString("timeFormat"));
        SharedPreferences.Editor edit=prefs.edit();if(bool)edit.putBoolean(key,(Boolean)value);else edit.putString(key,(String)value);
        if(!edit.commit())throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_122));
    }
}
