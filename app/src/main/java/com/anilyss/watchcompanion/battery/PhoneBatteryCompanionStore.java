package com.anilyss.watchcompanion.battery;

import android.content.Context;

public final class PhoneBatteryCompanionStore {

    private static final String KEY_LAST_WATCH_SEEN_AT = "phone_battery_last_watch_seen_at";

    private PhoneBatteryCompanionStore() {
    }

    public static void markWatchCompanionSeen(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_WATCH_SEEN_AT, System.currentTimeMillis())
                .apply();
    }

    public static long readLastWatchSeenAt(Context context) {
        if (context == null) {
            return 0L;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        return appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_WATCH_SEEN_AT, 0L);
    }
}
