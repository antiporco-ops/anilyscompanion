package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class PhoneBatteryAutoRefreshSync {

    public static final String SETTINGS_PATH = "/settings/phone_battery_auto_refresh";
    public static final String UI_POKE_PATH = "/settings/phone_battery_auto_refresh_ui_poke";
    private static final String KEY_MINUTES = "minutes";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_UPDATED_BY = "updated_by";
    private static final String UPDATED_BY_PHONE = "phone";
    private static final String UPDATED_BY_WEAR = "wear";
    private static final int UI_POKE_PAYLOAD_SIZE = 12;
    private static final String TAG = "AniLysBattery";

    private PhoneBatteryAutoRefreshSync() {
    }

    public static void setLocalAndSync(Context context, int minutes) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        int safeMinutes = PhoneBatteryAutoRefreshStore.sanitizeMinutes(minutes);
        long updatedAt = System.currentTimeMillis();
        PhoneBatteryAutoRefreshStore.writeMinutes(appContext, safeMinutes, updatedAt);
        PhoneBatterySender.syncPeriodicRefresh(appContext);
        publish(appContext, safeMinutes, updatedAt, UPDATED_BY_PHONE);
        sendUiPoke(appContext, safeMinutes, updatedAt);
    }

    public static void publishCurrent(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        int minutes = PhoneBatteryAutoRefreshStore.readMinutes(appContext);
        long updatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(appContext);
        publish(appContext, minutes, updatedAt, UPDATED_BY_PHONE);
    }

    public static boolean applyIncoming(Context context, DataMap dataMap) {
        Context appContext = appContext(context);
        if (appContext == null || dataMap == null) return false;

        int incomingMinutes = PhoneBatteryAutoRefreshStore.sanitizeMinutes(dataMap.getInt(KEY_MINUTES, 10));
        long incomingUpdatedAt = dataMap.getLong(KEY_UPDATED_AT, 0L);
        String updatedBy = dataMap.getString(KEY_UPDATED_BY, "");
        long localUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(appContext);
        if (incomingUpdatedAt <= localUpdatedAt) {
            return false;
        }

        PhoneBatteryAutoRefreshStore.writeMinutes(appContext, incomingMinutes, incomingUpdatedAt);
        if (UPDATED_BY_WEAR.equals(updatedBy)) {
            PhoneBatteryCompanionStore.markWatchCompanionSeen(appContext);
        }
        PhoneBatterySender.syncPeriodicRefresh(appContext);
        Log.d(TAG, "Applied auto-refresh sync: minutes=" + incomingMinutes + " updatedAt=" + incomingUpdatedAt);
        return true;
    }

    private static void publish(Context context, int minutes, long updatedAt, String updatedBy) {
        PutDataMapRequest request = PutDataMapRequest.create(SETTINGS_PATH);
        request.getDataMap().putInt(KEY_MINUTES, PhoneBatteryAutoRefreshStore.sanitizeMinutes(minutes));
        request.getDataMap().putLong(KEY_UPDATED_AT, updatedAt);
        request.getDataMap().putString(KEY_UPDATED_BY, updatedBy);
        com.google.android.gms.wearable.PutDataRequest putDataRequest = request.asPutDataRequest();
        putDataRequest.setUrgent();
        Wearable.getDataClient(context)
                .putDataItem(putDataRequest)
                .addOnFailureListener(e -> Log.w(TAG, "Failed to sync auto-refresh setting", e));
    }

    private static void sendUiPoke(Context context, int minutes, long updatedAt) {
        byte[] payload = ByteBuffer.allocate(UI_POKE_PAYLOAD_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(PhoneBatteryAutoRefreshStore.sanitizeMinutes(minutes))
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
                                    Log.d(TAG, "ui poke sent minutes=" + minutes + " updatedAt=" + updatedAt))
                            .addOnFailureListener(e -> Log.w(TAG, "Failed to send ui poke", e));
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to resolve nodes for ui poke", e));
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
