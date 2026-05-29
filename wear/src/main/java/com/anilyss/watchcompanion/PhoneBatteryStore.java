package com.anilyss.watchcompanion;

import android.content.Context;
import android.content.SharedPreferences;

public final class PhoneBatteryStore {

    private static final String PREFS = "anilys_wear_phone_battery";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_CHARGING = "charging";
    private static final String KEY_TIMESTAMP = "timestamp";

    private PhoneBatteryStore() {
    }

    public static void write(Context context, int level, boolean charging, long timestamp) {
        if (context == null || level < 0 || level > 100) {
            return;
        }
        Context appContext = appContext(context);
        prefs(appContext)
                .edit()
                .putInt(KEY_LEVEL, level)
                .putBoolean(KEY_CHARGING, charging)
                .putLong(KEY_TIMESTAMP, timestamp)
                .apply();
    }

    public static Snapshot read(Context context) {
        if (context == null) {
            return Snapshot.empty();
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        int level = prefs.getInt(KEY_LEVEL, -1);
        boolean charging = prefs.getBoolean(KEY_CHARGING, false);
        long timestamp = prefs.getLong(KEY_TIMESTAMP, 0L);
        if (level < 0 || level > 100 || timestamp <= 0L) {
            return Snapshot.empty();
        }
        return new Snapshot(level, charging, timestamp);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Context appContext(Context context) {
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
    }

    public static final class Snapshot {
        public final int level;
        public final boolean charging;
        public final long timestamp;

        Snapshot(int level, boolean charging, long timestamp) {
            this.level = level;
            this.charging = charging;
            this.timestamp = timestamp;
        }

        static Snapshot empty() {
            return new Snapshot(-1, false, 0L);
        }

        public boolean hasData() {
            return level >= 0 && level <= 100 && timestamp > 0L;
        }
    }
}
