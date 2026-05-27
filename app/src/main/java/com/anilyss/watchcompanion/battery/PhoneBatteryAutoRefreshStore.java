package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.content.SharedPreferences;

public final class PhoneBatteryAutoRefreshStore {

    private static final String KEY_AUTO_REFRESH_MINUTES = "phone_battery_auto_refresh_minutes";
    private static final String KEY_AUTO_REFRESH_UPDATED_AT = "phone_battery_auto_refresh_updated_at";
    private static final int DEFAULT_AUTO_REFRESH_MINUTES = 10;

    private PhoneBatteryAutoRefreshStore() {
    }

    public static int readMinutes(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return DEFAULT_AUTO_REFRESH_MINUTES;
        SharedPreferences prefs = appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE);
        int raw = prefs.getInt(KEY_AUTO_REFRESH_MINUTES, DEFAULT_AUTO_REFRESH_MINUTES);
        return sanitizeMinutes(raw);
    }

    public static long readUpdatedAt(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return 0L;
        SharedPreferences prefs = appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_AUTO_REFRESH_UPDATED_AT, 0L);
    }

    public static void writeMinutes(Context context, int minutes) {
        writeMinutes(context, minutes, System.currentTimeMillis());
    }

    public static void writeMinutes(Context context, int minutes, long updatedAt) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        int safeMinutes = sanitizeMinutes(minutes);
        long safeUpdatedAt = updatedAt > 0L ? updatedAt : System.currentTimeMillis();
        appContext.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_AUTO_REFRESH_MINUTES, safeMinutes)
                .putLong(KEY_AUTO_REFRESH_UPDATED_AT, safeUpdatedAt)
                .apply();
    }

    public static int sanitizeMinutes(int minutes) {
        if (minutes == 5 || minutes == 10 || minutes == 15) {
            return minutes;
        }
        return DEFAULT_AUTO_REFRESH_MINUTES;
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }
}
