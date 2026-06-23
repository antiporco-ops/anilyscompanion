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
    public static final String KEY_MONITOR_WATCH_BATTERY = "monitorWatchBattery";
    public static final String KEY_HIGH_LIMIT_PERCENT = "highBatteryLimitPercent";
    public static final String KEY_LOW_LIMIT_PERCENT = "lowBatteryLimitPercent";
    public static final String KEY_ALERT_PHONE_ON_PHONE = "alertPhoneOnPhone";
    public static final String KEY_ALERT_PHONE_ON_WATCH = "alertPhoneOnWatch";
    public static final String KEY_ALERT_WATCH_ON_PHONE = "alertWatchOnPhone";
    public static final String KEY_ALERT_WATCH_ON_WATCH = "alertWatchOnWatch";
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
    private static final String TAG_PHONE_SOURCE = "phone_source";
    private static final String TAG_WATCH_SOURCE = "watch_source";
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

    public static final class ProtectionState {
        public final boolean monitorPhoneEnabled;
        public final boolean monitorWatchEnabled;
        public final boolean alertPhoneOnPhoneEnabled;
        public final boolean alertPhoneOnWatchEnabled;
        public final boolean alertWatchOnPhoneEnabled;
        public final boolean alertWatchOnWatchEnabled;
        public final boolean alertsEnabled;
        public final boolean migrated;
        public final String source;

        ProtectionState(
                boolean monitorPhoneEnabled,
                boolean monitorWatchEnabled,
                boolean alertPhoneOnPhoneEnabled,
                boolean alertPhoneOnWatchEnabled,
                boolean alertWatchOnPhoneEnabled,
                boolean alertWatchOnWatchEnabled,
                boolean alertsEnabled,
                boolean migrated,
                String source
        ) {
            this.monitorPhoneEnabled = monitorPhoneEnabled;
            this.monitorWatchEnabled = monitorWatchEnabled;
            this.alertPhoneOnPhoneEnabled = alertPhoneOnPhoneEnabled;
            this.alertPhoneOnWatchEnabled = alertPhoneOnWatchEnabled;
            this.alertWatchOnPhoneEnabled = alertWatchOnPhoneEnabled;
            this.alertWatchOnWatchEnabled = alertWatchOnWatchEnabled;
            this.alertsEnabled = alertsEnabled;
            this.migrated = migrated;
            this.source = source;
        }
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return readProtectionState(appContext(context), "is_enabled", false).alertsEnabled;
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        prefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_MONITOR_PHONE_BATTERY, enabled)
                .putBoolean(KEY_MONITOR_WATCH_BATTERY, enabled)
                .putBoolean(KEY_ALERT_PHONE_ON_PHONE, enabled)
                .putBoolean(KEY_ALERT_PHONE_ON_WATCH, enabled)
                .putBoolean(KEY_ALERT_WATCH_ON_PHONE, enabled)
                .putBoolean(KEY_ALERT_WATCH_ON_WATCH, enabled)
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

        resetAlertWindow(appContext, "set_enabled");
        requestImmediateCheck(appContext, "enabled");
    }

    public static boolean isPhoneMonitorEnabled(Context context) {
        return context != null && readProtectionState(appContext(context), "read_monitor_phone", false).monitorPhoneEnabled;
    }

    public static void setPhoneMonitorEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        Log.i(TAG, "ui_control_changed reason=ui_monitor_changed control=phone enabled=" + enabled);
        prefs(appContext).edit()
                .putBoolean(KEY_MONITOR_PHONE_BATTERY, enabled)
                .apply();
        if (enabled) {
            resetAlertWindow(appContext, "ui_monitor_changed:phone");
            ensureMonitoring(appContext, "ui_monitor_changed:phone");
        } else if (!enabled) {
            cancelMonitor(appContext);
        }
    }

    public static boolean isWatchMonitorEnabled(Context context) {
        return context != null && readProtectionState(appContext(context), "read_monitor_watch", false).monitorWatchEnabled;
    }

    public static void setWatchMonitorEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        Log.i(TAG, "ui_control_changed reason=ui_monitor_changed control=watch enabled=" + enabled);
        prefs(appContext(context)).edit()
                .putBoolean(KEY_MONITOR_WATCH_BATTERY, enabled)
                .apply();
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
        int sanitized = sanitizeHighLimit(percent, low);
        Log.i(TAG, "ui_control_changed reason=ui_limit_changed control=high value=" + sanitized);
        prefs(appContext).edit()
                .putInt(KEY_HIGH_LIMIT_PERCENT, sanitized)
                .apply();
        resetAlertWindow(appContext, "ui_limit_changed:high");
        requestImmediateCheck(appContext, "ui_limit_changed:high");
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
        int sanitized = sanitizeLowLimit(percent, high);
        Log.i(TAG, "ui_control_changed reason=ui_limit_changed control=low value=" + sanitized);
        prefs(appContext).edit()
                .putInt(KEY_LOW_LIMIT_PERCENT, sanitized)
                .apply();
        resetAlertWindow(appContext, "ui_limit_changed:low");
        requestImmediateCheck(appContext, "ui_limit_changed:low");
    }

    public static boolean isSoundEnabled(Context context) {
        return isPhoneSoundEnabled(context);
    }

    public static boolean isPhoneProtectionEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return isAlertPhoneOnPhoneEnabled(context);
    }

    public static void setPhoneProtectionEnabled(Context context, boolean enabled) {
        setAlertPhoneOnPhoneEnabled(context, enabled);
    }

    public static boolean isWatchProtectionEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return isAlertWatchOnWatchEnabled(context);
    }

    public static void setWatchProtectionEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        setAlertWatchOnWatchEnabled(context, enabled);
    }

    public static boolean isAlertPhoneOnPhoneEnabled(Context context) {
        return context != null && readProtectionState(appContext(context), "read_phone_on_phone", false).alertPhoneOnPhoneEnabled;
    }

    public static void setAlertPhoneOnPhoneEnabled(Context context, boolean enabled) {
        setAlertFlag(context, KEY_ALERT_PHONE_ON_PHONE, enabled, "phone_on_phone");
    }

    public static boolean isAlertPhoneOnWatchEnabled(Context context) {
        return context != null && readProtectionState(appContext(context), "read_phone_on_watch", false).alertPhoneOnWatchEnabled;
    }

    public static void setAlertPhoneOnWatchEnabled(Context context, boolean enabled) {
        setAlertFlag(context, KEY_ALERT_PHONE_ON_WATCH, enabled, "phone_on_watch");
    }

    public static boolean isAlertWatchOnPhoneEnabled(Context context) {
        return context != null && readProtectionState(appContext(context), "read_watch_on_phone", false).alertWatchOnPhoneEnabled;
    }

    public static void setAlertWatchOnPhoneEnabled(Context context, boolean enabled) {
        setAlertFlag(context, KEY_ALERT_WATCH_ON_PHONE, enabled, "watch_on_phone");
    }

    public static boolean isAlertWatchOnWatchEnabled(Context context) {
        return context != null && readProtectionState(appContext(context), "read_watch_on_watch", false).alertWatchOnWatchEnabled;
    }

    public static void setAlertWatchOnWatchEnabled(Context context, boolean enabled) {
        setAlertFlag(context, KEY_ALERT_WATCH_ON_WATCH, enabled, "watch_on_watch");
    }

    private static void setAlertFlag(Context context, String key, boolean enabled, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        Log.i(TAG, "ui_control_changed reason=ui_destination_changed control=" + reason + " enabled=" + enabled);
        prefs(appContext).edit()
                .putBoolean(key, enabled)
                .putBoolean(KEY_ENABLED, readProtectionState(appContext, "set_alert_flag:" + reason, false).alertsEnabled || enabled)
                .apply();
        if (enabled) {
            resetAlertWindow(appContext, "ui_destination_changed:" + reason);
            ensureMonitoring(appContext, "ui_destination_changed:" + reason);
        }
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
        ProtectionState state = normalizeStoredState(appContext, "ensure_monitoring:" + reason);
        ensureNotificationChannels(appContext);
        if (!state.monitorPhoneEnabled) {
            cancelMonitor(appContext);
            Log.i(TAG, "monitor_ensure_skip reason=" + reason + " skip=phone_monitor_disabled");
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
        ProtectionState state = normalizeStoredState(appContext, "evaluate_current:" + reason);
        SharedPreferences prefs = prefs(appContext);
        rememberEval(prefs, reason);
        Log.i(
                TAG,
                "check_start reason=" + reason
                        + " enabled=" + state.alertsEnabled
                        + " monitorPhone=" + state.monitorPhoneEnabled
                        + " monitorWatch=" + state.monitorWatchEnabled
                        + " alertPhoneOnPhone=" + state.alertPhoneOnPhoneEnabled
                        + " alertPhoneOnWatch=" + state.alertPhoneOnWatchEnabled
                        + " alertWatchOnPhone=" + state.alertWatchOnPhoneEnabled
                        + " alertWatchOnWatch=" + state.alertWatchOnWatchEnabled
                        + " permission=" + isNotificationPermissionGranted(appContext)
        );
        PhoneBatterySnapshot snapshot = PhoneBatterySnapshot.readCurrent(appContext);
        if (snapshot == null) {
            Log.i(TAG, "battery_snapshot reason=" + reason + " state=missing");
            Log.i(TAG, "check_skip reason=" + reason + " skip=no_snapshot");
            return;
        }
        Log.i(TAG, "battery_snapshot reason=" + reason
                + " level=" + snapshot.level
                + " charging=" + snapshot.charging
                + " source=sticky");
        evaluateSnapshot(appContext, snapshot, reason);
    }

    public static void requestImmediateCheck(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        ProtectionState state = normalizeStoredState(appContext, "queue_check:" + reason);
        if (!state.monitorPhoneEnabled) {
            Log.i(TAG, "requestImmediateCheck immediate_check_requested reason=" + reason
                    + " accepted=false skip=phone_monitor_disabled");
            return;
        }
        Log.i(TAG, "requestImmediateCheck immediate_check_requested reason=" + reason
                + " accepted=true mode=immediate");
        scheduleImmediateCheck(appContext, reason);
    }

    public static void rearmForUiTest(Context context, String reason) {
        if (context == null) {
            return;
        }
        Context appContext = appContext(context);
        Log.i(TAG, "ui_control_changed reason=" + reason + " action=rearm_and_evaluate");
        resetAlertWindow(appContext, reason);
        ensureMonitoring(appContext, reason);
    }

    static synchronized void evaluateSnapshot(Context context, PhoneBatterySnapshot snapshot, String reason) {
        if (context == null || snapshot == null) {
            return;
        }
        Context appContext = appContext(context);
        ProtectionState state = normalizeStoredState(appContext, "evaluate_snapshot:" + reason);
        if (!state.monitorPhoneEnabled) {
            cancelMonitor(appContext);
            Log.i(TAG, "eval_skip reason=" + reason + " skip=phone_monitor_disabled");
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
        boolean bypassCooldown = isCooldownBypassed(reason);
        boolean highCooldownElapsed = now - lastHighAlertAt >= MIN_REPEAT_MS;
        boolean lowCooldownElapsed = now - lastLowAlertAt >= MIN_REPEAT_MS;
        long cooldownHighMs = Math.max(0L, MIN_REPEAT_MS - (now - lastHighAlertAt));
        long cooldownLowMs = Math.max(0L, MIN_REPEAT_MS - (now - lastLowAlertAt));
        boolean highEligible = snapshot.charging
                && snapshot.level >= highLimit
                && highArmed
                && (bypassCooldown || highCooldownElapsed);
        boolean lowEligible = !snapshot.charging
                && snapshot.level <= lowLimit
                && lowArmed
                && (bypassCooldown || lowCooldownElapsed);
        String highBlocked = highEligible
                ? "none"
                : snapshot.charging
                ? snapshot.level < highLimit
                ? "below_limit"
                : !highArmed
                ? "not_armed"
                : !highCooldownElapsed && !bypassCooldown
                ? "cooldown"
                : "not_eligible"
                : "not_charging";
        String lowBlocked = lowEligible
                ? "none"
                : !snapshot.charging
                ? snapshot.level > lowLimit
                ? "above_limit"
                : !lowArmed
                ? "not_armed"
                : !lowCooldownElapsed && !bypassCooldown
                ? "cooldown"
                : "not_eligible"
                : "charging";

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
                        + " bypassCooldown=" + bypassCooldown
                        + " lastHighAt=" + lastHighAlertAt
                        + " lastLowAt=" + lastLowAlertAt
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
                        + " cooldownHighMs=" + cooldownHighMs
                        + " cooldownLowMs=" + cooldownLowMs
                        + " highBlocked=" + highBlocked
                        + " lowBlocked=" + lowBlocked
        );

        if (highEligible) {
            Log.i(TAG, "threshold_crossed reason=" + reason + " type=HIGH level=" + snapshot.level + " limit=" + highLimit);
            boolean postedLocal = state.alertPhoneOnPhoneEnabled
                    && showNotification(appContext, AlertType.HIGH, AlertSource.PHONE, snapshot.level, highLimit, reason);
            boolean postedRemote = state.alertPhoneOnWatchEnabled
                    && BatteryAlertEventBridge.sendToWatch(
                    appContext,
                    BatteryAlertEventBridge.AlertEvent.create(
                            BatteryAlertEventBridge.SOURCE_PHONE,
                            BatteryAlertEventBridge.TYPE_HIGH,
                            snapshot.level,
                            highLimit,
                            true,
                            now
                    )
            );
            Log.i(TAG, "dispatch_result reason=" + reason
                    + " type=HIGH local=" + postedLocal
                    + " remote=" + postedRemote);
            if (postedLocal || postedRemote) {
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
            boolean postedLocal = state.alertPhoneOnPhoneEnabled
                    && showNotification(appContext, AlertType.LOW, AlertSource.PHONE, snapshot.level, lowLimit, reason);
            boolean postedRemote = state.alertPhoneOnWatchEnabled
                    && BatteryAlertEventBridge.sendToWatch(
                    appContext,
                    BatteryAlertEventBridge.AlertEvent.create(
                            BatteryAlertEventBridge.SOURCE_PHONE,
                            BatteryAlertEventBridge.TYPE_LOW,
                            snapshot.level,
                            lowLimit,
                            false,
                            now
                    )
            );
            Log.i(TAG, "dispatch_result reason=" + reason
                    + " type=LOW local=" + postedLocal
                    + " remote=" + postedRemote);
            if (postedLocal || postedRemote) {
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
        return showNotification(appContext, AlertType.HIGH, AlertSource.PHONE, highLimit, highLimit, "test");
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

    private static void scheduleImmediateCheck(Context context, String reason) {
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(PhoneBatteryFullAlertWorker.class)
                        .setInputData(new androidx.work.Data.Builder()
                                .putString("reason", "worker:" + (reason != null ? reason : "immediate"))
                                .build())
                        .build();
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
        ProtectionState state = readProtectionState(appContext, "rolling:" + reason, false);
        if (!state.monitorPhoneEnabled) {
            Log.i(TAG, "rolling_skip reason=" + reason + " skip=phone_monitor_disabled");
            return;
        }
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(PhoneBatteryFullAlertWorker.class)
                        .setInputData(new androidx.work.Data.Builder()
                                .putString("reason", "rolling_worker:" + (reason != null ? reason : "unknown"))
                                .build())
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

    static void postRemoteAlert(Context context, BatteryAlertEventBridge.AlertEvent event) {
        if (context == null || event == null) {
            return;
        }
        ProtectionState state = readProtectionState(appContext(context), "remote_alert:" + event.source + ":" + event.type, false);
        if (!state.alertWatchOnPhoneEnabled) {
            Log.i(TAG, "remote_event_skip reason=watch_on_phone_disabled eventId=" + event.eventId);
            return;
        }
        AlertType type = BatteryAlertEventBridge.TYPE_HIGH.equals(event.type) ? AlertType.HIGH : AlertType.LOW;
        boolean posted = showNotification(
                appContext(context),
                type,
                AlertSource.WATCH,
                event.level,
                event.limit,
                "remote_event:" + event.eventId
        );
        Log.i(TAG, "remote_event_result source=" + event.source
                + " type=" + event.type
                + " eventId=" + event.eventId
                + " posted=" + posted);
    }

    private static boolean showNotification(Context context, AlertType type, AlertSource source, int level, int limit, String reason) {
        ensureNotificationChannels(context);
        boolean soundEnabled = isPhoneSoundEnabled(context);
        boolean vibrationEnabled = isPhoneVibrationEnabled(context);
        String channelId = getActiveChannelId(context);
        boolean bypassCooldown = isCooldownBypassed(reason);
        String title = context.getString(R.string.battery_alert_notification_title);
        String text = resolveNotificationText(context, type, source, limit);
        String notificationTag = notificationTagFor(source);
        boolean permissionGranted = isNotificationPermissionGranted(context);
        Log.i(
                TAG,
                "notify_attempt reason=" + reason
                        + " permission=" + permissionGranted
                        + " channel=" + channelId
                        + " type=" + type
                        + " source=" + source
                        + " tag=" + notificationTag
                        + " level=" + level
                        + " sound=" + soundEnabled
                        + " vibration=" + vibrationEnabled
                        + " soundUri=" + (soundEnabled ? resolveAlertSoundUri(context) : "null")
                        + " importance=" + readChannelImportance(context, channelId)
                        + " hasVibrator=" + hasVibrator(context)
                        + " canVibrate=" + canVibrate(context)
                        + " bypassCooldown=" + bypassCooldown
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
                .setLocalOnly(true)
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
            if (bypassCooldown) {
                NotificationManagerCompat.from(context).cancel(notificationTag, notificationId);
                Log.i(TAG, "notify_replace reason=" + reason + " id=" + notificationId
                        + " tag=" + notificationTag
                        + " bypassCooldown=true");
            }
            NotificationManagerCompat.from(context).notify(notificationTag, notificationId, builder.build());
            Log.i(TAG, "notify_posted reason=" + reason
                    + " channel=" + channelId
                    + " id=" + notificationId
                    + " tag=" + notificationTag
                    + " source=" + source);
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
        NotificationManagerCompat.from(context).cancel(TAG_PHONE_SOURCE, HIGH_NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(TAG_PHONE_SOURCE, LOW_NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(TAG_WATCH_SOURCE, HIGH_NOTIFICATION_ID);
        NotificationManagerCompat.from(context).cancel(TAG_WATCH_SOURCE, LOW_NOTIFICATION_ID);
    }

    private static void resetAlertWindow(Context context, String reason) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putBoolean(KEY_HIGH_ARMED, true)
                .putBoolean(KEY_LOW_ARMED, true)
                .putLong(KEY_LAST_HIGH_ALERT_AT, 0L)
                .putLong(KEY_LAST_LOW_ALERT_AT, 0L)
                .apply();
        Log.i(TAG, "cooldown_reset reason=" + reason + " lastHighAt=0 lastLowAt=0 highArmed=true lowArmed=true");
    }

    static void resetDebugAlertWindow(Context context, String reason) {
        if (context == null) {
            return;
        }
        resetAlertWindow(appContext(context), reason);
    }

    private static boolean isCooldownBypassed(String reason) {
        if (reason == null) {
            return false;
        }
        return "test".equals(reason)
                || reason.startsWith("simulate_")
                || reason.startsWith("debug_");
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

    public static ProtectionState normalizeStoredState(Context context, String reason) {
        return readProtectionState(context, reason, true);
    }

    public static ProtectionState readCurrentState(Context context, String reason) {
        return readProtectionState(context, reason, false);
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

    private static ProtectionState readProtectionState(Context context, String reason, boolean persistFixes) {
        Context appContext = appContext(context);
        SharedPreferences prefs = prefs(appContext);
        boolean legacyEnabled = readLegacyEnabled(prefs);
        boolean hasLegacyPhoneTarget = prefs.contains(KEY_PHONE_ENABLED);
        boolean hasLegacyWatchTarget = prefs.contains(KEY_WATCH_ENABLED);
        boolean hasMonitorPhone = prefs.contains(KEY_MONITOR_PHONE_BATTERY);
        boolean hasMonitorWatch = prefs.contains(KEY_MONITOR_WATCH_BATTERY);
        boolean hasAlertPhoneOnPhone = prefs.contains(KEY_ALERT_PHONE_ON_PHONE);
        boolean hasAlertPhoneOnWatch = prefs.contains(KEY_ALERT_PHONE_ON_WATCH);
        boolean hasAlertWatchOnPhone = prefs.contains(KEY_ALERT_WATCH_ON_PHONE);
        boolean hasAlertWatchOnWatch = prefs.contains(KEY_ALERT_WATCH_ON_WATCH);
        boolean legacyPhoneTarget = hasLegacyPhoneTarget ? prefs.getBoolean(KEY_PHONE_ENABLED, legacyEnabled) : legacyEnabled;
        boolean legacyWatchTarget = hasLegacyWatchTarget ? prefs.getBoolean(KEY_WATCH_ENABLED, legacyEnabled) : legacyEnabled;
        boolean monitorPhoneEnabled = hasMonitorPhone ? prefs.getBoolean(KEY_MONITOR_PHONE_BATTERY, legacyPhoneTarget) : legacyPhoneTarget;
        boolean monitorWatchEnabled = hasMonitorWatch ? prefs.getBoolean(KEY_MONITOR_WATCH_BATTERY, legacyWatchTarget) : legacyWatchTarget;
        boolean alertPhoneOnPhoneEnabled = hasAlertPhoneOnPhone ? prefs.getBoolean(KEY_ALERT_PHONE_ON_PHONE, legacyPhoneTarget) : legacyPhoneTarget;
        boolean alertPhoneOnWatchEnabled = hasAlertPhoneOnWatch ? prefs.getBoolean(KEY_ALERT_PHONE_ON_WATCH, legacyWatchTarget) : legacyWatchTarget;
        boolean alertWatchOnPhoneEnabled = hasAlertWatchOnPhone ? prefs.getBoolean(KEY_ALERT_WATCH_ON_PHONE, legacyPhoneTarget) : legacyPhoneTarget;
        boolean alertWatchOnWatchEnabled = hasAlertWatchOnWatch ? prefs.getBoolean(KEY_ALERT_WATCH_ON_WATCH, legacyWatchTarget) : legacyWatchTarget;
        boolean migrated = false;
        StringBuilder source = new StringBuilder();
        if (hasAlertPhoneOnPhone || hasAlertPhoneOnWatch || hasAlertWatchOnPhone || hasAlertWatchOnWatch
                || hasMonitorPhone || hasMonitorWatch) {
            source.append("explicit");
        } else if (prefs.contains(KEY_ENABLED)) {
            source.append("global");
        } else {
            source.append("legacy");
        }
        if (!hasMonitorPhone || !hasMonitorWatch || !hasAlertPhoneOnPhone || !hasAlertPhoneOnWatch
                || !hasAlertWatchOnPhone || !hasAlertWatchOnWatch) {
            migrated = true;
        }
        boolean alertsEnabled = alertPhoneOnPhoneEnabled || alertPhoneOnWatchEnabled
                || alertWatchOnPhoneEnabled || alertWatchOnWatchEnabled;
        if (persistFixes && migrated) {
            prefs.edit()
                    .putBoolean(KEY_MONITOR_PHONE_BATTERY, monitorPhoneEnabled)
                    .putBoolean(KEY_MONITOR_WATCH_BATTERY, monitorWatchEnabled)
                    .putBoolean(KEY_ALERT_PHONE_ON_PHONE, alertPhoneOnPhoneEnabled)
                    .putBoolean(KEY_ALERT_PHONE_ON_WATCH, alertPhoneOnWatchEnabled)
                    .putBoolean(KEY_ALERT_WATCH_ON_PHONE, alertWatchOnPhoneEnabled)
                    .putBoolean(KEY_ALERT_WATCH_ON_WATCH, alertWatchOnWatchEnabled)
                    .putBoolean(KEY_ENABLED, alertsEnabled)
                    .apply();
        }
        ProtectionState state = new ProtectionState(
                monitorPhoneEnabled,
                monitorWatchEnabled,
                alertPhoneOnPhoneEnabled,
                alertPhoneOnWatchEnabled,
                alertWatchOnPhoneEnabled,
                alertWatchOnWatchEnabled,
                alertsEnabled,
                migrated,
                source.toString()
        );
        if (persistFixes || migrated) {
            logProtectionState("flags_loaded", state, "reason=" + reason
                    + " legacyEnabled=" + legacyEnabled
                    + " hasLegacyPhoneTarget=" + hasLegacyPhoneTarget
                    + " hasLegacyWatchTarget=" + hasLegacyWatchTarget
                    + " hasMonitorPhone=" + hasMonitorPhone
                    + " hasMonitorWatch=" + hasMonitorWatch
                    + " hasAlertPhoneOnPhone=" + hasAlertPhoneOnPhone
                    + " hasAlertPhoneOnWatch=" + hasAlertPhoneOnWatch
                    + " hasAlertWatchOnPhone=" + hasAlertWatchOnPhone
                    + " hasAlertWatchOnWatch=" + hasAlertWatchOnWatch);
        }
        return state;
    }

    private static void logProtectionState(String event, ProtectionState state, String extra) {
        Log.i(
                TAG,
                event
                        + " source=" + state.source
                        + " monitorPhoneEnabled=" + state.monitorPhoneEnabled
                        + " monitorWatchEnabled=" + state.monitorWatchEnabled
                        + " alertPhoneOnPhoneEnabled=" + state.alertPhoneOnPhoneEnabled
                        + " alertPhoneOnWatchEnabled=" + state.alertPhoneOnWatchEnabled
                        + " alertWatchOnPhoneEnabled=" + state.alertWatchOnPhoneEnabled
                        + " alertWatchOnWatchEnabled=" + state.alertWatchOnWatchEnabled
                        + " alertsEnabled=" + state.alertsEnabled
                        + " migrated=" + state.migrated
                        + (extra != null && !extra.isEmpty() ? " " + extra : "")
        );
    }

    private static String notificationTagFor(AlertSource source) {
        return source == AlertSource.PHONE ? TAG_PHONE_SOURCE : TAG_WATCH_SOURCE;
    }

    private static String resolveNotificationText(Context context, AlertType type, AlertSource source, int limit) {
        if (source == AlertSource.WATCH) {
            return type == AlertType.HIGH
                    ? context.getString(R.string.battery_alert_watch_high_notification_text, limit)
                    : context.getString(R.string.battery_alert_watch_low_notification_text, limit);
        }
        return type == AlertType.HIGH
                ? context.getString(R.string.battery_alert_phone_high_notification_text, limit)
                : context.getString(R.string.battery_alert_phone_low_notification_text, limit);
    }

    private enum AlertType {
        HIGH,
        LOW
    }

    private enum AlertSource {
        PHONE,
        WATCH
    }
}
