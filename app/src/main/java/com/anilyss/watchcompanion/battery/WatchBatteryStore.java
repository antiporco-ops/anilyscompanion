package com.anilyss.watchcompanion.battery;

import android.content.Context;

public final class WatchBatteryStore {

    public static final String DATA_PATH = "/watch_battery";
    private static final String KEY_LEVEL = "watch_battery_level";
    private static final String KEY_CHARGING = "watch_battery_charging";
    private static final String KEY_TIMESTAMP = "watch_battery_timestamp";

    private WatchBatteryStore() {
    }

    public static void write(Context context, int level, boolean charging, long timestamp) {
        if (context == null || level < 0 || level > 100) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LEVEL, level)
                .putBoolean(KEY_CHARGING, charging)
                .putLong(KEY_TIMESTAMP, timestamp)
                .apply();
    }

    public static int readLevel(Context context) {
        if (context == null) {
            return -1;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        return appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LEVEL, -1);
    }

    public static boolean readCharging(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        return appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHARGING, false);
    }

    public static long readTimestamp(Context context) {
        if (context == null) {
            return 0L;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        return appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_TIMESTAMP, 0L);
    }
}
