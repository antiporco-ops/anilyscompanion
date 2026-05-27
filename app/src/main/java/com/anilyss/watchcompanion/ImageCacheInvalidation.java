package com.anilyss.watchcompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

final class ImageCacheInvalidation {

    private static final String PREFS_NAME = "maintenance_image_cache";
    private static final String KEY_VERSION = "remote_image_cache_bust_version";
    private static final String PARAM_NAME = "acb";

    private ImageCacheInvalidation() {
    }

    static void bumpVersion(@NonNull Context context) {
        long next = System.currentTimeMillis();
        prefs(context).edit().putLong(KEY_VERSION, next).apply();
    }

    @Nullable
    static String resolveLoadUrl(@NonNull Context context, @Nullable String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Uri uri = Uri.parse(trimmed);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return trimmed;
        }
        String normalizedScheme = scheme.toLowerCase(Locale.US);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            return trimmed;
        }

        long version = prefs(context).getLong(KEY_VERSION, 0L);
        if (version <= 0L) {
            return trimmed;
        }
        return uri.buildUpon()
                .appendQueryParameter(PARAM_NAME, Long.toString(version))
                .build()
                .toString();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
