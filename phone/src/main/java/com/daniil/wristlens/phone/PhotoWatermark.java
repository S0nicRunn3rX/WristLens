package com.daniil.wristlens.phone;
import android.graphics.*;import com.daniil.wristlens.core.WatermarkSpec;import java.io.*;import java.nio.ByteBuffer;
/** Apply EXIF orientation, stamp physical pixels, then encode the saved original. */
final class PhotoWatermark {
 static void write(byte[] jpeg,String type,WatermarkSpec spec,long epoch,OutputStream out)throws IOException{
  if(type.equals("jpeg")&&!spec.enabled()){out.write(jpeg);return;}
  BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,size);Runtime r=Runtime.getRuntime();
  if(size.outWidth<=0||size.outHeight<=0)throw new IOException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_extra_15));if((long)size.outWidth*size.outHeight*8>r.maxMemory()-(r.totalMemory()-r.freeMemory()))throw new IOException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_extra_16));
  Bitmap b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(jpeg)),(d,i,s)->{d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);d.setMutableRequired(true);});
  try{if(spec.enabled())stamp(b,spec,epoch);Bitmap.CompressFormat format=type.equals("png")?Bitmap.CompressFormat.PNG:type.equals("webp")?Bitmap.CompressFormat.WEBP:Bitmap.CompressFormat.JPEG;if(!b.compress(format,type.equals("jpeg")?95:100,out))throw new IOException(com.daniil.wristlens.shared.Strings.tr(com.daniil.wristlens.shared.R.string.wl_extra_17));}finally{b.recycle();}
 }
 static void stamp(Bitmap image,WatermarkSpec spec,long epoch){if(!spec.enabled())return;try(WatermarkOverlay o=new WatermarkOverlay(spec,image.getWidth(),image.getHeight(),0)){o.update(epoch);new Canvas(image).drawBitmap(o.bitmap,(o.vertices[0]+1)*image.getWidth()/2f,(1-o.vertices[1])*image.getHeight()/2f,null);}}
}
