package com.daniil.wristlens.shared;

import android.app.*;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import com.daniil.wristlens.core.WatermarkSpec;
import org.json.JSONObject;
import java.util.*;
import java.util.function.Consumer;

public final class CaptureSettingsUi {
    private static LinearLayout column(Activity a,boolean round,String title){
        LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);
        int side=Controls.dp(a,round?28:22);col.setPadding(side,Controls.dp(a,18),side,Controls.dp(a,36));col.setBackgroundColor(Controls.BG);
        add(col,Controls.text(a,title,round?18:24,ThemeColors.accent(a)));return col;
    }
    private static void add(LinearLayout c,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=Controls.dp(c.getContext(),10);c.addView(v,p);}
    public static void settings(Activity a,JSONObject options,boolean round,SettingsPage.Sender send){SettingsPage.edit(a,options,(s,apply,close)->page(a,s,round,apply,close),send);}
    private static View page(Activity a,JSONObject s,boolean round,Consumer<JSONObject> apply,Runnable close){LinearLayout col=column(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_100));
        addSettings(a,col,s,round,apply);add(col,Controls.button(a,Strings.tr(R.string.wl_160),close));return col;
    }
    public static void addSettings(Activity a,LinearLayout col,JSONObject options,boolean round,Consumer<JSONObject> apply){
        JSONObject s=options==null?new JSONObject():options;
        CheckBox background=Controls.checkBox(a);background.setText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_223));background.setTextColor(Color.WHITE);background.setChecked(s.optBoolean("background",true));
        background.setOnCheckedChangeListener((v,on)->apply.accept(Wire.object("op","capture_settings","key","background","value",on)));add(col,background);
        CheckBox watermark=Controls.checkBox(a);watermark.setText(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_225));watermark.setTextColor(Color.WHITE);watermark.setChecked(s.optBoolean("watermark"));
        watermark.setOnCheckedChangeListener((v,on)->apply.accept(Wire.object("op","capture_settings","key","watermark","value",on)));add(col,watermark);
        option(a,round,col,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_226),s.optString("corner","bottom_right"),List.of(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_227),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_228),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_229),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_230)),WatermarkSpec.CORNERS,"corner",apply);
        option(a,round,col,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_231),s.optString("color","white"),List.of(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_232),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_233)),WatermarkSpec.COLORS,"color",apply);
        option(a,round,col,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_234),s.optString("size","small"),List.of(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_235),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_236),com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_237)),WatermarkSpec.SIZES,"size",apply);
        for(String key:List.of("date","time")){
            CheckBox check=Controls.checkBox(a);check.setText(key.equals("date")?com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_238):com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_239));check.setTextColor(Color.WHITE);check.setChecked(s.optBoolean(key,true));
            check.setOnCheckedChangeListener((v,on)->apply.accept(Wire.object("op","capture_settings","key",key,"value",on)));add(col,check);
        }
        option(a,round,col,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_240),s.optString("dateFormat","dd.MM.yyyy"),WatermarkSpec.DATES,WatermarkSpec.DATES,"dateFormat",apply);
        option(a,round,col,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_241),s.optString("timeFormat","HH:mm:ss"),WatermarkSpec.TIMES,WatermarkSpec.TIMES,"timeFormat",apply);
    }
    private static void option(Activity a,boolean round,LinearLayout col,String title,String current,List<String> labels,List<String> values,String key,Consumer<JSONObject> apply){
        int index=values.indexOf(current);String value=index>=0?labels.get(index):current;
        add(col,Controls.button(a,title+" · "+value,()->Controls.choose(a,round,title,labels,i->apply.accept(Wire.object("op","capture_settings","key",key,"value",values.get(i))))));
    }
    public static void help(Activity a,boolean round){
        Dialog dialog=new Dialog(a);LinearLayout col=column(a,round,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_243));
        add(col,Controls.text(a,Strings.tr(R.string.help_preview_gestures),14,Color.WHITE));
        String[] paragraphs={
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_244),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_245),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_246),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_247),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_248),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_249),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_250),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_251),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_252),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_253),
            com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_254)
        };
        for(String p:paragraphs)add(col,Controls.text(a,p,14,Color.WHITE));
        for(int id:new int[]{R.string.help_audio,R.string.help_downloads,R.string.help_menu,R.string.help_capture_feedback,R.string.help_idle,R.string.help_haptics,R.string.screen_timeout_help,R.string.wl_224,R.string.wl_242,R.string.preview_help,R.string.wl_300,R.string.wl_321,R.string.copy_folder_help,R.string.copy_settings_help,R.string.wl_extra_02})add(col,Controls.text(a,Strings.tr(id),14,Color.WHITE));
        add(col,Controls.button(a,com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_160),dialog::dismiss));ScrollView scroll=new ScrollView(a);scroll.addView(col);Controls.show(a,dialog,scroll);
    }
}
