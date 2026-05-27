package com.anilyss.watchcompanion.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class AppThemeStore {

    public static final String PREFS_SETTINGS = "settings";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private AppThemeStore() {
    }

    public static String readThemeMode(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return THEME_SYSTEM;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        return sanitizeThemeMode(prefs.getString(KEY_THEME_MODE, THEME_SYSTEM));
    }

    public static boolean writeThemeMode(Context context, String mode) {
        Context appContext = appContext(context);
        if (appContext == null) return false;
        String safeMode = sanitizeThemeMode(mode);
        String currentMode = readThemeMode(appContext);
        if (safeMode.equals(currentMode)) {
            return false;
        }
        appContext.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_MODE, safeMode)
                .apply();
        return true;
    }

    public static void applyStoredTheme(Context context) {
        int targetMode = toNightMode(readThemeMode(context));
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    public static int toNightMode(String mode) {
        String safeMode = sanitizeThemeMode(mode);
        if (THEME_LIGHT.equals(safeMode)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (THEME_DARK.equals(safeMode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static String sanitizeThemeMode(String mode) {
        if (THEME_LIGHT.equals(mode)) {
            return THEME_LIGHT;
        }
        if (THEME_DARK.equals(mode)) {
            return THEME_DARK;
        }
        return THEME_SYSTEM;
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }
}
