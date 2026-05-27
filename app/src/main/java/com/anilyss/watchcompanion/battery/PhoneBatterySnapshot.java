package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

final class PhoneBatterySnapshot {

    final int level;
    final boolean charging;

    private PhoneBatterySnapshot(int level, boolean charging) {
        this.level = level;
        this.charging = charging;
    }

    static PhoneBatterySnapshot readCurrent(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        Intent sticky = appContext.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
        if (sticky == null) {
            return null;
        }

        int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return null;
        }
        int pct = Math.max(0, Math.min(100, (int) ((level * 100f) / scale)));

        int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        return new PhoneBatterySnapshot(pct, charging);
    }
}
