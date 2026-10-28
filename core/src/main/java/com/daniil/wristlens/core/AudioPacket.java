package com.daniil.wristlens.core;
import java.io.*;
/** Independently decodable IMA ADPCM frames: 16 kHz mono, at most 80 ms. */
public final class AudioPacket {
 public static final int RATE=16000,MAX=1280;private static final int MAGIC=0x574c4131;
 private static final int[] STEPS={7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767};
 private static final int[] INDEX={-1,-1,-1,-1,2,4,6,8};
 public record Frame(long sequence,short[] pcm){}
 private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
 public static void write(DataOutputStream out,long sequence,short[] pcm)throws IOException{
  if(pcm.length<1||pcm.length>MAX)throw new IOException("Invalid audio size");out.writeInt(MAGIC);out.writeLong(sequence);out.writeShort(pcm.length);out.writeShort(pcm[0]);out.writeByte(40);int pred=pcm[0],index=40,packed=0;
  for(int i=1;i<pcm.length;i++){int step=STEPS[index],d=pcm[i]-pred,code=d<0?8:0;d=Math.abs(d);int diff=step>>3;if(d>=step){code|=4;d-=step;diff+=step;}if(d>=step/2){code|=2;d-=step/2;diff+=step/2;}if(d>=step/4){code|=1;diff+=step/4;}pred=clamp(pred+((code&8)!=0?-diff:diff),-32768,32767);index=clamp(index+INDEX[code&7],0,88);if((i&1)==1)packed=code;else out.writeByte(packed|(code<<4));}
  if((pcm.length&1)==0)out.writeByte(packed);
 }
 public static Frame read(DataInputStream in)throws IOException{
  if(in.readInt()!=MAGIC)throw new IOException("Invalid audio header");long seq=in.readLong();int n=in.readUnsignedShort(),pred=in.readShort(),index=in.readUnsignedByte();if(n<1||n>MAX||index>88)throw new IOException("Invalid audio frame");short[] pcm=new short[n];pcm[0]=(short)pred;int packed=0;
  for(int i=1;i<n;i++){if((i&1)==1)packed=in.readUnsignedByte();int code=(i&1)==1?packed&15:packed>>4,step=STEPS[index],diff=step>>3;if((code&4)!=0)diff+=step;if((code&2)!=0)diff+=step>>1;if((code&1)!=0)diff+=step>>2;pred=clamp(pred+((code&8)!=0?-diff:diff),-32768,32767);index=clamp(index+INDEX[code&7],0,88);pcm[i]=(short)pred;}return new Frame(seq,pcm);
 }
}
