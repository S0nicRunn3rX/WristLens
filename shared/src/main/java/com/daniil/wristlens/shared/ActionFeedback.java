package com.daniil.wristlens.shared;
import android.content.Context;import android.view.*;import android.os.Build;
/** One preference gates explicit feedback and native long-press feedback. */
public final class ActionFeedback {
 public static boolean enabled(Context c){return c.getSharedPreferences("watch_ui",0).getBoolean("vibration",true);}
 public static void enabled(Context c,boolean on){c.getSharedPreferences("watch_ui",0).edit().putBoolean("vibration",on).apply();}
 public static boolean perform(View view,int kind){if(!enabled(view.getContext()))return false;view.setHapticFeedbackEnabled(true);return view.performHapticFeedback(kind);}
 public static void press(View view){if(view.isEnabled())perform(view,HapticFeedbackConstants.VIRTUAL_KEY);}
 public static void tick(View view){perform(view,Build.VERSION.SDK_INT>=34?HapticFeedbackConstants.SEGMENT_TICK:HapticFeedbackConstants.CLOCK_TICK);}
 public static void apply(View view){boolean on=enabled(view.getContext());apply(view,on);}
 private static void apply(View v,boolean on){v.setHapticFeedbackEnabled(on);if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)apply(g.getChildAt(i),on);}
}
