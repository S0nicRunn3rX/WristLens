package com.daniil.wristlens.shared;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

/** Version is read from the installed package so the footer cannot become stale. */
public final class AppCredits {
    public static String version(Context c){
        try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}
        catch(android.content.pm.PackageManager.NameNotFoundException e){return "—";}
    }
    public static void add(Context c,LinearLayout col){
        TextView title=Controls.text(c,"WristLens · "+version(c),14,ThemeColors.accent(c));title.setGravity(Gravity.CENTER);
        title.setPadding(0,Controls.dp(c,8),0,0);col.addView(title,new LinearLayout.LayoutParams(-1,-2));

    }
}
