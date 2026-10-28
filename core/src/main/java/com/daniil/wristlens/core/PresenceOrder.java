package com.daniil.wristlens.core;

import java.util.LinkedHashSet;

/** Prevent a delayed watch onPause/HELLO from reversing a newer lifecycle event. */
public final class PresenceOrder {
    private String client="";
    private long sequence=-1;
    private final LinkedHashSet<String> retired=new LinkedHashSet<>();
    public boolean hello(String id,long next){
        if(id.isEmpty())return client.isEmpty(); // Earlier protocol version, without lifecycle messages.
        if(id.length()>100||next<0||retired.contains(id))return false;
        if(!client.equals(id)){
            if(!client.isEmpty())retired.add(client);
            while(retired.size()>128)retired.remove(retired.iterator().next());
            client=id;sequence=-1;
        }
        if(next<=sequence)return false;sequence=next;return true;
    }
    public boolean hidden(String id,long next){
        if(id.isEmpty()||!client.equals(id)||next<=sequence)return false;
        sequence=next;return true;
    }
}
