package com.daniil.wristlens.shared;
import org.json.*;
import java.nio.charset.StandardCharsets;

public final class Wire {
 public static final String AUDIO="/wristlens/v5/audio/";
 public static final String MEDIA_CHANGED="/wristlens/v4/media-changed";
    public static final String VISIBILITY="/wristlens/v3/visibility";
    public static final String MEDIA="/wristlens/v2/media", MEDIA_RESULT="/wristlens/v2/media-result", FILE="/wristlens/v2/file/", HOLD="/wristlens/v2/hold";
    public static final String COMMAND="/wristlens/v1/command", STATE="/wristlens/v1/state",
            ACK="/wristlens/v1/ack", HELLO="/wristlens/v1/hello", PREVIEW="/wristlens/v1/preview/";
    public static JSONObject object(Object...pairs) {
        JSONObject j=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) j.put((String)pairs[i],pairs[i+1]); }
        catch(JSONException e) { throw new IllegalArgumentException(e); } return j;
    }
    public static byte[] bytes(JSONObject j){ return j.toString().getBytes(StandardCharsets.UTF_8); }
    public static JSONObject parse(byte[] data) throws JSONException {
        if(data.length>64*1024) throw new JSONException("Message too large");
        return new JSONObject(new String(data,StandardCharsets.UTF_8));
    }
}
