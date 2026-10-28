package com.daniil.wristlens.shared;
import android.content.Context;import android.graphics.Color;import android.os.Build;
public final class ThemeColors {public static final int BLUE=0xff82b1ff;public static int accent(Context c){if(Build.VERSION.SDK_INT>=31){int color=c.getColor(android.R.color.system_accent1_200);float[] hsv=new float[3];Color.colorToHSV(color,hsv);if(hsv[1]>.05f)return color;}return BLUE;}}
