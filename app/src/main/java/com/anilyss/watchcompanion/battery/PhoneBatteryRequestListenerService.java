package com.anilyss.watchcompanion.battery;

import android.util.Log;

import com.anilyss.watchcompanion.settings.WatchAppVersionStore;
import com.anilyss.watchcompanion.settings.WatchAppVersionSync;
import com.anilyss.watchcompanion.settings.AppLanguageSync;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

public class PhoneBatteryRequestListenerService extends WearableListenerService {

    private static final String TAG = "AniLysBattery";
    private static final String REQUEST_PATH = "/request_phone_battery";
    private static final byte REQUEST_PAYLOAD_VERSION = 1;
    private static final long NO_REQUEST_ID = -1L;
    private static final int REQUEST_PAYLOAD_SIZE = 10;

    @Override
    public void onCreate() {
        super.onCreate();
        PhoneBatteryAutoRefreshSync.publishCurrent(getApplicationContext());
        PhoneBatteryProtectionSync.publishCurrent(getApplicationContext());
        AppLanguageSync.publishCurrent(getApplicationContext());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        try {
            for (DataEvent event : dataEvents) {
                if (event.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }
                String path = event.getDataItem().getUri().getPath();
                if (PhoneBatteryAutoRefreshSync.SETTINGS_PATH.equals(path)) {
                    PhoneBatteryAutoRefreshSync.applyIncoming(
                            getApplicationContext(),
                            DataMapItem.fromDataItem(event.getDataItem()).getDataMap()
                    );
                    continue;
                }
                if (!WatchBatteryStore.DATA_PATH.equals(path)) {
                    continue;
                }

                int level = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getInt("level", -1);
                if (level < 0 || level > 100) {
                    continue;
                }
                boolean charging = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getBoolean("charging", false);
                long timestamp = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getLong("ts", 0L);
                long safeTimestamp = timestamp > 0L ? timestamp : System.currentTimeMillis();
                if (safeTimestamp < WatchBatteryStore.readTimestamp(getApplicationContext())) {
                    continue;
                }
                WatchBatteryStore.write(getApplicationContext(), level, charging, safeTimestamp);
                PhoneBatteryCompanionStore.markWatchCompanionSeen(getApplicationContext());
            }
        } finally {
            dataEvents.release();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (event == null) {
            return;
        }
        if (WatchAppVersionSync.RESPONSE_PATH.equals(event.getPath())) {
            handleWatchVersionResponse(event.getData());
            return;
        }
        if (!REQUEST_PATH.equals(event.getPath())) {
            return;
        }
        PhoneBatteryCompanionStore.markWatchCompanionSeen(getApplicationContext());
        PhoneBatterySender.syncPeriodicRefresh(getApplicationContext());
        if (!PhoneBatterySender.isFeatureEnabled(getApplicationContext())) {
            Log.d(TAG, "PhoneBatteryRequestListenerService disabled by user; ignoring request");
            return;
        }

        RequestPayload payload = parsePayload(event.getData());
        String reason = payload.reason;
        long requestId = payload.requestId;

        Log.d(TAG, "PhoneBatteryRequestListenerService RX path=" + REQUEST_PATH + " reason=" + reason + " requestId=" + requestId);
        PhoneBatterySender.sendIfNeeded(this, reason, requestId);
    }

    private RequestPayload parsePayload(byte[] data) {
        if (data != null && data.length >= REQUEST_PAYLOAD_SIZE) {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            byte version = buffer.get();
            byte manualFlag = buffer.get();
            if (version == REQUEST_PAYLOAD_VERSION) {
                String reason = manualFlag == 1 ? "manual" : "request";
                long requestId = buffer.getLong();
                return new RequestPayload(reason, requestId);
            }
            Log.d(TAG, "PhoneBatteryRequestListenerService unknown payload version=" + version + "; fallback parse");
        }

        String reason = "request";
        if (data != null && data.length > 0 && data[0] == 1) {
            reason = "manual";
        }
        return new RequestPayload(reason, NO_REQUEST_ID);
    }

    private void handleWatchVersionResponse(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        try {
            String payload = new String(data, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(payload);
            String versionName = json.optString("versionName", null);
            long versionCode = json.optLong("versionCode", 0L);
            if (versionName == null || versionName.trim().isEmpty() || versionCode <= 0L) {
                Log.w(TAG, "Watch version payload invalid");
                return;
            }
            WatchAppVersionStore.write(getApplicationContext(), versionName, versionCode);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse watch version payload", e);
        }
    }

    private static final class RequestPayload {
        final String reason;
        final long requestId;

        RequestPayload(String reason, long requestId) {
            this.reason = reason;
            this.requestId = requestId;
        }
    }
}
