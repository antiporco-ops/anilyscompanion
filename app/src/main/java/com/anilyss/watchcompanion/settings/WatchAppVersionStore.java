package com.anilyss.watchcompanion.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class WatchAppVersionStore {

    private static final String PREFS = "watch_app_version_store";
    private static final String KEY_NAME = "watch_version_name";
    private static final String KEY_CODE = "watch_version_code";
    private static final String KEY_UPDATED_AT = "watch_version_updated_at";

    private WatchAppVersionStore() {
    }

    @NonNull
    public static Snapshot read(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String versionName = sanitize(prefs.getString(KEY_NAME, null));
        long versionCode = prefs.getLong(KEY_CODE, 0L);
        long updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L);
        return new Snapshot(versionName, versionCode, updatedAt);
    }

    public static void write(@NonNull Context context, @NonNull String versionName, long versionCode) {
        String safeName = sanitize(versionName);
        if (safeName == null || versionCode <= 0L) {
            return;
        }

        Context appContext = context.getApplicationContext();
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, safeName)
                .putLong(KEY_CODE, versionCode)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public static void seedIfMissing(
            @NonNull Context context,
            @NonNull String versionName,
            long versionCode
    ) {
        Snapshot current = read(context);
        if (current.hasValue()) {
            return;
        }
        write(context, versionName, versionCode);
    }

    public static void addChangeListener(
            @NonNull Context context,
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        Context appContext = context.getApplicationContext();
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(listener);
    }

    public static void removeChangeListener(
            @NonNull Context context,
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        Context appContext = context.getApplicationContext();
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static boolean isVersionKey(@Nullable String key) {
        return KEY_NAME.equals(key) || KEY_CODE.equals(key) || KEY_UPDATED_AT.equals(key);
    }

    public static final class Snapshot {
        public final String versionName;
        public final long versionCode;
        public final long updatedAt;

        private Snapshot(String versionName, long versionCode, long updatedAt) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.updatedAt = updatedAt;
        }

        public boolean hasValue() {
            return versionName != null && versionCode > 0L;
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
