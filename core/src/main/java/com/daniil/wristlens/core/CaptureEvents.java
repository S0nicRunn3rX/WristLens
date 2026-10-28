package com.daniil.wristlens.core;

import java.util.*;

/** Successful camera actions, ordered on the camera thread; heartbeat snapshots may repeat them. */
public final class CaptureEvents {
    public enum Kind { PHOTO, BURST_PHOTO, START, PAUSE, RESUME, STOP }
    public record Event(long id,long time,Kind kind) {}
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private long sequence;
    public void add(long time,Kind kind){events.addLast(new Event(++sequence,time,kind));while(events.size()>32)events.removeFirst();}
    public List<Event> snapshot(){return List.copyOf(events);}

    /** Do not replay history after resume, duplicate messages or a connection backlog. */
    public static final class Cursor {
        private String session="";private long sequence;
        public void reset(){session="";sequence=0;}
        public Event accept(String next,long phoneTime,List<Event> events){
            long newest=events.stream().mapToLong(Event::id).max().orElse(0);
            if(next.isEmpty())return null;
            if(!next.equals(session)){session=next;sequence=newest;return null;}
            Event result=null;
            for(Event e:events)if(e.id()>sequence&&e.time()<=phoneTime&&phoneTime-e.time()<=1500&&(result==null||e.id()>result.id()))result=e;
            sequence=Math.max(sequence,newest);return result;
        }
    }
}
