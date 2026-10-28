package com.daniil.wristlens.phone;

import android.graphics.*;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import com.daniil.wristlens.core.StreamSizing;
import com.daniil.wristlens.core.WatermarkSpec;
import java.nio.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Camera PRIVATE stream. Optional GPU timestamp compositing into the recorder's input surface. */
final class GlPreview implements AutoCloseable {
    interface Sink{void frame(byte[] jpeg,int width,int height);}
    private final Handler handler;
    private final int width,height;
    private final Sink sink;
    private final Consumer<Exception> failure;
    private final Surface encoderSurface;
    private final WatermarkOverlay watermark;
    private final ExecutorService jpeg=Executors.newSingleThreadExecutor();
    private final AtomicBoolean encoding=new AtomicBoolean();
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface pbuffer=EGL14.EGL_NO_SURFACE,encoder=EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;
    Surface surface;
    private int program,tex,overlayTex;
    private long markedFrames;long markedFrames(){return markedFrames;}
    private volatile boolean closed;
    private boolean recording,failed;
    volatile int screenW=480,screenH=480,rotation,interval=67,quality=60,preferred;
    private long last;
    private final FloatBuffer quad=buffer(new float[]{-1,-1,1,-1,-1,1,1,1});
    
    private final float[] transform=new float[16];
    GlPreview(Handler handler,int width,int height,Sink sink)throws Exception{this(handler,width,height,sink,null,null,0,e->android.util.Log.w("WristLens","GL preview",e));}
    GlPreview(Handler handler,int width,int height,Sink sink,Surface encoderSurface,WatermarkSpec spec,int rotation,Consumer<Exception> failure)throws Exception{
        this.handler=handler;this.width=width;this.height=height;this.sink=sink;this.encoderSurface=encoderSurface;this.failure=failure;
        watermark=spec==null?null:new WatermarkOverlay(spec,width,height,rotation);
        try{sync(this::init);}catch(Exception e){close();throw e;}
    }
    boolean compositing(){return encoderSurface!=null;}
    void recording(boolean value)throws Exception{sync(()->recording=value);}
    private void sync(Runnable work)throws Exception{
        if(Looper.myLooper()==handler.getLooper()){work.run();return;}
        FutureTask<Void> task=new FutureTask<>(()->{work.run();return null;});if(!handler.post(task))throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_083));task.get(10,TimeUnit.SECONDS);
    }
    private static FloatBuffer buffer(float[] values){FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(values).position(0);return b;}
    private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String message=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException(message);}return s;}
    private static int program(String vertex,String fragment){int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));return p;}
    private static int texture(int target){int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);GLES20.glBindTexture(target,ids[0]);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);return ids[0];}
    private void init(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(!EGL14.eglInitialize(display,new int[2],0,new int[2],0))throw new IllegalStateException("EGL initialize");
        EGLConfig[] config=new EGLConfig[1];int[] count=new int[1];
        int[] attributes={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT|(compositing()?EGL14.EGL_WINDOW_BIT:0),EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        if(compositing()){int n=attributes.length;attributes=java.util.Arrays.copyOf(attributes,n+2);attributes[n-1]=0x3142;attributes[n]=1;attributes[n+1]=EGL14.EGL_NONE;}
        if(!EGL14.eglChooseConfig(display,attributes,0,config,0,1,count,0)||count[0]==0)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_084));
        context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        pbuffer=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,640,EGL14.EGL_HEIGHT,640,EGL14.EGL_NONE},0);current(pbuffer);
        if(compositing()){encoder=EGL14.eglCreateWindowSurface(display,config[0],encoderSurface,new int[]{EGL14.EGL_NONE},0);if(encoder==EGL14.EGL_NO_SURFACE)throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_085));}
        tex=texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        program=program("attribute vec2 p;uniform mat4 m;varying vec2 uv;varying vec2 frame;void main(){gl_Position=vec4(p,0.,1.);frame=(p+1.)/2.;uv=(m*vec4(frame,0.,1.)).xy;}","#extension GL_OES_EGL_image_external : require\nprecision highp float;uniform samplerExternalOES image;uniform sampler2D stamp;uniform float marked;uniform vec2 origin;uniform vec2 edgeU;uniform vec2 edgeV;varying vec2 uv;varying vec2 frame;void main(){vec4 c=texture2D(image,uv);if(marked>0.5){vec2 d=frame-origin;vec2 t=vec2(dot(d,edgeU)/dot(edgeU,edgeU),dot(d,edgeV)/dot(edgeV,edgeV));if(t.x>=0.&&t.x<=1.&&t.y>=0.&&t.y<=1.){vec4 w=texture2D(stamp,t);c=vec4(w.rgb+c.rgb*(1.-w.a),1.);}}gl_FragColor=c;}");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);overlayTex=texture(GLES20.GL_TEXTURE_2D);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,1,1,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(4));GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        texture=new SurfaceTexture(tex);texture.setDefaultBufferSize(width,height);surface=new Surface(texture);texture.setOnFrameAvailableListener(st->draw(),handler);
    }
    private void current(EGLSurface target){if(!EGL14.eglMakeCurrent(display,target,target,context))throw new IllegalStateException("EGL make current: "+EGL14.eglGetError());}
    private static void attribute(int program,String name,FloatBuffer data){int at=GLES20.glGetAttribLocation(program,name);GLES20.glEnableVertexAttribArray(at);data.position(0);GLES20.glVertexAttribPointer(at,2,GLES20.GL_FLOAT,false,0,data);}
    private void scene(int w,int h,boolean encode){
        GLES20.glViewport(0,0,w,h);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glUseProgram(program);attribute(program,"p",quad);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"m"),1,false,transform,0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,tex);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,overlayTex);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"stamp"),1);boolean marked=encode&&watermark!=null;
        if(marked){if(watermark.update(System.currentTimeMillis()))GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,watermark.bitmap,0);float[] v=watermark.vertices;GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"origin"),(v[0]+1)/2,(v[1]+1)/2);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"edgeU"),(v[2]-v[0])/2,(v[3]-v[1])/2);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"edgeV"),(v[4]-v[0])/2,(v[5]-v[1])/2);}
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"marked"),marked?1:0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);int error=GLES20.glGetError();if(error!=GLES20.GL_NO_ERROR)throw new IllegalStateException("Video GL: "+error);
    }
    private void draw(){
        if(closed||failed)return;
        try{
            current(pbuffer);texture.updateTexImage();texture.getTransformMatrix(transform);
            if(compositing()&&recording){current(encoder);scene(width,height,true);if(!EGLExt.eglPresentationTimeANDROID(display,encoder,texture.getTimestamp())||!EGL14.eglSwapBuffers(display,encoder))throw new IllegalStateException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_086));markedFrames++;}
            long now=SystemClock.elapsedRealtime();if(now-last<interval||!encoding.compareAndSet(false,true))return;last=now;
            try{
                int[] dimensions=StreamSizing.fit(width,height,rotation,Math.min(640,screenW),Math.min(640,screenH),preferred);int w=dimensions[0],h=dimensions[1],q=quality;
                current(pbuffer);scene(w,h,false);ByteBuffer pixels=ByteBuffer.allocateDirect(w*h*4);GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);
                jpeg.execute(()->{try{
                    int[] colors=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int offset=((h-1-y)*w+x)*4;colors[y*w+x]=Color.rgb(pixels.get(offset)&255,pixels.get(offset+1)&255,pixels.get(offset+2)&255);}
                    Bitmap bitmap=Bitmap.createBitmap(colors,w,h,Bitmap.Config.ARGB_8888);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,q,out);bitmap.recycle();if(!closed)sink.frame(out.toByteArray(),w,h);
                }catch(Exception e){android.util.Log.w("WristLens","Preview JPEG",e);}finally{encoding.set(false);}});
            }catch(Exception e){encoding.set(false);throw e;}
        }catch(Exception e){failed=true;recording=false;failure.accept(e);}
    }
    @Override public void close(){
        if(closed)return;closed=true;
        try{sync(()->{
            recording=false;if(texture!=null)texture.setOnFrameAvailableListener(null);if(surface!=null)surface.release();if(texture!=null)texture.release();if(watermark!=null)watermark.close();
            if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(encoder!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,encoder);if(pbuffer!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,pbuffer);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);EGL14.eglReleaseThread();}
        });}catch(Exception e){android.util.Log.w("WristLens","Close GL",e);}finally{jpeg.shutdown();}
    }
}
