package com.anilyss.watchcompanion.battery;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.anilyss.watchcompanion.MainActivity;
import com.anilyss.watchcompanion.R;

import java.util.concurrent.TimeUnit;

public final class PhoneBatteryFullAlert {

    private static final String TAG = "AniLysFullAlert";
    public static final String KEY_ENABLED = "batteryAlertsEnabled";
    public static final String KEY_MONITOR_PHONE_BATTERY = "monitorPhoneBattery";
    public static final String KEY_HIGH_LIMIT_PERCENT = "highBatteryLimitPercent";
    public static final String KEY_LOW_LIMIT_PERCENT = "lowBatteryLimitPercent";
    public static final String KEY_PHONE_ENABLED = "batteryProtectionPhoneEnabled";
    public static final String KEY_WATCH_ENABLED = "batteryProtectionWatchEnabled";
    public static final String KEY_SOUND_ENABLED = "alertSoundEnabled";
    public static final String KEY_VIBRATION_ENABLED = "alertVibrationEnabled";
    public static final String KEY_LAST_HIGH_ALERT_AT = "lastHighAlertAt";
    public static final String KEY_LAST_LOW_ALERT_AT = "lastLowAlertAt";
    private static final String LEGACY_KEY_ENABLED = "phone_battery_full_alert_enabled";
    private static final String KEY_HIGH_ARMED = "phone_battery_alert_high_armed";
    private static final String KEY_LOW_ARMED = "phone_battery_alert_low_armed";
    private static final String KEY_LAST_EVAL_AT = "phone_battery_full_alert_last_eval_at";
    private static final String KEY_LAST_REASON = "phone_battery_full_alert_last_reason";
    private static final String WORK_NAME = "phone_battery_full_alert_monitor";
    private static final String CHANNEL_ID = "phone_battery_alerts";
    private static final int NOTIFICATION_ID = 4100;
    private static final int HIGH_NOTIFICATION_ID = 4101;
    private static final int LOW_NOTIFICATION_ID = 4102;
    private static final int DEFAULT_HIGH_LIMIT = 80;
    private static final int DEFAULT_LOW_LIMIT = 20;
    private static final int LIMIT_STEP = 5;
    private static final long MIN_REPEAT_MS = TimeUnit.HOURS.toMillis(6);
    private static final long ACTIVE_MONITOR_DELAY_MINUTES = 5L;
    private static final long IDLE_MONITOR_DELAY_MINUTES = 15L;

    private PhoneBatteryFullAlert() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        if (prefs.contains(KEY_ENABLED)) {
            return prefs.getBoolean(KEY_ENABLED, false);
        }
        return prefs.getBoolean(LEGACY_KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_MONITOR_PHONE_BATTERY, prefs.getBoolean(KEY_MONITOR_PHONE_BATTERY, true))
                .apply();

        if (!enabled) {
            prefs.edit()
                    .putBoolean(KEY_HIGH_ARMED, false)
                    .putBoolean(KEY_LOW_ARMED, false)
                    .apply();
            cancelMonitor(appContext);
            cancelNotification(appContext);
            return;
        }

