package com.daniil.wristlens.shared;
import android.content.Context;
import android.text.format.Formatter;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.*;

public final class MediaProperties {
    private MediaProperties(){}
    public static String describe(Context c,JSONObject item){
        List<String> lines=new ArrayList<>(),facts=new ArrayList<>();
        String mime=item.optString("mime");int slash=mime.indexOf('/');
        if(slash>=0)facts.add(mime.substring(slash+1).toUpperCase(Locale.ROOT));
        long bytes=item.optLong("bytes");if(bytes>0)facts.add(Formatter.formatShortFileSize(c,bytes));
        if(!facts.isEmpty())lines.add(String.join(" · ",facts));facts.clear();
        int w=item.optInt("width"),h=item.optInt("height");if(w>0&&h>0)facts.add(w+"×"+h);
        long duration=item.optLong("durationMs");if(duration>0){long seconds=duration/1000;facts.add(String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60));}
        if(!facts.isEmpty())lines.add(String.join(" · ",facts));
        long time=item.optLong("time");if(time>0)lines.add(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time)));
        return String.join("\n",lines);
    }
}
