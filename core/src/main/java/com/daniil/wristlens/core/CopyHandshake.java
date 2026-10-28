package com.daniil.wristlens.core;
import java.io.*;
/** Streaming preparation keeps a lengthy video conversion alive before file bytes follow. */
public final class CopyHandshake {
 public static final int WAIT=0,READY=1,ERROR=2;
 @FunctionalInterface public interface Pulse {void run()throws Exception;}
 public record Metadata(String id,String name,String mime,long bytes){}
 public static void waiting(DataOutputStream out)throws IOException{out.writeByte(WAIT);out.flush();}
 public static void ready(DataOutputStream out,Metadata m)throws IOException{out.writeByte(READY);out.writeUTF(m.id);out.writeUTF(m.name);out.writeUTF(m.mime);out.writeLong(m.bytes);out.flush();}
 public static void error(DataOutputStream out,String message)throws IOException{out.writeByte(ERROR);out.writeUTF(message==null?"Copy failed":message.substring(0,Math.min(2000,message.length())));out.flush();}
 public static Metadata receive(DataInputStream in,String expected,Runnable heartbeat)throws IOException{for(int count=0;count<10800;count++){int type=in.readUnsignedByte();heartbeat.run();if(type==WAIT)continue;if(type==ERROR)throw new IOException(in.readUTF());if(type!=READY)throw new IOException("Unknown copy response");Metadata m=new Metadata(in.readUTF(),in.readUTF(),in.readUTF(),in.readLong());if(!m.id.equals(expected)||m.bytes<=0||m.bytes>OriginalTransfer.MAX||(!m.mime.startsWith("image/")&&!m.mime.startsWith("video/")))throw new IOException("Invalid copy metadata");return m;}throw new IOException("Copy preparation timed out");}
}
