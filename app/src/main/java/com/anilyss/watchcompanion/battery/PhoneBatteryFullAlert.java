package com.anilyss.watchcompanion.battery;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
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
    public static final String KEY_PHONE_SOUND_ENABLED = "phoneAlertSoundEnabled";
    public static final String KEY_PHONE_VIBRATION_ENABLED = "phoneAlertVibrationEnabled";
    public static final String KEY_WATCH_SOUND_ENABLED = "watchAlertSoundEnabled";
    public static final String KEY_WATCH_VIBRATION_ENABLED = "watchAlertVibrationEnabled";
    public static final String KEY_LIMIT_MODE = "batteryProtectionLimitMode";
    public static final String LIMIT_MODE_PRESET = "preset";
    public static final String LIMIT_MODE_CUSTOM = "custom";
    public static final String KEY_LAST_HIGH_ALERT_AT = "lastHighAlertAt";
    public static final String KEY_LAST_LOW_ALERT_AT = "lastLowAlertAt";
    private static final String LEGACY_KEY_ENABLED = "phone_battery_full_alert_enabled";
    private static final String KEY_HIGH_ARMED = "phone_battery_alert_high_armed";
    private static final String KEY_LOW_ARMED = "phone_battery_alert_low_armed";
    private static final String KEY_LAST_EVAL_AT = "phone_battery_full_alert_last_eval_at";
    private static final String KEY_LAST_REASON = "phone_battery_full_alert_last_reason";
    private static final String IMMEDIATE_WORK_NAME = "phone_battery_full_alert_immediate";
    private static final String PERIODIC_WORK_NAME = "phone_battery_full_alert_periodic_v2";
    private static final String ROLLING_WORK_NAME = "phone_battery_full_alert_rolling_v1";
    private static final String LEGACY_CHANNEL_ID = "phone_battery_alerts";
    private static final String CHANNEL_PREFIX = "battery_alerts";
    private static final String CHANNEL_VERSION_SUFFIX = "_v3";
    private static final int NOTIFICATION_ID = 4100;
    private static final int HIGH_NOTIFICATION_ID = 4101;
    private static final int LOW_NOTIFICATION_ID = 4102;
    private static final int DEFAULT_HIGH_LIMIT = 80;
    private static final int DEFAULT_LOW_LIMIT = 20;
    private static final int LIMIT_STEP = 5;
    private static final long MIN_REPEAT_MS = TimeUnit.HOURS.toMillis(6);
    private static final long PERIODIC_MONITOR_MINUTES = 15L;
    private static final long ROLLING_MONITOR_MINUTES = 2L;

    private PhoneBatteryFullAlert() {
    }

    public enum ChannelHealth {
        READY,
        MISSING,
        BLOCKED,
        SILENT
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        if (prefs.contains(KEY_PHONE_ENABLED) || prefs.contains(KEY_WATCH_ENABLED)) {
            boolean legacyEnabled = readLegacyEnabled(prefs);
            return prefs.getBoolean(KEY_PHONE_ENABLED, legacyEnabled)
                    || prefs.getBoolean(KEY_WATCH_ENABLED, legacyEnabled);
        }
        return readLegacyEnabled(prefs);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_PHONE_ENABLED, enabled)
                .putBoolean(KEY_WATCH_ENABLED, enabled)
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
        if (isPhoneProtectionEnabled(appContext) && enabled) {
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
        return isPhoneSoundEnabled(context);
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
        return readLegacyEnabled(prefs);
    }

    public static void setPhoneProtectionEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        boolean watchEnabled = isWatchProtectionEnabled(appContext);
        prefs.edit()
                .putBoolean(KEY_PHONE_ENABLED, enabled)
                .putBoolean(KEY_ENABLED, enabled || watchEnabled)
                .apply();
        Log.i(TAG, "phone_protection_changed enabled=" + enabled + " watchEnabled=" + watchEnabled);
        if (enabled) {
            ensureMonitoring(appContext, "phone_protection_enabled");
        } else {
            cancelMonitor(appContext);
            cancelPhoneNotifications(appContext);
        }
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
        return readLegacyEnabled(prefs);
    }

    public static void setWatchProtectionEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        boolean phoneEnabled = isPhoneProtectionEnabled(appContext);
        prefs(appContext).edit()
                .putBoolean(KEY_WATCH_ENABLED, enabled)
                .putBoolean(KEY_ENABLED, enabled || phoneEnabled)
                .apply();
        Log.i(TAG, "watch_protection_changed enabled=" + enabled + " phoneEnabled=" + phoneEnabled);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        setPhoneSoundEnabled(context, enabled);
        setWatchSoundEnabled(context, enabled);
    }

    public static boolean isVibrationEnabled(Context context) {
        return isPhoneVibrationEnabled(context);
    }

    public static void setVibrationEnabled(Context context, boolean enabled) {
        setPhoneVibrationEnabled(context, enabled);
        setWatchVibrationEnabled(context, enabled);
    }

    public static boolean isPhoneSoundEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = prefs(appContext(context));
        return prefs.contains(KEY_PHONE_SOUND_ENABLED)
                ? prefs.getBoolean(KEY_PHONE_SOUND_ENABLED, true)
                : prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setPhoneSoundEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(appContext(context)).edit()
                .putBoolean(KEY_PHONE_SOUND_ENABLED, enabled)
                .putBoolean(KEY_SOUND_ENABLED, enabled)
                .apply();
        ensureNotificationChannels(appContext(context));
    }

    public static boolean isPhoneVibrationEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = prefs(appContext(context));
        return prefs.contains(KEY_PHONE_VIBRATION_ENABLED)
                ? prefs.getBoolean(KEY_PHONE_VIBRATION_ENABLED, true)
                : prefs.getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public static void setPhoneVibrationEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(appContext(context)).edit()
                .putBoolean(KEY_PHONE_VIBRATION_ENABLED, enabled)
                .putBoolean(KEY_VIBRATION_ENABLED, enabled)
                .apply();
        ensureNotificationChannels(appContext(context));
    }

    public static boolean isWatchSoundEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = prefs(appContext(context));
        return prefs.contains(KEY_WATCH_SOUND_ENABLED)
                ? prefs.getBoolean(KEY_WATCH_SOUND_ENABLED, true)
                : prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static void setWatchSoundEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(appContext(context)).edit().putBoolean(KEY_WATCH_SOUND_ENABLED, enabled).apply();
    }

    public static boolean isWatchVibrationEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = prefs(appContext(context));
        return prefs.contains(KEY_WATCH_VIBRATION_ENABLED)
                ? prefs.getBoolean(KEY_WATCH_VIBRATION_ENABLED, true)
                : prefs.getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public static void setWatchVibrationEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(appContext(context)).edit().putBoolean(KEY_WATCH_VIBRATION_ENABLED, enabled).apply();
    }

    public static String readLimitMode(Context context) {
        if (context == null) return LIMIT_MODE_PRESET;
        SharedPreferences prefs = prefs(appContext(context));
        if (prefs.contains(KEY_LIMIT_MODE)) {
            return LIMIT_MODE_CUSTOM.equals(prefs.getString(KEY_LIMIT_MODE, LIMIT_MODE_PRESET))
                    ? LIMIT_MODE_CUSTOM
                    : LIMIT_MODE_PRESET;
        }
        int high = readHighLimitPercent(context);
        int low = readLowLimitPercent(context);
        return isPresetHigh(high) && isPresetLow(low) ? LIMIT_MODE_PRESET : LIMIT_MODE_CUSTOM;
    }

    public static void setLimitMode(Context context, String mode) {
        if (context == null) return;
        String safeMode = LIMIT_MODE_CUSTOM.equals(mode) ? LIMIT_MODE_CUSTOM : LIMIT_MODE_PRESET;
        prefs(appContext(context)).edit().putString(KEY_LIMIT_MODE, safeMode).apply();
    }

    public static void ensureMonitoring(Context context, String reason) {
        if (context == null) return;
        Context appContext = appContext(context);
        ensureNotificationChannels(appContext);
        if (!isPhoneProtectionEnabled(appContext) || !isPhoneMonitorEnabled(appContext)) {
            cancelMonitor(appContext);
            Log.i(TAG, "monitor_ensure_skip reason=" + reason + " skip=phone_disabled");
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                PhoneBatteryFullAlertWorker.class,
                PERIODIC_MONITOR_MINUTES,
                TimeUnit.MINUTES
        ).build();
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
        Log.i(TAG, "monitor_ensured reason=" + reason + " intervalMinutes=" + PERIODIC_MONITOR_MINUTES);
        scheduleRollingMonitor(appContext, reason);
        requestImmediateCheck(appContext, reason);
    }

    public static boolean isNotificationPermissionGranted(Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = appContext(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled();
    }

    public static String getActiveChannelId(Context context) {
        if (context == null) {
            return channelId(true, true);
        }
        Context appContext = appContext(context);
        return channelId(
                isPhoneSoundEnabled(appContext),
                isPhoneVibrationEnabled(appContext)
        );
    }

    public static ChannelHealth readActiveChannelHealth(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return context == null ? ChannelHealth.MISSING : ChannelHealth.READY;
        }
        Context appContext = appContext(context);
        ensureNotificationChannels(appContext);
        NotificationManager manager = appContext.getSystemService(NotificationManager.class);
        String channelId = getActiveChannelId(appContext);
        NotificationChannel channel = manager != null
                ? manager.getNotificationChannel(channelId)
                : null;
        ChannelHealth health;
        if (channel == null) {
            health = ChannelHealth.MISSING;
        } else if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            health = ChannelHealth.BLOCKED;
        } else if (isPhoneSoundEnabled(appContext)
                && (channel.getSound() == null
                || channel.getImportance() < NotificationManager.IMPORTANCE_DEFAULT)) {
            health = ChannelHealth.SILENT;
        } else {
            health = ChannelHealth.READY;
        }
        Log.i(
                TAG,
                "channel_health channel=" + channelId
                        + " health=" + health
                        + " sound=" + isPhoneSoundEnabled(appContext)
                        + " vibration=" + isPhoneVibrationEnabled(appContext)
                        + " soundUri=" + (channel != null ? channel.getSound() : null)
                        + " expectedSoundUri=" + (isPhoneSoundEnabled(appContext) ? resolveAlertSoundUri(appContext) : "null")
                        + " importance=" + (channel != null ? channel.getImportance() : -1)
                        + " hasVibrator=" + hasVibrator(appContext)
                        + " canVibrate=" + canVibrate(appContext)
                        + " permission=" + isNotificationPermissionGranted(appContext)
        );
        return health;
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
        if (!isPhoneProtectionEnabled(appContext) || !isPhoneMonitorEnabled(appContext)) {
            Log.i(TAG, "queue_check_skip reason=" + reason + " skip=disabled");
            return;
        }
        Log.i(TAG, "queue_check reason=" + reason + " mode=immediate");
        scheduleImmediateCheck(appContext);
    }

    static synchronized void evaluateSnapshot(Context context, PhoneBatterySnapshot snapshot, String reason) {
        if (context == null || snapshot == null) {
            return;
        }
        Context appContext = appContext(context);
        if (!isPhoneProtectionEnabled(appContext) || !isPhoneMonitorEnabled(appContext)) {
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
        Log.i(
                TAG,
                "decision reason=" + reason
                        + " crossedHigh=" + (snapshot.level >= highLimit)
                        + " crossedLow=" + (snapshot.level <= lowLimit)
                        + " notifyHigh=" + highEligible
                        + " notifyLow=" + lowEligible
                        + " cooldownHighMs=" + Math.max(0L, MIN_REPEAT_MS - (now - lastHighAlertAt))
                        + " cooldownLowMs=" + Math.max(0L, MIN_REPEAT_MS - (now - lastLowAlertAt))
        );

        if (highEligible) {
            Log.i(TAG, "threshold_crossed reason=" + reason + " type=HIGH level=" + snapshot.level + " limit=" + highLimit);
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
            Log.i(TAG, "threshold_crossed reason=" + reason + " type=LOW level=" + snapshot.level + " limit=" + lowLimit);
            if (showNotification(appContext, AlertType.LOW, snapshot.level, lowLimit, reason)) {
                prefs.edit()
                        .putLong(KEY_LAST_LOW_ALERT_AT, now)
                        .putBoolean(KEY_LOW_ARMED, false)
                        .apply();
            }
        } else if (snapshot.charging || snapshot.level >= Math.min(100, lowLimit + LIMIT_STEP)) {
            prefs.edit().putBoolean(KEY_LOW_ARMED, true).apply();
        }

    }

    public static boolean showTestNotification(Context context) {
        if (context == null || !isNotificationPermissionGranted(context)) {
            return false;
        }
        Context appContext = appContext(context);
        int highLimit = readHighLimitPercent(appContext);
        return showNotification(appContext, AlertType.HIGH, highLimit, highLimit, "test");
    }

    public static boolean simulateAlert(Context context, String target, Boolean soundOverride, Boolean vibrationOverride) {
        if (context == null) {
            return false;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        boolean originalHighArmed = prefs.getBoolean(KEY_HIGH_ARMED, true);
        boolean originalLowArmed = prefs.getBoolean(KEY_LOW_ARMED, true);
        long originalLastHigh = prefs.getLong(KEY_LAST_HIGH_ALERT_AT, 0L);
        long originalLastLow = prefs.getLong(KEY_LAST_LOW_ALERT_AT, 0L);
        boolean originalSoundEnabled = isPhoneSoundEnabled(appContext);
        boolean originalVibrationEnabled = isPhoneVibrationEnabled(appContext);
        try {
            if (soundOverride != null) {
                setPhoneSoundEnabled(appContext, soundOverride);
            }
            if (vibrationOverride != null) {
                setPhoneVibrationEnabled(appContext, vibrationOverride);
            }
            if ("low".equalsIgnoreCase(target)) {
                int lowLimit = readLowLimitPercent(appContext);
                prefs.edit()
                        .putBoolean(KEY_LOW_ARMED, true)
                        .putLong(KEY_LAST_LOW_ALERT_AT, 0L)
                        .apply();
                Log.i(TAG, "simulate_start target=LOW level=" + lowLimit
                        + " sound=" + isPhoneSoundEnabled(appContext)
                        + " vibration=" + isPhoneVibrationEnabled(appContext));
                evaluateSnapshot(appContext, new PhoneBatterySnapshot(lowLimit, false), "simulate_low");
                return true;
            }
            int highLimit = readHighLimitPercent(appContext);
            prefs.edit()
                    .putBoolean(KEY_HIGH_ARMED, true)
                    .putLong(KEY_LAST_HIGH_ALERT_AT, 0L)
                    .apply();
            Log.i(TAG, "simulate_start target=HIGH level=" + highLimit
                    + " sound=" + isPhoneSoundEnabled(appContext)
                    + " vibration=" + isPhoneVibrationEnabled(appContext));
            evaluateSnapshot(appContext, new PhoneBatterySnapshot(highLimit, true), "simulate_high");
            return true;
        } finally {
            prefs.edit()
                    .putBoolean(KEY_HIGH_ARMED, originalHighArmed)
                    .putBoolean(KEY_LOW_ARMED, originalLowArmed)
                    .putLong(KEY_LAST_HIGH_ALERT_AT, originalLastHigh)
                    .putLong(KEY_LAST_LOW_ALERT_AT, originalLastLow)
                    .putBoolean(KEY_SOUND_ENABLED, originalSoundEnabled)
                    .putBoolean(KEY_VIBRATION_ENABLED, originalVibrationEnabled)
                    .putBoolean(KEY_PHONE_SOUND_ENABLED, originalSoundEnabled)
                    .putBoolean(KEY_PHONE_VIBRATION_ENABLED, originalVibrationEnabled)
                    .apply();
            ensureNotificationChannels(appContext);
            Log.i(TAG, "simulate_restore target=" + target);
        }
    }

    public static void clearPostedNotifications(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        cancelNotification(appContext);
        NotificationManagerCompat.from(appContext).cancelAll();
        Log.i(TAG, "notify_cleared source=debug");
    }

    private static void scheduleImmediateCheck(Context context) {
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(PhoneBatteryFullAlertWorker.class).build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    static void scheduleRollingMonitor(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        if (!isPhoneProtectionEnabled(appContext) || !isPhoneMonitorEnabled(appContext)) {
            Log.i(TAG, "rolling_skip reason=" + reason + " skip=disabled");
            return;
        }
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(PhoneBatteryFullAlertWorker.class)
                        .setInitialDelay(ROLLING_MONITOR_MINUTES, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(appContext).enqueueUniqueWork(
                ROLLING_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
        Log.i(TAG, "rolling_scheduled reason=" + reason + " intervalMinutes=" + ROLLING_MONITOR_MINUTES);
    }

    private static void cancelMonitor(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(IMMEDIATE_WORK_NAME);
        manager.cancelUniqueWork(PERIODIC_WORK_NAME);
        manager.cancelUniqueWork(ROLLING_WORK_NAME);
    }

    private static boolean showNotification(Context context, AlertType type, int level, int limit, String reason) {
        ensureNotificationChannels(context);
        boolean soundEnabled = isPhoneSoundEnabled(context);
        boolean vibrationEnabled = isPhoneVibrationEnabled(context);
        String channelId = getActiveChannelId(context);
        String title = context.getString(R.string.battery_alert_notification_title);
        String text = type == AlertType.HIGH
                ? context.getString(R.string.battery_alert_high_notification_text, limit)
                : context.getString(R.string.battery_alert_low_notification_text, limit);
        boolean permissionGranted = isNotificationPermissionGranted(context);
        Log.i(
                TAG,
                "notify_attempt reason=" + reason
                        + " permission=" + permissionGranted
                        + " channel=" + channelId
                        + " type=" + type
                        + " level=" + level
                        + " sound=" + soundEnabled
                        + " vibration=" + vibrationEnabled
                        + " soundUri=" + (soundEnabled ? resolveAlertSoundUri(context) : "null")
                        + " importance=" + readChannelImportance(context, channelId)
                        + " hasVibrator=" + hasVibrator(context)
                        + " canVibrate=" + canVibrate(context)
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

        ChannelHealth channelHealth = readActiveChannelHealth(context);
        if (channelHealth == ChannelHealth.MISSING || channelHealth == ChannelHealth.BLOCKED) {
            Log.w(TAG, "notify_skip reason=" + reason + " skip=channel_" + channelHealth
                    + " channel=" + channelId);
            return false;
        }
        if (channelHealth == ChannelHealth.SILENT) {
            Log.w(TAG, "notify_channel_warning reason=" + reason + " warning=silent channel=" + channelId);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_tab_battery)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        if (soundEnabled) {
            builder.setSound(resolveAlertSoundUri(context));
        } else {
            builder.setSilent(true);
            builder.setSound(null);
        }
        if (vibrationEnabled) {
            builder.setVibrate(new long[]{0L, 250L, 150L, 250L});
        } else {
            builder.setVibrate(new long[]{0L});
        }

        int notificationId = type == AlertType.HIGH ? HIGH_NOTIFICATION_ID : LOW_NOTIFICATION_ID;
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
            Log.i(TAG, "notify_posted reason=" + reason + " channel=" + channelId + " id=" + notificationId);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "notify_failed reason=" + reason + " channel=" + channelId, e);
            return false;
        }
    }

    private static void cancelNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(HIGH_NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(LOW_NOTIFICATION_ID);
    }

    public static void ensureNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.w(TAG, "channel_create_failed reason=no_notification_manager");
            return;
        }
        createChannel(context, manager, true, true);
        createChannel(context, manager, true, false);
        createChannel(context, manager, false, true);
        createChannel(context, manager, false, false);
        NotificationChannel legacy = manager.getNotificationChannel(LEGACY_CHANNEL_ID);
        if (legacy != null) {
            Log.i(TAG, "legacy_channel_detected id=" + LEGACY_CHANNEL_ID
                    + " importance=" + legacy.getImportance());
        }
    }

    private static void createChannel(
            Context context,
            NotificationManager manager,
            boolean sound,
            boolean vibration
    ) {
        String id = channelId(sound, vibration);
        if (manager.getNotificationChannel(id) != null) return;
        NotificationChannel channel = new NotificationChannel(
                id,
                context.getString(R.string.battery_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.battery_alert_channel_description));
        channel.enableVibration(vibration);
        channel.setVibrationPattern(vibration ? new long[]{0L, 250L, 150L, 250L} : null);
        if (sound) {
            channel.setSound(
                    resolveAlertSoundUri(context),
                    buildAudioAttributes()
            );
        } else {
            channel.setSound(null, null);
        }
        manager.createNotificationChannel(channel);
        Log.i(TAG, "channel_created id=" + id
                + " sound=" + sound
                + " vibration=" + vibration
                + " soundUri=" + (sound ? resolveAlertSoundUri(context) : "null")
                + " importance=" + channel.getImportance()
                + " hasVibrator=" + hasVibrator(context)
                + " canVibrate=" + canVibrate(context));
    }

    private static String channelId(boolean sound, boolean vibration) {
        return CHANNEL_PREFIX + "_s" + (sound ? "1" : "0") + "_v" + (vibration ? "1" : "0") + CHANNEL_VERSION_SUFFIX;
    }

    private static Uri resolveAlertSoundUri(Context context) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.anilys_battery_alert);
    }

    private static AudioAttributes buildAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private static int readChannelImportance(Context context, String channelId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return NotificationManager.IMPORTANCE_UNSPECIFIED;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager != null ? manager.getNotificationChannel(channelId) : null;
        return channel != null ? channel.getImportance() : -1;
    }

    private static boolean hasVibrator(Context context) {
        Vibrator vibrator = getVibrator(context);
        return vibrator != null && vibrator.hasVibrator();
    }

    private static boolean canVibrate(Context context) {
        Vibrator vibrator = getVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int[] support = vibrator.areEffectsSupported(VibrationEffect.EFFECT_CLICK);
            return vibrator.hasAmplitudeControl()
                    || (support.length > 0 && support[0] != Vibrator.VIBRATION_EFFECT_SUPPORT_NO);
        }
        return true;
    }

    private static Vibrator getVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = context.getSystemService(VibratorManager.class);
            return vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static void cancelPhoneNotifications(Context context) {
        NotificationManagerCompat.from(context).cancel(HIGH_NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(LOW_NOTIFICATION_ID);
    }

    private static int readRawHighLimitPercent(Context context) {
        return prefs(appContext(context)).getInt(KEY_HIGH_LIMIT_PERCENT, DEFAULT_HIGH_LIMIT);
    }

    private static int sanitizeHighLimit(int value, int lowLimit) {
        int clamped = Math.max(30, Math.min(100, value));
        return Math.max(clamped, lowLimit + LIMIT_STEP);
    }

    private static int sanitizeLowLimit(int value, int highLimit) {
        int clamped = Math.max(1, Math.min(70, value));
        return Math.min(clamped, highLimit - LIMIT_STEP);
    }

    private static boolean isPresetHigh(int value) {
        return value == 80 || value == 85 || value == 90;
    }

    private static boolean isPresetLow(int value) {
        return value == 15 || value == 20 || value == 25;
    }

    private static boolean readLegacyEnabled(SharedPreferences prefs) {
        if (prefs.contains(KEY_ENABLED)) {
            return prefs.getBoolean(KEY_ENABLED, false);
        }
        return prefs.getBoolean(LEGACY_KEY_ENABLED, false);
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
