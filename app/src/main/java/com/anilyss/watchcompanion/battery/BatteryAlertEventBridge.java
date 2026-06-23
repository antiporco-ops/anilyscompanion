package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class BatteryAlertEventBridge {

    static final String MESSAGE_PATH = "/battery_alert_event";
    static final String SOURCE_PHONE = "phone";
    static final String SOURCE_WATCH = "watch";
    static final String TYPE_HIGH = "high";
    static final String TYPE_LOW = "low";

    private static final String TAG = "AniLysFullAlert";
    private static final String PREFS = "anilys_battery_alert_events";
    private static final String KEY_LAST_PHONE_HIGH = "last_phone_high_event";
    private static final String KEY_LAST_PHONE_LOW = "last_phone_low_event";
    private static final String KEY_LAST_WATCH_HIGH = "last_watch_high_event";
    private static final String KEY_LAST_WATCH_LOW = "last_watch_low_event";

    private BatteryAlertEventBridge() {
    }

    static boolean sendToWatch(Context context, AlertEvent event) {
        Context appContext = appContext(context);
        if (appContext == null || event == null) {
            return false;
        }
        byte[] payload = event.toJson().toString().getBytes(StandardCharsets.UTF_8);
        Wearable.getNodeClient(appContext).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    Node target = selectSingleTarget(nodes);
                    if (target == null) {
                        Log.w(TAG, "remote_event_skip target=watch reason=no_connected_node eventId=" + event.eventId);
                        return;
                    }
                    Wearable.getMessageClient(appContext)
                            .sendMessage(target.getId(), MESSAGE_PATH, payload)
                            .addOnSuccessListener(unused ->
                                    Log.i(TAG, "remote_event_sent target=watch source=" + event.source
                                            + " type=" + event.type
                                            + " eventId=" + event.eventId))
                            .addOnFailureListener(error ->
                                    Log.w(TAG, "remote_event_failed target=watch eventId=" + event.eventId, error));
                })
                .addOnFailureListener(error ->
                        Log.w(TAG, "remote_event_failed target=watch eventId=" + event.eventId, error));
        return true;
    }

    static void handleIncoming(Context context, byte[] payload) {
        Context appContext = appContext(context);
        if (appContext == null || payload == null || payload.length == 0) {
            return;
        }
        AlertEvent event = AlertEvent.fromBytes(payload);
        if (event == null) {
            Log.w(TAG, "remote_event_skip reason=parse_failed");
            return;
        }
        if (!SOURCE_WATCH.equals(event.source)) {
            Log.i(TAG, "remote_event_skip reason=unexpected_source source=" + event.source + " eventId=" + event.eventId);
            return;
        }
        if (isDuplicate(appContext, event)) {
            Log.i(TAG, "remote_event_skip reason=duplicate source=" + event.source
                    + " type=" + event.type
                    + " eventId=" + event.eventId);
            return;
        }
        remember(appContext, event);
        PhoneBatteryFullAlert.postRemoteAlert(appContext, event);
    }

    private static boolean isDuplicate(Context context, AlertEvent event) {
        SharedPreferences prefs = prefs(context);
        String key = keyFor(event);
        return event.eventId.equals(prefs.getString(key, ""));
    }

    private static void remember(Context context, AlertEvent event) {
        prefs(context).edit().putString(keyFor(event), event.eventId).apply();
    }

    private static String keyFor(AlertEvent event) {
        if (SOURCE_PHONE.equals(event.source)) {
            return TYPE_HIGH.equals(event.type) ? KEY_LAST_PHONE_HIGH : KEY_LAST_PHONE_LOW;
        }
        return TYPE_HIGH.equals(event.type) ? KEY_LAST_WATCH_HIGH : KEY_LAST_WATCH_LOW;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Context appContext(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext();
        return appContext != null ? appContext : context;
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

    static final class AlertEvent {
        final String eventId;
        final String source;
        final String type;
        final int level;
        final int limit;
        final boolean charging;
        final long occurredAt;

        AlertEvent(
                String eventId,
                String source,
                String type,
                int level,
                int limit,
                boolean charging,
                long occurredAt
        ) {
            this.eventId = eventId;
            this.source = source;
            this.type = type;
            this.level = level;
            this.limit = limit;
            this.charging = charging;
            this.occurredAt = occurredAt;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("v", 1);
                json.put("eventId", eventId);
                json.put("source", source);
                json.put("type", type);
                json.put("level", level);
                json.put("limit", limit);
                json.put("charging", charging);
                json.put("occurredAt", occurredAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static AlertEvent fromBytes(byte[] payload) {
            try {
                JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
                String eventId = json.optString("eventId", "");
                String source = json.optString("source", "");
                String type = json.optString("type", "");
                int level = json.optInt("level", -1);
                int limit = json.optInt("limit", -1);
                boolean charging = json.optBoolean("charging", false);
                long occurredAt = json.optLong("occurredAt", 0L);
                if (eventId.isEmpty()
                        || level < 0 || level > 100
                        || limit < 0 || limit > 100
                        || (!TYPE_HIGH.equals(type) && !TYPE_LOW.equals(type))
                        || (!SOURCE_PHONE.equals(source) && !SOURCE_WATCH.equals(source))) {
                    return null;
                }
                return new AlertEvent(eventId, source, type, level, limit, charging, occurredAt);
            } catch (JSONException error) {
                return null;
            }
        }

        static AlertEvent create(String source, String type, int level, int limit, boolean charging, long occurredAt) {
            long safeOccurredAt = occurredAt > 0L ? occurredAt : System.currentTimeMillis();
            String eventId = source + ":" + type + ":" + level + ":" + limit + ":" + safeOccurredAt;
            return new AlertEvent(eventId, source, type, level, limit, charging, safeOccurredAt);
        }
    }
}
