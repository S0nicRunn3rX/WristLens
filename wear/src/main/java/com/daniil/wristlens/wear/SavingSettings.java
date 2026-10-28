package com.daniil.wristlens.wear;

import android.app.Activity;
import android.view.Gravity;
import android.widget.*;
import com.daniil.wristlens.shared.*;
import org.json.*;

/** One page for phone originals and watch copies. Local options remain available offline. */
final class SavingSettings {
    static void show(Activity a,MediaClient media,String node,JSONObject initial){
        JSONObject[] storage={initial==null?new JSONObject():initial};
        SettingsPage.live(a,()->Wire.object("storage",storage[0]),(s,apply,close)->{
            LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);
            heading(a,col,com.daniil.wristlens.shared.R.string.files_and_saving,18);
            heading(a,col,com.daniil.wristlens.shared.R.string.saving_phone,14);
            Controls.storageFields(a,col,s.optJSONObject("storage"),true,apply,()->media.launch("folder",""));
            heading(a,col,com.daniil.wristlens.shared.R.string.saving_watch,14);
            AutoSave.addSettings(a,col,media,node);
            col.addView(Controls.button(a,Strings.tr(com.daniil.wristlens.shared.R.string.wl_160),close));return col;
        },command->media.request(command,result->{storage[0]=result.optJSONObject("storage");Controls.saved(a);}));
        media.request(Wire.object("op","list"),result->storage[0]=result.optJSONObject("storage"),error->{});
    }
    private static void heading(Activity a,LinearLayout col,int id,int size){TextView title=Controls.text(a,Strings.tr(id),size,ThemeColors.accent(a));title.setGravity(Gravity.CENTER);col.addView(title,new LinearLayout.LayoutParams(-1,-2));}
}