        requestImmediateCheck(appContext, "enabled");
    }

    public static boolean isPhoneMonitorEnabled(Context context) {
        return context != null && prefs(appContext(context)).getBoolean(KEY_MONITOR_PHONE_BATTERY, true);
    }

    public static void setPhoneMonitorEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        prefs(appContext).edit()
                .putBoolean(KEY_MONITOR_PHONE_BATTERY, enabled)
                .apply();
        if (isEnabled(appContext) && enabled) {
            requestImmediateCheck(appContext, "monitor_enabled");
        } else if (!enabled) {
            cancelMonitor(appContext);
        }
    }

    public static int readHighLimitPercent(Context context) {
        if (context == null) {
            return DEFAULT_HIGH_LIMIT;
        }
        return sanitizeHighLimit(prefs(appContext(context)).getInt(KEY_HIGH_LIMIT_PERCENT, DEFAULT_HIGH_LIMIT), readLowLimitPercent(context));
    }

    public static void setHighLimitPercent(Context context, int percent) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        int low = readLowLimitPercent(appContext);
        prefs(appContext).edit()
                .putInt(KEY_HIGH_LIMIT_PERCENT, sanitizeHighLimit(percent, low))
                .putBoolean(KEY_HIGH_ARMED, true)
                .apply();
        requestImmediateCheck(appContext, "high_limit_changed");
    }

    public static int readLowLimitPercent(Context context) {
        if (context == null) {
            return DEFAULT_LOW_LIMIT;
        }
        return sanitizeLowLimit(prefs(appContext(context)).getInt(KEY_LOW_LIMIT_PERCENT, DEFAULT_LOW_LIMIT), readRawHighLimitPercent(context));
    }

    public static void setLowLimitPercent(Context context, int percent) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        int high = readHighLimitPercent(appContext);
        prefs(appContext).edit()
                .putInt(KEY_LOW_LIMIT_PERCENT, sanitizeLowLimit(percent, high))
                .putBoolean(KEY_LOW_ARMED, true)
                .apply();
        requestImmediateCheck(appContext, "low_limit_changed");
    }

    public static boolean isSoundEnabled(Context context) {
        return context == null || prefs(appContext(context)).getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static boolean isPhoneProtectionEnabled(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        if (prefs.contains(KEY_PHONE_ENABLED)) {
            return prefs.getBoolean(KEY_PHONE_ENABLED, true);
        }
        return isEnabled(appContext);
    }

    public static void setPhoneProtectionEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        prefs(appContext(context)).edit().putBoolean(KEY_PHONE_ENABLED, enabled).apply();
    }

    public static boolean isWatchProtectionEnabled(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        if (prefs.contains(KEY_WATCH_ENABLED)) {
            return prefs.getBoolean(KEY_WATCH_ENABLED, true);
        }
        return isEnabled(appContext);
    }

    public static void setWatchProtectionEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        prefs(appContext(context)).edit().putBoolean(KEY_WATCH_ENABLED, enabled).apply();
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        if (context != null) {
            prefs(appContext(context)).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
        }
    }

    public static boolean isVibrationEnabled(Context context) {
        return context == null || prefs(appContext(context)).getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public static void setVibrationEnabled(Context context, boolean enabled) {
        if (context != null) {
            prefs(appContext(context)).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
        }
    }

    public static boolean isNotificationPermissionGranted(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                appContext(context),
                Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isArmed(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = prefs(appContext(context));
        return prefs.getBoolean(KEY_HIGH_ARMED, true) || prefs.getBoolean(KEY_LOW_ARMED, true);
    }

    public static boolean hasNotified(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = prefs(appContext(context));
        return prefs.getLong(KEY_LAST_HIGH_ALERT_AT, 0L) > 0L || prefs.getLong(KEY_LAST_LOW_ALERT_AT, 0L) > 0L;
    }

    public static long readLastEvalAt(Context context) {
        if (context == null) {
            return 0L;
        }
        return prefs(appContext(context)).getLong(KEY_LAST_EVAL_AT, 0L);
    }

    public static String readLastEvalReason(Context context) {
        if (context == null) {
            return "";
        }
        return prefs(appContext(context)).getString(KEY_LAST_REASON, "");
    }

    public static void evaluateCurrentState(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        rememberEval(prefs, reason);
        Log.i(
                TAG,
                "check_start reason=" + reason
                        + " enabled=" + isEnabled(appContext)
                        + " permission=" + isNotificationPermissionGranted(appContext)
        );
        PhoneBatterySnapshot snapshot = PhoneBatterySnapshot.readCurrent(appContext);
        if (snapshot == null) {
            Log.i(TAG, "check_skip reason=" + reason + " skip=no_snapshot");
            return;
        }
        evaluateSnapshot(appContext, snapshot, reason);
    }

    public static void requestImmediateCheck(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        if (!isEnabled(appContext) || !isPhoneMonitorEnabled(appContext)) {
            cancelMonitor(appContext);
            Log.i(TAG, "queue_check_skip reason=" + reason + " skip=disabled");
            return;
        }
        Log.i(TAG, "queue_check reason=" + reason + " mode=immediate");
        scheduleMonitor(appContext, 0L);
    }

    static void evaluateSnapshot(Context context, PhoneBatterySnapshot snapshot, String reason) {
        if (context == null || snapshot == null) {
            return;
        }
        Context appContext = appContext(context);
        if (!isEnabled(appContext) || !isPhoneMonitorEnabled(appContext)) {
            cancelMonitor(appContext);
            Log.i(TAG, "eval_skip reason=" + reason + " skip=disabled");
            return;
        }

        SharedPreferences prefs = prefs(appContext);
        rememberEval(prefs, reason);
        int highLimit = readHighLimitPercent(appContext);
        int lowLimit = readLowLimitPercent(appContext);
        boolean highArmed = prefs.getBoolean(KEY_HIGH_ARMED, true);
        boolean lowArmed = prefs.getBoolean(KEY_LOW_ARMED, true);
        boolean permissionGranted = isNotificationPermissionGranted(appContext);
        long now = System.currentTimeMillis();
        long lastHighAlertAt = prefs.getLong(KEY_LAST_HIGH_ALERT_AT, 0L);
        long lastLowAlertAt = prefs.getLong(KEY_LAST_LOW_ALERT_AT, 0L);
        boolean highEligible = snapshot.charging
                && snapshot.level >= highLimit
                && highArmed
                && now - lastHighAlertAt >= MIN_REPEAT_MS;
        boolean lowEligible = !snapshot.charging
                && snapshot.level <= lowLimit
                && lowArmed
                && now - lastLowAlertAt >= MIN_REPEAT_MS;

        Log.i(
                TAG,
                "eval reason=" + reason
                        + " level=" + snapshot.level
                        + " charging=" + snapshot.charging
                        + " enabled=true"
                        + " permission=" + permissionGranted
                        + " highLimit=" + highLimit
                        + " lowLimit=" + lowLimit
                        + " highArmed=" + highArmed
                        + " lowArmed=" + lowArmed
                        + " highEligible=" + highEligible
                        + " lowEligible=" + lowEligible
        );

        if (highEligible) {
            if (showNotification(appContext, AlertType.HIGH, snapshot.level, highLimit, reason)) {
                prefs.edit()
                        .putLong(KEY_LAST_HIGH_ALERT_AT, now)
                        .putBoolean(KEY_HIGH_ARMED, false)
                        .apply();
            }
        } else if (!snapshot.charging || snapshot.level <= Math.max(0, highLimit - LIMIT_STEP)) {
            prefs.edit().putBoolean(KEY_HIGH_ARMED, true).apply();
        }

        if (lowEligible) {
            if (showNotification(appContext, AlertType.LOW, snapshot.level, lowLimit, reason)) {
                prefs.edit()
                        .putLong(KEY_LAST_LOW_ALERT_AT, now)
                        .putBoolean(KEY_LOW_ARMED, false)
                        .apply();
            }
        } else if (snapshot.charging || snapshot.level >= Math.min(100, lowLimit + LIMIT_STEP)) {
            prefs.edit().putBoolean(KEY_LOW_ARMED, true).apply();
        }

        scheduleMonitor(appContext, snapshot.charging ? ACTIVE_MONITOR_DELAY_MINUTES : IDLE_MONITOR_DELAY_MINUTES);
    }

    public static boolean showTestNotification(Context context) {
        if (context == null || !isNotificationPermissionGranted(context)) {
            return false;
        }
        Context appContext = appContext(context);
        int highLimit = readHighLimitPercent(appContext);
        return showNotification(appContext, AlertType.HIGH, highLimit, highLimit, "test");
    }

    private static void scheduleMonitor(Context context, long delayMinutes) {
        OneTimeWorkRequest.Builder builder =
                new OneTimeWorkRequest.Builder(PhoneBatteryFullAlertWorker.class);
        if (delayMinutes > 0L) {
            builder.setInitialDelay(delayMinutes, TimeUnit.MINUTES);
        }
        OneTimeWorkRequest request = builder.build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    private static void cancelMonitor(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    private static boolean showNotification(Context context, AlertType type, int level, int limit, String reason) {
        ensureNotificationChannel(context);
        String title = context.getString(R.string.battery_alert_notification_title);
        String text = type == AlertType.HIGH
                ? context.getString(R.string.battery_alert_high_notification_text, limit)
                : context.getString(R.string.battery_alert_low_notification_text, limit);
        boolean permissionGranted = isNotificationPermissionGranted(context);
        Log.i(
                TAG,
                "notify_attempt reason=" + reason
                        + " permission=" + permissionGranted
                        + " channel=" + CHANNEL_ID
                        + " type=" + type
                        + " level=" + level
                        + " title=" + title
                        + " text=" + text
        );
        if (!isNotificationPermissionGranted(context)) {
            Log.i(TAG, "notify_skip reason=" + reason + " skip=permission_missing");
            return false;
        }

        Intent intent = MainActivity.createOpenBatteryIntent(context)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tab_battery)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (!isSoundEnabled(context)) {
            builder.setSilent(true);
        }
        if (isVibrationEnabled(context)) {
            builder.setVibrate(new long[]{0L, 250L, 150L, 250L});
        } else {
            builder.setVibrate(new long[]{0L});
        }

        int notificationId = type == AlertType.HIGH ? HIGH_NOTIFICATION_ID : LOW_NOTIFICATION_ID;
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        Log.i(TAG, "notify_posted reason=" + reason + " channel=" + CHANNEL_ID + " id=" + notificationId);
        return true;
    }

    private static void cancelNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(HIGH_NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(LOW_NOTIFICATION_ID);
    }

    private static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.battery_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.battery_alert_channel_description));
        manager.createNotificationChannel(channel);
    }

    private static int readRawHighLimitPercent(Context context) {
        return prefs(appContext(context)).getInt(KEY_HIGH_LIMIT_PERCENT, DEFAULT_HIGH_LIMIT);
    }

    private static int sanitizeHighLimit(int value, int lowLimit) {
        int clamped = Math.max(50, Math.min(100, value));
        return Math.max(clamped, lowLimit + LIMIT_STEP);
    }

    private static int sanitizeLowLimit(int value, int highLimit) {
        int clamped = Math.max(5, Math.min(50, value));
        return Math.min(clamped, highLimit - LIMIT_STEP);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PhoneBatterySender.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void rememberEval(SharedPreferences prefs, String reason) {
        prefs.edit()
                .putLong(KEY_LAST_EVAL_AT, System.currentTimeMillis())
                .putString(KEY_LAST_REASON, reason != null ? reason : "")
                .apply();
    }

    private static Context appContext(Context context) {
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }

    private enum AlertType {
        HIGH,
        LOW
    }
}
