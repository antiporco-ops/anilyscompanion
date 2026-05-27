package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

public final class PhoneBatterySender {

    private static final String TAG = "AniLysBattery";
    public static final String PREFS_NAME = "anilys_settings";
    public static final String KEY_PHONE_BATTERY_ENABLED = "phone_battery_enabled";
    private static final String PREFS = "anilys_battery_prefs";
    private static final String KEY_LAST_TS = "last_ts";
    private static final String KEY_LAST_LVL = "last_level";
    private static final String DATA_PATH = "/phone_battery";
    private static final String KEY_REQUEST_ID = "request_id";
    private static final long NO_REQUEST_ID = -1L;
    private static final String PERIODIC_WORK_NAME = "phone_battery_periodic_refresh";

    private static final long DEFAULT_BURST_GUARD_MS = 2_000L;
    private static final Object LOCK = new Object();
    private static long lastEnqueueMs = 0L;

    private PhoneBatterySender() {
    }

    public static boolean isFeatureEnabled(Context context) {
        if (context == null) return false;
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PHONE_BATTERY_ENABLED, true);
    }

    public static void setFeatureEnabled(Context context, boolean enabled) {
        if (context == null) return;
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PHONE_BATTERY_ENABLED, enabled)
                .apply();
        syncPeriodicRefresh(appContext);
    }

    public static void syncPeriodicRefresh(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        if (isFeatureEnabled(appContext)) {
            schedulePeriodicRefresh(appContext);
        } else {
            cancelPeriodicRefresh(appContext);
        }
    }

    private static void schedulePeriodicRefresh(Context appContext) {
        long delayMinutes = PhoneBatteryAutoRefreshStore.readMinutes(appContext);
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(PhoneBatteryRefreshWorker.class)
                        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                PERIODIC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    private static void cancelPeriodicRefresh(Context appContext) {
        WorkManager.getInstance(appContext).cancelUniqueWork(PERIODIC_WORK_NAME);
    }

    public static long readLastSentAt(Context context) {
        if (context == null) {
            return 0L;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_TS, 0L);
    }

    public static void sendIfNeeded(Context context, String reason) {
        sendIfNeeded(context, reason, NO_REQUEST_ID);
    }

    public static void sendIfNeeded(Context context, String reason, long requestId) {
        if (context == null) {
            return;
        }
        if (!isFeatureEnabled(context)) {
            return;
        }

        Policy policy = policyFor(reason);
        sendIfNeeded(
                context,
                reason,
                policy.minIntervalMs,
                policy.minDeltaPct,
                DEFAULT_BURST_GUARD_MS,
                policy.maxStaleMs,
                requestId
        );
    }

    private static void sendIfNeeded(
            Context context,
            String reason,
            long minIntervalMs,
            int minDeltaPct,
            long burstGuardMs,
            long maxStaleMs,
            long requestId
    ) {
        boolean forceManualWatchResponse = "manual".equals(reason) && requestId != NO_REQUEST_ID;
        long now = System.currentTimeMillis();
        if (!forceManualWatchResponse && isBurstThrottled(now, burstGuardMs)) {
            Log.d(TAG, "Throttled(burst): " + reason);
            return;
        }

        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        PhoneBatterySnapshot snapshot = PhoneBatterySnapshot.readCurrent(appContext);
        if (snapshot == null) {
            Log.w(TAG, "Skipping send (" + reason + "): battery snapshot unavailable");
            return;
        }
        PhoneBatteryFullAlert.evaluateSnapshot(appContext, snapshot, "send:" + reason);
        int pct = snapshot.level;
        boolean charging = snapshot.charging;

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastTs = prefs.getLong(KEY_LAST_TS, 0L);
        int lastLvl = prefs.getInt(KEY_LAST_LVL, -1);

        long ageMs = now - lastTs;
        boolean dueByTime = ageMs >= minIntervalMs;
        boolean dueByDelta = lastLvl < 0 || (pct >= 0 && Math.abs(pct - lastLvl) >= minDeltaPct);
        boolean dueByStale = ageMs >= maxStaleMs;

        boolean allowByPolicy;
        if ("battchg".equals(reason)) {
            allowByPolicy = dueByDelta || dueByStale;
        } else {
            allowByPolicy = dueByDelta || dueByTime || dueByStale;
        }
        if (!forceManualWatchResponse && !allowByPolicy) {
            Log.d(TAG, "Skip send (" + reason + "): pct=" + pct + " last=" + lastLvl + " age=" + ageMs + "ms");
            return;
        }

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(DATA_PATH);
        putDataMapRequest.getDataMap().putInt("level", pct);
        putDataMapRequest.getDataMap().putBoolean("charging", charging);
        putDataMapRequest.getDataMap().putLong("ts", now);
        if (requestId != NO_REQUEST_ID) {
            putDataMapRequest.getDataMap().putLong(KEY_REQUEST_ID, requestId);
        }

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        if ("request".equals(reason) || forceManualWatchResponse) {
            putDataRequest.setUrgent();
        }

        final int sentPct = pct;
        final String sentReason = reason;
        final long sentNow = now;

        Wearable.getDataClient(appContext).putDataItem(putDataRequest)
                .addOnSuccessListener(unused -> {
                    prefs.edit()
                            .putInt(KEY_LAST_LVL, sentPct)
                            .putLong(KEY_LAST_TS, sentNow)
                            .apply();
                    syncPeriodicRefresh(appContext);
                })
                .addOnFailureListener(e -> Log.w(TAG, "PutDataItem failed (" + sentReason + ")", e));
    }

    private static boolean isBurstThrottled(long now, long burstGuardMs) {
        synchronized (LOCK) {
            if (now - lastEnqueueMs < burstGuardMs) {
                return true;
            }
            lastEnqueueMs = now;
            return false;
        }
    }

    private static Policy policyFor(String reason) {
        if ("manual".equals(reason)) {
            return new Policy(10_000L, 1, 2 * 60_000L);
        }
        if ("request".equals(reason)) {
            return new Policy(0L, 1, 4 * 60_000L);
        }
        if ("keepalive".equals(reason)) {
            return new Policy(60_000L, 2, 6 * 60_000L);
        }
        if ("periodic".equals(reason)) {
            return new Policy(60_000L, 2, 6 * 60_000L);
        }
        if ("power_connected".equals(reason)
                || "power_disconnected".equals(reason)
                || "battery_low".equals(reason)
                || "battery_okay".equals(reason)) {
            return new Policy(0L, 0, 2 * 60_000L);
        }
        if ("battchg".equals(reason)) {
            return new Policy(30_000L, 3, 15 * 60_000L);
        }
        return new Policy(30_000L, 2, 5 * 60_000L);
    }

    private static final class Policy {
        final long minIntervalMs;
        final int minDeltaPct;
        final long maxStaleMs;

        Policy(long minIntervalMs, int minDeltaPct, long maxStaleMs) {
            this.minIntervalMs = minIntervalMs;
            this.minDeltaPct = minDeltaPct;
            this.maxStaleMs = maxStaleMs;
        }
    }
}
