package com.daniil.wristlens.shared;
import android.content.Context;import android.graphics.*;import android.text.format.DateFormat;import android.view.View;import java.util.Date;
/** Transparent edge overlay; no reserved row and no intercepted touches. */
public final class CurvedClock extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path arc=new Path();
 private final Runnable tick=new Runnable(){public void run(){invalidate();postDelayed(this,1000);}};
 public CurvedClock(Context c){super(c);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);p.setColor(ThemeColors.accent(c));p.setTextAlign(Paint.Align.CENTER);p.setShadowLayer(2,0,1,Color.BLACK);}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(tick);}
 @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
 @Override protected void onDraw(Canvas c){float r=Math.min(getWidth(),getHeight())/2f-Controls.dp(getContext(),10),x=getWidth()/2f,y=getHeight()/2f;if(r<=0)return;p.setTextSize(Controls.dp(getContext(),11));arc.reset();arc.addArc(x-r,y-r,x+r,y+r,220,100);Paint.FontMetrics m=p.getFontMetrics();c.drawTextOnPath(DateFormat.getTimeFormat(getContext()).format(new Date()),arc,0,-(m.ascent+m.descent)/2,p);}
}
