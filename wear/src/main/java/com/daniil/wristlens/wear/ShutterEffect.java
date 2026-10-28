package com.daniil.wristlens.wear;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A short aperture blink above the live image, below all controls. */
final class ShutterEffect extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mask=new Path();
    private ValueAnimator animator;private float progress;
    ShutterEffect(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);}
    void fire(){clear();animator=ValueAnimator.ofFloat(0,1);animator.setDuration(180);animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();}
    void clear(){if(animator!=null){animator.cancel();animator=null;}progress=0;invalidate();}
    @Override protected void onDetachedFromWindow(){clear();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        if(progress<=0||progress>=1)return;
        float close=(float)Math.sin(Math.PI*progress),radius=(float)Math.hypot(getWidth(),getHeight())/2f;
        mask.reset();mask.setFillType(Path.FillType.EVEN_ODD);mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);
        mask.addCircle(getWidth()/2f,getHeight()/2f,radius*(1-.88f*close),Path.Direction.CW);
        paint.setColor(Color.BLACK);paint.setAlpha(Math.round(165*close));canvas.drawPath(mask,paint);
    }
}
