package com.daniil.wristlens.wear;
import android.content.Context;import androidx.work.*;import java.util.*;import java.util.function.BiConsumer;
final class DownloadTestDispatch {
 static final List<OneTimeWorkRequest> enqueued=new ArrayList<>();static final List<UUID> cancelled=new ArrayList<>();private static BiConsumer<Context,OneTimeWorkRequest> oldSchedule;private static BiConsumer<Context,UUID> oldCancel;
 static void install(Context c){c.getSharedPreferences("downloads",0).edit().clear().commit();enqueued.clear();cancelled.clear();oldSchedule=Downloads.schedule;oldCancel=Downloads.cancelWork;Downloads.schedule=(a,w)->enqueued.add(w);Downloads.cancelWork=(a,w)->cancelled.add(w);}
 static void restore(){Downloads.schedule=oldSchedule;Downloads.cancelWork=oldCancel;}
}
