package com.daniil.wristlens.core;
/** Stable, bounded copy presets shared by both sides of the transfer. */
public record CopyProfile(String id,int photoEdge,int jpegQuality,int videoEdge,int videoBitrate) {
 public static CopyProfile of(String id){return switch(id){
  case "balanced"->new CopyProfile(id,1920,85,1280,2_000_000);
  case "compact"->new CopyProfile(id,1280,70,854,1_000_000);
  case "original"->new CopyProfile(id,0,100,0,0);
  default->throw new IllegalArgumentException("Unknown copy profile");};}
 public boolean original(){return id.equals("original");}
 public static String folder(String raw){String s=raw.trim().replace('\\','/').replaceAll("/+","/").replaceAll("/+$","");
  if(!s.startsWith("DCIM/")||s.length()>160||!s.matches("[\\p{L}\\p{N}_ /.-]+"))throw new IllegalArgumentException("Use a folder inside DCIM");
  for(String part:s.split("/"))if(part.isBlank()||part.equals(".")||part.equals(".."))throw new IllegalArgumentException("Invalid folder");return s;
 }
 public int[] dimensions(int width,int height,boolean video){if(width<=0||height<=0)throw new IllegalArgumentException("Invalid dimensions");int edge=video?videoEdge:photoEdge;double scale=edge==0?1:Math.min(1,edge/(double)Math.max(width,height));int w=Math.max(1,(int)Math.floor(width*scale)),h=Math.max(1,(int)Math.floor(height*scale));return video?new int[]{Math.max(2,w/2*2),Math.max(2,h/2*2)}:new int[]{w,h};}
 public String copyName(String source,boolean video){if(original())return source;int dot=source.lastIndexOf('.');return (dot>0?source.substring(0,dot):source)+"_"+id+(video?".mp4":".jpg");}
}
