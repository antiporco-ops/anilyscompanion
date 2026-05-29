package com.anilyss.watchcompanion;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class PhoneBatteryDataLayerService extends WearableListenerService {

    private static final String TAG = "AniLysWearBattery";
    private static final String PHONE_BATTERY_PATH = "/phone_battery";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        try {
            for (DataEvent event : dataEvents) {
                if (event.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }
                String path = event.getDataItem().getUri().getPath();
                if (!PHONE_BATTERY_PATH.equals(path)) {
                    continue;
                }
                int level = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getInt("level", -1);
                if (level < 0 || level > 100) {
                    Log.d(TAG, "Ignoring invalid phone battery level=" + level);
                    continue;
                }
                boolean charging = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getBoolean("charging", false);
                long timestamp = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getLong("ts", 0L);
                long safeTimestamp = timestamp > 0L ? timestamp : System.currentTimeMillis();
                PhoneBatteryStore.write(this, level, charging, safeTimestamp);
            }
        } finally {
            dataEvents.release();
        }
    }
}
