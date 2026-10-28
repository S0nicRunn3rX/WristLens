package com.daniil.wristlens.core;
import java.io.*;import java.security.*;import java.util.function.LongConsumer;
public final class OriginalTransfer {
 public static final long MAX=4L*1024*1024*1024; private static final int MAGIC=0x574c4632;
 private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 public static void send(InputStream source,DataOutputStream out,long size,LongConsumer progress)throws IOException{
  if(size<0||size>MAX)throw new IOException(CoreText.choose("Invalid file size","Размер файла недопустим"));out.writeInt(MAGIC);out.writeLong(size);MessageDigest digest=sha();byte[] b=new byte[65536];long total=0;
  while(total<size){int n=source.read(b,0,(int)Math.min(b.length,size-total));if(n<0)throw new EOFException();out.write(b,0,n);digest.update(b,0,n);total+=n;progress.accept(total);}out.write(digest.digest());out.flush();
 }
 public static long receive(DataInputStream in,OutputStream out,long space,LongConsumer progress)throws IOException{
  if(in.readInt()!=MAGIC)throw new IOException(CoreText.choose("Unknown transfer format","Неизвестный формат передачи"));long size=in.readLong();if(size<0||size>MAX||size>space)throw new IOException(CoreText.choose("Not enough storage or file too large","Недостаточно памяти или файл слишком велик"));
  MessageDigest digest=sha();byte[] b=new byte[65536];long total=0;while(total<size){int n=in.read(b,0,(int)Math.min(b.length,size-total));if(n<0)throw new EOFException();out.write(b,0,n);digest.update(b,0,n);total+=n;progress.accept(total);}byte[] expected=new byte[32];in.readFully(expected);if(!MessageDigest.isEqual(expected,digest.digest()))throw new IOException(CoreText.choose("SHA-256 verification failed","Проверка SHA-256 не пройдена"));out.flush();return total;
 }
}
