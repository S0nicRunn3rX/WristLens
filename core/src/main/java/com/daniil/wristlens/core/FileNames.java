package com.daniil.wristlens.core;
import java.time.*;import java.time.format.DateTimeFormatter;
public final class FileNames {
 public static String name(boolean video,String pattern,LocalDateTime date,String extension,int collision){
  String p=switch(pattern){case "millis"->"yyyyMMdd_HHmmss_SSS";case "iso"->"yyyy-MM-dd_HH-mm-ss";default->"yyyyMMdd_HHmmss";};
  return (pattern.equals("wristlens")?"WristLens_":"")+(video?"VID_":"IMG_")+date.format(DateTimeFormatter.ofPattern(p))+(collision>0?String.format(java.util.Locale.ROOT,"_%03d",collision):"")+"."+extension;
 }
}
