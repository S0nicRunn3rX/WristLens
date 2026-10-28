package com.daniil.wristlens.wear;
import com.google.android.gms.wearable.*;import com.daniil.wristlens.shared.Wire;
public final class MediaChanges extends WearableListenerService {
 @Override public void onDataChanged(DataEventBuffer events){for(DataEvent e:events)if(e.getType()==DataEvent.TYPE_CHANGED&&Wire.MEDIA_CHANGED.equals(e.getDataItem().getUri().getPath()))AutoSave.enqueue(this);}
}
