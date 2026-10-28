package com.daniil.wristlens.wear;
import android.content.*;
public final class DownloadCancelReceiver extends BroadcastReceiver {@Override public void onReceive(Context c,Intent i){Downloads.cancel(c,i.getStringExtra("job"));}}
