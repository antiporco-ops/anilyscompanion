package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class PhoneBatteryProtectionSync {

    public static final String SETTINGS_PATH = "/settings/battery_protection";
    public static final String UI_POKE_PATH = "/settings/battery_protection_ui_poke";
    private static final String KEY_HIGH_LIMIT = "high_limit";
    private static final String KEY_LOW_LIMIT = "low_limit";
    private static final String KEY_PHONE_ENABLED = "phone_enabled";
    private static final String KEY_WATCH_ENABLED = "watch_enabled";
    private static final String KEY_ALERTS_ENABLED = "alerts_enabled";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_UPDATED_BY = "updated_by";
    private static final String UPDATED_BY_PHONE = "phone";
    private static final String PREF_KEY_SYNC_UPDATED_AT = "battery_protection_sync_updated_at";
    private static final int UI_POKE_PAYLOAD_SIZE = 18;
    private static final String TAG = "AniLysBattery";

    private PhoneBatteryProtectionSync() {
    }

    public static void publishCurrent(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;

        long updatedAt = readSyncUpdatedAt(appContext);
        if (updatedAt <= 0L) {
            updatedAt = System.currentTimeMillis();
            writeSyncUpdatedAt(appContext, updatedAt);
        }
        publish(appContext, updatedAt);
    }

    public static void setLocalAndSync(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;

        long updatedAt = System.currentTimeMillis();
        writeSyncUpdatedAt(appContext, updatedAt);
        publish(appContext, updatedAt);
        sendUiPoke(appContext, updatedAt);
    }

    private static void publish(Context context, long updatedAt) {
        int highLimit = PhoneBatteryFullAlert.readHighLimitPercent(context);
        int lowLimit = PhoneBatteryFullAlert.readLowLimitPercent(context);
        boolean phoneEnabled = PhoneBatteryFullAlert.isPhoneProtectionEnabled(context);
        boolean watchEnabled = PhoneBatteryFullAlert.isWatchProtectionEnabled(context);
        boolean alertsEnabled = phoneEnabled || watchEnabled;

        PutDataMapRequest request = PutDataMapRequest.create(SETTINGS_PATH);
        request.getDataMap().putInt(KEY_HIGH_LIMIT, highLimit);
        request.getDataMap().putInt(KEY_LOW_LIMIT, lowLimit);
        request.getDataMap().putBoolean(KEY_PHONE_ENABLED, phoneEnabled);
        request.getDataMap().putBoolean(KEY_WATCH_ENABLED, watchEnabled);
        request.getDataMap().putBoolean(KEY_ALERTS_ENABLED, alertsEnabled);
        request.getDataMap().putLong(KEY_UPDATED_AT, updatedAt);
        request.getDataMap().putString(KEY_UPDATED_BY, UPDATED_BY_PHONE);

        com.google.android.gms.wearable.PutDataRequest putDataRequest = request.asPutDataRequest();
        putDataRequest.setUrgent();
        Wearable.getDataClient(context)
                .putDataItem(putDataRequest)
                .addOnFailureListener(e -> Log.w(TAG, "Failed to sync protection settings", e));
    }

    private static void sendUiPoke(Context context, long updatedAt) {
        int highLimit = PhoneBatteryFullAlert.readHighLimitPercent(context);
        int lowLimit = PhoneBatteryFullAlert.readLowLimitPercent(context);
        boolean phoneEnabled = PhoneBatteryFullAlert.isPhoneProtectionEnabled(context);
        boolean watchEnabled = PhoneBatteryFullAlert.isWatchProtectionEnabled(context);
        byte[] payload = ByteBuffer.allocate(UI_POKE_PAYLOAD_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(highLimit)
                .putInt(lowLimit)
                .put((byte) (phoneEnabled ? 1 : 0))
                .put((byte) (watchEnabled ? 1 : 0))
                .putLong(updatedAt)
                .array();

        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    Node target = selectSingleTarget(nodes);
                    if (target == null) {
                        return;
                    }
                    Wearable.getMessageClient(context)
                            .sendMessage(target.getId(), UI_POKE_PATH, payload)
                            .addOnSuccessListener(unused ->
                                    Log.d(TAG, "protection ui poke sent high=" + highLimit
                                            + " low=" + lowLimit
                                            + " phone=" + phoneEnabled
                                            + " watch=" + watchEnabled
                                            + " updatedAt=" + updatedAt))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to send protection ui poke", e));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to resolve nodes for protection ui poke", e));
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

    private static long readSyncUpdatedAt(Context context) {
        return prefs(context).getLong(PREF_KEY_SYNC_UPDATED_AT, 0L);
    }

    private static void writeSyncUpdatedAt(Context context, long updatedAt) {
        prefs(context).edit().putLong(PREF_KEY_SYNC_UPDATED_AT, updatedAt).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }
}
