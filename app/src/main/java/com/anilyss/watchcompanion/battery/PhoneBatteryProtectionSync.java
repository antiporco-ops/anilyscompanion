package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;
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
    private static final String KEY_MONITOR_PHONE_ENABLED = "monitor_phone_enabled";
    private static final String KEY_MONITOR_WATCH_ENABLED = "monitor_watch_enabled";
    private static final String KEY_ALERT_PHONE_ON_PHONE = "alert_phone_on_phone";
    private static final String KEY_ALERT_PHONE_ON_WATCH = "alert_phone_on_watch";
    private static final String KEY_ALERT_WATCH_ON_PHONE = "alert_watch_on_phone";
    private static final String KEY_ALERT_WATCH_ON_WATCH = "alert_watch_on_watch";
    private static final String KEY_PHONE_ENABLED = "phone_enabled";
    private static final String KEY_WATCH_ENABLED = "watch_enabled";
    private static final String KEY_ALERTS_ENABLED = "alerts_enabled";
    private static final String KEY_PHONE_SOUND_ENABLED = "phone_sound_enabled";
    private static final String KEY_PHONE_VIBRATION_ENABLED = "phone_vibration_enabled";
    private static final String KEY_WATCH_SOUND_ENABLED = "watch_sound_enabled";
    private static final String KEY_WATCH_VIBRATION_ENABLED = "watch_vibration_enabled";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_UPDATED_BY = "updated_by";
    private static final String UPDATED_BY_PHONE = "phone";
    private static final String UPDATED_BY_WEAR = "wear";
    private static final String PREF_KEY_SYNC_UPDATED_AT = "battery_protection_sync_updated_at";
    private static final int UI_POKE_PAYLOAD_SIZE_LEGACY = 18;
    private static final int UI_POKE_PAYLOAD_SIZE_WITH_ALERT_OPTIONS = 22;
    private static final int UI_POKE_PAYLOAD_SIZE = 26;
    private static final String TAG = "AniLysBattery";

    private PhoneBatteryProtectionSync() {
    }

    public static void publishCurrent(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.normalizeStoredState(appContext, "sync_publish_current");

        long updatedAt = readSyncUpdatedAt(appContext);
        if (updatedAt <= 0L) {
            updatedAt = System.currentTimeMillis();
            writeSyncUpdatedAt(appContext, updatedAt);
        }
        Log.i(TAG, "protection_sync_publish_current updatedAt=" + updatedAt
                + " monitorPhone=" + state.monitorPhoneEnabled
                + " monitorWatch=" + state.monitorWatchEnabled
                + " alertPhoneOnPhone=" + state.alertPhoneOnPhoneEnabled
                + " alertPhoneOnWatch=" + state.alertPhoneOnWatchEnabled
                + " alertWatchOnPhone=" + state.alertWatchOnPhoneEnabled
                + " alertWatchOnWatch=" + state.alertWatchOnWatchEnabled
                + " source=" + state.source);
        publish(appContext, updatedAt);
    }

    public static boolean applyIncoming(Context context, DataMap dataMap) {
        Context appContext = appContext(context);
        if (appContext == null || dataMap == null) {
            return false;
        }

        long incomingUpdatedAt = dataMap.getLong(KEY_UPDATED_AT, 0L);
        long localUpdatedAt = readSyncUpdatedAt(appContext);
        if (incomingUpdatedAt <= localUpdatedAt) {
            Log.d(TAG, "Ignored stale protection sync updatedAt=" + incomingUpdatedAt
                    + " localUpdatedAt=" + localUpdatedAt);
            return false;
        }

        int highLimit = dataMap.getInt(KEY_HIGH_LIMIT, -1);
        int lowLimit = dataMap.getInt(KEY_LOW_LIMIT, -1);
        boolean alertsEnabled = dataMap.getBoolean(KEY_ALERTS_ENABLED, false);
        boolean legacyPhoneEnabled = dataMap.containsKey(KEY_PHONE_ENABLED)
                ? dataMap.getBoolean(KEY_PHONE_ENABLED)
                : alertsEnabled;
        boolean legacyWatchEnabled = dataMap.containsKey(KEY_WATCH_ENABLED)
                ? dataMap.getBoolean(KEY_WATCH_ENABLED)
                : alertsEnabled;
        boolean monitorPhoneEnabled = dataMap.getBoolean(KEY_MONITOR_PHONE_ENABLED, legacyPhoneEnabled);
        boolean monitorWatchEnabled = dataMap.getBoolean(KEY_MONITOR_WATCH_ENABLED, legacyWatchEnabled);
        boolean alertPhoneOnPhoneEnabled = dataMap.getBoolean(KEY_ALERT_PHONE_ON_PHONE, legacyPhoneEnabled);
        boolean alertPhoneOnWatchEnabled = dataMap.getBoolean(KEY_ALERT_PHONE_ON_WATCH, legacyWatchEnabled);
        boolean alertWatchOnPhoneEnabled = dataMap.getBoolean(KEY_ALERT_WATCH_ON_PHONE, legacyPhoneEnabled);
        boolean alertWatchOnWatchEnabled = dataMap.getBoolean(KEY_ALERT_WATCH_ON_WATCH, legacyWatchEnabled);
        boolean phoneSoundEnabled = dataMap.getBoolean(KEY_PHONE_SOUND_ENABLED, true);
        boolean phoneVibrationEnabled = dataMap.getBoolean(KEY_PHONE_VIBRATION_ENABLED, true);
        boolean watchSoundEnabled = dataMap.getBoolean(KEY_WATCH_SOUND_ENABLED, true);
        boolean watchVibrationEnabled = dataMap.getBoolean(KEY_WATCH_VIBRATION_ENABLED, true);
        String updatedBy = dataMap.getString(KEY_UPDATED_BY, "");

        if (highLimit < 0 || highLimit > 100 || lowLimit < 0 || lowLimit > 100 || lowLimit > highLimit) {
            Log.w(TAG, "Ignored invalid protection sync high=" + highLimit
                    + " low=" + lowLimit
                    + " updatedAt=" + incomingUpdatedAt);
            return false;
        }

        return applyIncomingState(
                appContext,
                highLimit,
                lowLimit,
                monitorPhoneEnabled,
                monitorWatchEnabled,
                alertPhoneOnPhoneEnabled,
                alertPhoneOnWatchEnabled,
                alertWatchOnPhoneEnabled,
                alertWatchOnWatchEnabled,
                phoneSoundEnabled,
                phoneVibrationEnabled,
                watchSoundEnabled,
                watchVibrationEnabled,
                incomingUpdatedAt,
                "data",
                updatedBy
        );
    }

    public static boolean applyIncomingUiPoke(Context context, byte[] payload) {
        Context appContext = appContext(context);
        if (appContext == null || payload == null || payload.length < UI_POKE_PAYLOAD_SIZE_LEGACY) {
            return false;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int highLimit = buffer.getInt();
        int lowLimit = buffer.getInt();
        boolean monitorPhoneEnabled;
        boolean monitorWatchEnabled;
        boolean alertPhoneOnPhoneEnabled;
        boolean alertPhoneOnWatchEnabled;
        boolean alertWatchOnPhoneEnabled;
        boolean alertWatchOnWatchEnabled;
        long incomingUpdatedAt;
        boolean phoneSoundEnabled = true;
        boolean phoneVibrationEnabled = true;
        boolean watchSoundEnabled = true;
        boolean watchVibrationEnabled = true;

        if (payload.length >= UI_POKE_PAYLOAD_SIZE) {
            monitorPhoneEnabled = buffer.get() != 0;
            monitorWatchEnabled = buffer.get() != 0;
            alertPhoneOnPhoneEnabled = buffer.get() != 0;
            alertPhoneOnWatchEnabled = buffer.get() != 0;
            alertWatchOnPhoneEnabled = buffer.get() != 0;
            alertWatchOnWatchEnabled = buffer.get() != 0;
            incomingUpdatedAt = buffer.getLong();
            phoneSoundEnabled = buffer.get() != 0;
            phoneVibrationEnabled = buffer.get() != 0;
            watchSoundEnabled = buffer.get() != 0;
            watchVibrationEnabled = buffer.get() != 0;
        } else {
            boolean legacyPhoneEnabled = buffer.get() != 0;
            boolean legacyWatchEnabled = buffer.get() != 0;
            monitorPhoneEnabled = legacyPhoneEnabled;
            monitorWatchEnabled = legacyWatchEnabled;
            alertPhoneOnPhoneEnabled = legacyPhoneEnabled;
            alertPhoneOnWatchEnabled = legacyWatchEnabled;
            alertWatchOnPhoneEnabled = legacyPhoneEnabled;
            alertWatchOnWatchEnabled = legacyWatchEnabled;
            incomingUpdatedAt = buffer.getLong();
            if (payload.length >= UI_POKE_PAYLOAD_SIZE_WITH_ALERT_OPTIONS) {
                phoneSoundEnabled = buffer.get() != 0;
                phoneVibrationEnabled = buffer.get() != 0;
                watchSoundEnabled = buffer.get() != 0;
                watchVibrationEnabled = buffer.get() != 0;
            }
        }

        long localUpdatedAt = readSyncUpdatedAt(appContext);
        if (incomingUpdatedAt <= localUpdatedAt) {
            Log.d(TAG, "Ignored stale protection ui poke updatedAt=" + incomingUpdatedAt
                    + " localUpdatedAt=" + localUpdatedAt
                    + " payloadSize=" + payload.length);
            return false;
        }
        if (highLimit < 0 || highLimit > 100 || lowLimit < 0 || lowLimit > 100 || lowLimit > highLimit) {
            Log.w(TAG, "Ignored invalid protection ui poke high=" + highLimit
                    + " low=" + lowLimit
                    + " updatedAt=" + incomingUpdatedAt
                    + " payloadSize=" + payload.length);
            return false;
        }

        return applyIncomingState(
                appContext,
                highLimit,
                lowLimit,
                monitorPhoneEnabled,
                monitorWatchEnabled,
                alertPhoneOnPhoneEnabled,
                alertPhoneOnWatchEnabled,
                alertWatchOnPhoneEnabled,
                alertWatchOnWatchEnabled,
                phoneSoundEnabled,
                phoneVibrationEnabled,
                watchSoundEnabled,
                watchVibrationEnabled,
                incomingUpdatedAt,
                "ui_poke",
                UPDATED_BY_WEAR
        );
    }

    public static void setLocalAndSync(Context context) {
        Context appContext = appContext(context);
        if (appContext == null) return;
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.normalizeStoredState(appContext, "sync_set_local");

        long updatedAt = System.currentTimeMillis();
        writeSyncUpdatedAt(appContext, updatedAt);
        Log.i(TAG, "protection_sync_set_local updatedAt=" + updatedAt
                + " monitorPhone=" + state.monitorPhoneEnabled
                + " monitorWatch=" + state.monitorWatchEnabled
                + " alertPhoneOnPhone=" + state.alertPhoneOnPhoneEnabled
                + " alertPhoneOnWatch=" + state.alertPhoneOnWatchEnabled
                + " alertWatchOnPhone=" + state.alertWatchOnPhoneEnabled
                + " alertWatchOnWatch=" + state.alertWatchOnWatchEnabled
                + " source=" + state.source);
        publish(appContext, updatedAt);
        sendUiPoke(appContext, updatedAt);
    }

    private static void publish(Context context, long updatedAt) {
        int highLimit = PhoneBatteryFullAlert.readHighLimitPercent(context);
        int lowLimit = PhoneBatteryFullAlert.readLowLimitPercent(context);
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.readCurrentState(context, "sync_publish_payload");
        boolean phoneSound = PhoneBatteryFullAlert.isPhoneSoundEnabled(context);
        boolean phoneVibration = PhoneBatteryFullAlert.isPhoneVibrationEnabled(context);
        boolean watchSound = PhoneBatteryFullAlert.isWatchSoundEnabled(context);
        boolean watchVibration = PhoneBatteryFullAlert.isWatchVibrationEnabled(context);
        boolean alertsEnabled = state.alertsEnabled;
        Log.i(TAG, "protection_sync_payload reason=publish"
                + " high=" + highLimit
                + " low=" + lowLimit
                + " monitorPhone=" + state.monitorPhoneEnabled
                + " monitorWatch=" + state.monitorWatchEnabled
                + " alertPhoneOnPhone=" + state.alertPhoneOnPhoneEnabled
                + " alertPhoneOnWatch=" + state.alertPhoneOnWatchEnabled
                + " alertWatchOnPhone=" + state.alertWatchOnPhoneEnabled
                + " alertWatchOnWatch=" + state.alertWatchOnWatchEnabled
                + " phoneSound=" + phoneSound
                + " phoneVibration=" + phoneVibration
                + " watchSound=" + watchSound
                + " watchVibration=" + watchVibration
                + " updatedAt=" + updatedAt);

        PutDataMapRequest request = PutDataMapRequest.create(SETTINGS_PATH);
        request.getDataMap().putInt(KEY_HIGH_LIMIT, highLimit);
        request.getDataMap().putInt(KEY_LOW_LIMIT, lowLimit);
        request.getDataMap().putBoolean(KEY_MONITOR_PHONE_ENABLED, state.monitorPhoneEnabled);
        request.getDataMap().putBoolean(KEY_MONITOR_WATCH_ENABLED, state.monitorWatchEnabled);
        request.getDataMap().putBoolean(KEY_ALERT_PHONE_ON_PHONE, state.alertPhoneOnPhoneEnabled);
        request.getDataMap().putBoolean(KEY_ALERT_PHONE_ON_WATCH, state.alertPhoneOnWatchEnabled);
        request.getDataMap().putBoolean(KEY_ALERT_WATCH_ON_PHONE, state.alertWatchOnPhoneEnabled);
        request.getDataMap().putBoolean(KEY_ALERT_WATCH_ON_WATCH, state.alertWatchOnWatchEnabled);
        request.getDataMap().putBoolean(KEY_PHONE_ENABLED, state.monitorPhoneEnabled);
        request.getDataMap().putBoolean(KEY_WATCH_ENABLED, state.monitorWatchEnabled);
        request.getDataMap().putBoolean(KEY_ALERTS_ENABLED, alertsEnabled);
        request.getDataMap().putBoolean(KEY_PHONE_SOUND_ENABLED, phoneSound);
        request.getDataMap().putBoolean(KEY_PHONE_VIBRATION_ENABLED, phoneVibration);
        request.getDataMap().putBoolean(KEY_WATCH_SOUND_ENABLED, watchSound);
        request.getDataMap().putBoolean(KEY_WATCH_VIBRATION_ENABLED, watchVibration);
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
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.readCurrentState(context, "sync_send_ui_poke");
        boolean phoneSound = PhoneBatteryFullAlert.isPhoneSoundEnabled(context);
        boolean phoneVibration = PhoneBatteryFullAlert.isPhoneVibrationEnabled(context);
        boolean watchSound = PhoneBatteryFullAlert.isWatchSoundEnabled(context);
        boolean watchVibration = PhoneBatteryFullAlert.isWatchVibrationEnabled(context);
        byte[] payload = ByteBuffer.allocate(UI_POKE_PAYLOAD_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(highLimit)
                .putInt(lowLimit)
                .put((byte) (state.monitorPhoneEnabled ? 1 : 0))
                .put((byte) (state.monitorWatchEnabled ? 1 : 0))
                .put((byte) (state.alertPhoneOnPhoneEnabled ? 1 : 0))
                .put((byte) (state.alertPhoneOnWatchEnabled ? 1 : 0))
                .put((byte) (state.alertWatchOnPhoneEnabled ? 1 : 0))
                .put((byte) (state.alertWatchOnWatchEnabled ? 1 : 0))
                .putLong(updatedAt)
                .put((byte) (phoneSound ? 1 : 0))
                .put((byte) (phoneVibration ? 1 : 0))
                .put((byte) (watchSound ? 1 : 0))
                .put((byte) (watchVibration ? 1 : 0))
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
                                            + " monitorPhone=" + state.monitorPhoneEnabled
                                            + " monitorWatch=" + state.monitorWatchEnabled
                                            + " alertPhoneOnPhone=" + state.alertPhoneOnPhoneEnabled
                                            + " alertPhoneOnWatch=" + state.alertPhoneOnWatchEnabled
                                            + " alertWatchOnPhone=" + state.alertWatchOnPhoneEnabled
                                            + " alertWatchOnWatch=" + state.alertWatchOnWatchEnabled
                                            + " phoneSound=" + phoneSound
                                            + " phoneVibration=" + phoneVibration
                                            + " watchSound=" + watchSound
                                            + " watchVibration=" + watchVibration
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

    private static boolean applyIncomingState(
            Context context,
            int highLimit,
            int lowLimit,
            boolean monitorPhoneEnabled,
            boolean monitorWatchEnabled,
            boolean alertPhoneOnPhoneEnabled,
            boolean alertPhoneOnWatchEnabled,
            boolean alertWatchOnPhoneEnabled,
            boolean alertWatchOnWatchEnabled,
            boolean phoneSoundEnabled,
            boolean phoneVibrationEnabled,
            boolean watchSoundEnabled,
            boolean watchVibrationEnabled,
            long updatedAt,
            String source,
            String updatedBy
    ) {
        SharedPreferences prefs = prefs(context);
        boolean alertsEnabled = alertPhoneOnPhoneEnabled
                || alertPhoneOnWatchEnabled
                || alertWatchOnPhoneEnabled
                || alertWatchOnWatchEnabled;
        prefs.edit()
                .putInt(PhoneBatteryFullAlert.KEY_HIGH_LIMIT_PERCENT, highLimit)
                .putInt(PhoneBatteryFullAlert.KEY_LOW_LIMIT_PERCENT, lowLimit)
                .putBoolean(PhoneBatteryFullAlert.KEY_MONITOR_PHONE_BATTERY, monitorPhoneEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_MONITOR_WATCH_BATTERY, monitorWatchEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_ALERT_PHONE_ON_PHONE, alertPhoneOnPhoneEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_ALERT_PHONE_ON_WATCH, alertPhoneOnWatchEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_ALERT_WATCH_ON_PHONE, alertWatchOnPhoneEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_ALERT_WATCH_ON_WATCH, alertWatchOnWatchEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_PHONE_ENABLED, monitorPhoneEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_WATCH_ENABLED, monitorWatchEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_ENABLED, alertsEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_PHONE_SOUND_ENABLED, phoneSoundEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_PHONE_VIBRATION_ENABLED, phoneVibrationEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_WATCH_SOUND_ENABLED, watchSoundEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_WATCH_VIBRATION_ENABLED, watchVibrationEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_SOUND_ENABLED, phoneSoundEnabled)
                .putBoolean(PhoneBatteryFullAlert.KEY_VIBRATION_ENABLED, phoneVibrationEnabled)
                .apply();
        writeSyncUpdatedAt(context, updatedAt);
        if (UPDATED_BY_WEAR.equals(updatedBy)) {
            PhoneBatteryCompanionStore.markWatchCompanionSeen(context);
        }
        PhoneBatteryFullAlert.normalizeStoredState(context, "sync_apply_" + source);
        PhoneBatteryFullAlert.ensureNotificationChannels(context);
        PhoneBatteryFullAlert.ensureMonitoring(context, "sync_apply_" + source);
        PhoneBatteryFullAlert.requestImmediateCheck(context, "sync_apply_" + source);
        Log.i(TAG, "Applied protection sync source=" + source
                + " high=" + highLimit
                + " low=" + lowLimit
                + " monitorPhone=" + monitorPhoneEnabled
                + " monitorWatch=" + monitorWatchEnabled
                + " alertPhoneOnPhone=" + alertPhoneOnPhoneEnabled
                + " alertPhoneOnWatch=" + alertPhoneOnWatchEnabled
                + " alertWatchOnPhone=" + alertWatchOnPhoneEnabled
                + " alertWatchOnWatch=" + alertWatchOnWatchEnabled
                + " phoneSound=" + phoneSoundEnabled
                + " phoneVibration=" + phoneVibrationEnabled
                + " watchSound=" + watchSoundEnabled
                + " watchVibration=" + watchVibrationEnabled
                + " updatedAt=" + updatedAt
                + " updatedBy=" + updatedBy);
        return true;
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
