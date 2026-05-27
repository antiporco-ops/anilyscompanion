package com.anilyss.watchcompanion.settings;

import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public final class AppLanguageSync {

    public static final String SETTINGS_PATH = "/settings/app_language";
    public static final String UI_POKE_PATH = "/settings/app_language_ui_poke";
    private static final String KEY_LANGUAGE_TAG = "language_tag";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_UPDATED_BY = "updated_by";
    private static final String UPDATED_BY_PHONE = "phone";
    private static final String TAG = "AniLysLang";

    private AppLanguageSync() {
    }

    public static void setLocalAndSync(Context context, String languageTag) {
        Context appContext = appContext(context);
        if (appContext == null) return;

        String safeLanguageTag = AppLanguageStore.sanitizeLanguageTag(languageTag);
        long updatedAt = System.currentTimeMillis();

        AppLanguageStore.writeLanguage(appContext, safeLanguageTag, updatedAt);
        applyLanguageTag(safeLanguageTag);
        publish(appContext, safeLanguageTag, updatedAt, UPDATED_BY_PHONE);
        sendUiPoke(appContext, safeLanguageTag, updatedAt, UPDATED_BY_PHONE);
    }

    public static void publishCurrent(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        String languageTag = AppLanguageStore.readLanguageTag(appContext);
        long updatedAt = AppLanguageStore.readUpdatedAt(appContext);
        publish(appContext, languageTag, updatedAt, UPDATED_BY_PHONE);
    }

    public static void applyStoredLanguage(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        String languageTag = AppLanguageStore.readLanguageTag(appContext);
        applyLanguageTag(languageTag);
    }

    private static void applyLanguageTag(String languageTag) {
        String safeLanguageTag = AppLanguageStore.sanitizeLanguageTag(languageTag);
        LocaleListCompat locales = AppLanguageStore.LANGUAGE_SYSTEM.equals(safeLanguageTag)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(safeLanguageTag);
        AppCompatDelegate.setApplicationLocales(locales);
        Log.d(TAG, "phone language applied tag=" + safeLanguageTag);
    }

    private static void publish(Context context, String languageTag, long updatedAt, String updatedBy) {
        PutDataMapRequest request = PutDataMapRequest.create(SETTINGS_PATH);
        request.getDataMap().putString(KEY_LANGUAGE_TAG, AppLanguageStore.sanitizeLanguageTag(languageTag));
        request.getDataMap().putLong(KEY_UPDATED_AT, updatedAt);
        request.getDataMap().putString(KEY_UPDATED_BY, updatedBy);
        com.google.android.gms.wearable.PutDataRequest putDataRequest = request.asPutDataRequest();
        putDataRequest.setUrgent();

        Wearable.getDataClient(context)
                .putDataItem(putDataRequest)
                .addOnSuccessListener(unused -> Log.d(TAG,
                        "language setting synced tag=" + languageTag + " updatedAt=" + updatedAt))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to sync language setting", e));
    }

    private static void sendUiPoke(Context context, String languageTag, long updatedAt, String updatedBy) {
        DataMap payloadMap = new DataMap();
        payloadMap.putString(KEY_LANGUAGE_TAG, AppLanguageStore.sanitizeLanguageTag(languageTag));
        payloadMap.putLong(KEY_UPDATED_AT, updatedAt);
        payloadMap.putString(KEY_UPDATED_BY, updatedBy);
        byte[] payload = payloadMap.toByteArray();

        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    Node target = selectSingleTarget(nodes);
                    if (target == null) {
                        return;
                    }
                    Wearable.getMessageClient(context)
                            .sendMessage(target.getId(), UI_POKE_PATH, payload)
                            .addOnSuccessListener(unused -> Log.d(TAG,
                                    "language poke sent tag=" + languageTag + " updatedAt=" + updatedAt))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to send language poke", e));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to resolve nodes for language poke", e));
    }

    private static Node selectSingleTarget(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node;
            }
        }
        return nodes.get(0);
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }
}
