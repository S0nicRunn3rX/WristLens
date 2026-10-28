package com.daniil.wristlens.core;
import java.util.*;
/** Lease expires if release is lost. Retired IDs prevent reordered start after release. */
public final class HoldBurst {
 public static final int MAX_PHOTOS=50; public static final long MAX_MS=60000, LEASE_MS=1200;
 private String id=""; private long renewed,first=-1; private int count,limit;
 private final LinkedHashSet<String> retired=new LinkedHashSet<>();
 public boolean start(String value,long now,int max){if(value==null||value.isEmpty()||value.length()>100||retired.contains(value))return false;if(id.equals(value))return true;if(!id.isEmpty())return false;id=value;renewed=now;first=-1;count=0;limit=Math.max(1,Math.min(MAX_PHOTOS,max));return true;}
 public void renew(String value,long now){if(id.equals(value)&&active(now))renewed=now;}
 public void stop(String value){if(value==null||value.isEmpty())return;retired.add(value);while(retired.size()>128)retired.remove(retired.iterator().next());if(id.equals(value))id="";}
 public void stop(){stop(id);}
 public boolean active(long now){if(id.isEmpty())return false;if(now-renewed>LEASE_MS||count>=limit||(first>=0&&now-first>=MAX_MS)){stop();return false;}return true;}
 public boolean take(long now){if(!active(now))return false;if(first<0)first=now;count++;return true;}
 public int count(){return count;}
}
