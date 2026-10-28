package com.daniil.wristlens.phone;

import com.google.android.gms.wearable.*;
import com.google.android.gms.tasks.Tasks;
import com.daniil.wristlens.core.FrameCodec;
import java.io.*;
import java.util.concurrent.*;

/** Single in-flight frame plus one replaceable frame. A slow link cannot build a stale queue. */
final class FrameSender {
    private final ChannelClient client;
    private final ChannelClient.Channel channel;
    private final ArrayBlockingQueue<FrameCodec.Frame> latest=new ArrayBlockingQueue<>(1);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    volatile long inFlightSince;
    FrameSender(ChannelClient c,ChannelClient.Channel ch,Runnable finished) {
        client=c;channel=ch;
        worker.execute(()->{
            try(DataOutputStream out=new DataOutputStream(Tasks.await(c.getOutputStream(ch),10,TimeUnit.SECONDS));
                DataInputStream ack=new DataInputStream(Tasks.await(c.getInputStream(ch),10,TimeUnit.SECONDS))){
                while(!closed){
                    FrameCodec.Frame f=latest.poll(3,TimeUnit.SECONDS);if(f==null)continue;
                    inFlightSince=android.os.SystemClock.elapsedRealtime();FrameCodec.write(out,f);
                    if(ack.readLong()!=f.sequence())throw new IOException("Wrong frame acknowledgement");inFlightSince=0;
                }
            }catch(Exception ignored){}finally{close();finished.run();}
        });
    }
    void offer(FrameCodec.Frame f){if(closed)return;latest.poll();latest.offer(f);}
    void close(){if(closed)return;closed=true;client.close(channel);worker.shutdownNow();latest.clear();}
}
