package com.daniil.wristlens.phone;
import android.content.Context;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34) public class AudioOptionsTest {
 Context c;@Before public void setup(){c=RuntimeEnvironment.getApplication();c.getSharedPreferences("audio_options",0).edit().clear().commit();c.getSharedPreferences("PhoneActivity",0).edit().clear().commit();}
 @Test public void defaultsRecordOnMonitorOff(){AudioOptions a=new AudioOptions(c);assertTrue(a.record());assertFalse(a.preview());}
 @Test public void migratesExistingPhonePreferenceOnce(){c.getSharedPreferences("PhoneActivity",0).edit().putBoolean("audio",false).commit();AudioOptions a=new AudioOptions(c);assertFalse(a.record());a.record(true);assertTrue(new AudioOptions(c).record());}
 @Test public void recordingAndPreviewAreIndependentAndPersistent(){AudioOptions a=new AudioOptions(c);a.preview(true);a.record(false);AudioOptions b=new AudioOptions(c);assertTrue(b.preview());assertFalse(b.record());b.preview(false);assertFalse(new AudioOptions(c).record());}
}
