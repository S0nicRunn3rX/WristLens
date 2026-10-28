package com.daniil.wristlens.shared;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Full-screen circular menu: content insets, natural scrolling, no rectangular crop. */
public final class RoundMenuFrame extends FrameLayout {
    private final View body;
    private final CurvedClock clock;
    private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Runnable back=()->{};
    private boolean rim,backDrag;
    private float angle,remainder,downX,downY;
    private float feedbackDistance;private long lastTick=-100;
    private float indicatorAlpha;private ValueAnimator indicatorAnimation;
    private final Runnable fadeIndicator=()->animateIndicator(0,260);
    private int scrollRange(){return body instanceof ScrollView s&&s.getChildCount()>0?Math.max(0,s.getChildAt(0).getHeight()-s.getHeight()):0;}
    private void animateIndicator(float to,long duration){if(indicatorAnimation!=null)indicatorAnimation.cancel();indicatorAnimation=ValueAnimator.ofFloat(indicatorAlpha,to);indicatorAnimation.setDuration(duration);indicatorAnimation.addUpdateListener(a->{indicatorAlpha=(float)a.getAnimatedValue();invalidate();});indicatorAnimation.start();}
    private void showIndicator(){if(scrollRange()==0)return;removeCallbacks(fadeIndicator);animateIndicator(1,100);postDelayed(fadeIndicator,900);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)ActionFeedback.apply(body);return super.dispatchTouchEvent(e);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(fadeIndicator);if(indicatorAnimation!=null)indicatorAnimation.cancel();indicatorAlpha=0;super.onDetachedFromWindow();}

    public RoundMenuFrame(Context c,View content){
        super(c);body=content;clock=new CurvedClock(c);setBackgroundColor(Controls.BG);
        addView(body,new LayoutParams(-1,-1));addView(clock,new LayoutParams(-1,-1));
        setFocusableInTouchMode(true);
        track.setStyle(Paint.Style.STROKE);thumb.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);thumb.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x557b8797);thumb.setColor(ThemeColors.accent(c));
        if(body instanceof ScrollView s){s.setClipToPadding(false);s.setVerticalScrollBarEnabled(false);s.setVerticalFadingEdgeEnabled(false);s.setOverScrollMode(OVER_SCROLL_NEVER);s.setOnScrollChangeListener((v,x,y,oldX,oldY)->{clock.setAlpha(Math.max(0,1-y/(float)Controls.dp(c,24)));showIndicator();invalidate();});}
    }
    public void onBack(Runnable action){back=action;}
    @Override protected void onMeasure(int ws,int hs){
        int width=MeasureSpec.getSize(ws),height=MeasureSpec.getSize(hs),d=Math.min(width,height);
        if(body instanceof ScrollView s&&s.getChildCount()>0){
            s.setPadding(0,0,0,0);View child=s.getChildAt(0);int side=Math.max(Controls.dp(getContext(),24),Math.round(d*.12f));
            child.setPadding(side,Math.round(d*.19f),side,Math.round(d*.23f));
            if(child instanceof LinearLayout col){
                if(col.getChildCount()>0&&col.getChildAt(0) instanceof TextView title&&!(title instanceof Button))title.setGravity(Gravity.CENTER);
                for(int i=0;i<col.getChildCount()-1;i++){View view=col.getChildAt(i);if(view.getLayoutParams() instanceof LinearLayout.LayoutParams p&&p.bottomMargin<Controls.dp(getContext(),10)){p.bottomMargin=Controls.dp(getContext(),10);view.setLayoutParams(p);}}
            }
        }
        super.onMeasure(ws,hs);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();requestFocus();}
    private SeekBar slider(View view){if(view instanceof SeekBar bar)return bar;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){SeekBar b=slider(group.getChildAt(i));if(b!=null)return b;}return null;}
    /** Positive units move down / increase. Both hardware rotary and our touch rim use this. */
    public void rotate(float units){
        if(!Float.isFinite(units))return;SeekBar bar=slider(body);
        if(bar!=null&&bar.isEnabled()){
            remainder+=units;int steps=(int)remainder;if(steps!=0){remainder-=steps;int old=bar.getProgress();bar.setProgress(Math.max(0,Math.min(bar.getMax(),old+steps)));if(old!=bar.getProgress()){tick();bar.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);}}
        }else if(body instanceof ScrollView s){int old=s.getScrollY();s.scrollBy(0,Math.round(units*Controls.dp(getContext(),28)));int moved=s.getScrollY()-old;if(moved==0)feedbackDistance=0;else{if(Math.signum(moved)!=Math.signum(feedbackDistance))feedbackDistance=0;feedbackDistance+=moved;int step=Controls.dp(getContext(),12);if(Math.abs(feedbackDistance)>=step){feedbackDistance%=step;tick();}}}
        invalidate();
    }
    private void tick(){long now=android.os.SystemClock.uptimeMillis();if(now-lastTick>=45){lastTick=now;ActionFeedback.tick(this);}}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_SCROLL&&event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)){rotate(-event.getAxisValue(MotionEvent.AXIS_SCROLL));return true;}return super.dispatchGenericMotionEvent(event);
    }
    private float angle(MotionEvent e){return (float)Math.atan2(e.getY()-getHeight()/2f,e.getX()-getWidth()/2f);}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();backDrag=false;remainder=feedbackDistance=0;float r=Math.min(getWidth(),getHeight())/2f;rim=downX>Controls.dp(getContext(),24)&&Math.hypot(downX-getWidth()/2f,downY-getHeight()/2f)>r-Controls.dp(getContext(),16);angle=angle(e);if(rim)return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&downX<Controls.dp(getContext(),24)&&e.getX()-downX>Controls.dp(getContext(),32)&&Math.abs(e.getY()-downY)<Controls.dp(getContext(),28)){backDrag=true;return true;}
        return super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(backDrag){if(e.getActionMasked()==MotionEvent.ACTION_UP){backDrag=false;back.run();}return true;}
        if(rim){if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float next=angle(e),delta=next-angle;if(delta>Math.PI)delta-=2*Math.PI;if(delta< -Math.PI)delta+=2*Math.PI;angle=next;rotate((float)(delta/(Math.PI/18)));}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){rim=false;performClick();}return true;}
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);if(!(body instanceof ScrollView s)||s.getChildCount()==0)return;
        int range=Math.max(0,s.getChildAt(0).getHeight()-s.getHeight());if(range==0||indicatorAlpha<=0)return;
        float radius=Math.min(getWidth(),getHeight())/2f-Controls.dp(getContext(),5),cx=getWidth()/2f,cy=getHeight()/2f;
        RectF arc=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);float sweep=Math.max(10,72f*s.getHeight()/s.getChildAt(0).getHeight());
        track.setStrokeWidth(Controls.dp(getContext(),2));thumb.setStrokeWidth(Controls.dp(getContext(),3));
        track.setAlpha(Math.round(85*indicatorAlpha));thumb.setAlpha(Math.round(255*indicatorAlpha));
        canvas.drawArc(arc,-36,72,false,track);canvas.drawArc(arc,-36+(72-sweep)*Math.max(0,Math.min(1,s.getScrollY()/(float)range)),sweep,false,thumb);
    }
}
