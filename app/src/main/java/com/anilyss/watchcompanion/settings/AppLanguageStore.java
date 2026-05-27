package com.anilyss.watchcompanion.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppLanguageStore {

    public static final String PREFS_SETTINGS = "settings";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_LANGUAGE_UPDATED_AT = "app_language_updated_at";

    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_PT = "pt";
    public static final String LANGUAGE_ES = "es";

    private AppLanguageStore() {
    }

    public static String readLanguageTag(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return LANGUAGE_SYSTEM;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM);
        return sanitizeLanguageTag(raw);
    }

    public static long readUpdatedAt(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return 0L;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LANGUAGE_UPDATED_AT, 0L);
    }

    public static void writeLanguage(Context context, String languageTag) {
        writeLanguage(context, languageTag, System.currentTimeMillis());
    }

    public static void writeLanguage(Context context, String languageTag, long updatedAt) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        String safeTag = sanitizeLanguageTag(languageTag);
        long safeUpdatedAt = updatedAt > 0L ? updatedAt : System.currentTimeMillis();
        appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, safeTag)
                .putLong(KEY_LANGUAGE_UPDATED_AT, safeUpdatedAt)
                .apply();
    }

    public static String sanitizeLanguageTag(String languageTag) {
        if (LANGUAGE_EN.equals(languageTag)) {
            return LANGUAGE_EN;
        }
        if (LANGUAGE_PT.equals(languageTag)) {
            return LANGUAGE_PT;
        }
        if (LANGUAGE_ES.equals(languageTag)) {
            return LANGUAGE_ES;
        }
        return LANGUAGE_SYSTEM;
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }
}
