package com.daniil.wristlens.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Camera commands can be retried without taking a second photo. Owned by one handler. */
public final class CommandLedger {
    private final Map<String,String> done = new LinkedHashMap<>();
    public boolean contains(String id) { return done.containsKey(id); }
    public String get(String id) { return done.get(id); }
    public void put(String id,String response) {
        done.put(id,response);
        while(done.size()>256) done.remove(done.keySet().iterator().next());
    }
}
