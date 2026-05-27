package com.anilyss.watchcompanion.battery;

public final class PhoneBatteryCompanionDiagnostics {

    private static final long MIN_CONFIRMATION_WINDOW_MS = 5 * 60_000L;

    private PhoneBatteryCompanionDiagnostics() {
    }

    public static CompanionStatus resolve(
            long now,
            boolean connected,
            long lastWatchSeenAt,
            long lastSentAt,
            long freshnessWindowMs
    ) {
        long confirmationWindowMs = Math.max(freshnessWindowMs, MIN_CONFIRMATION_WINDOW_MS);
        boolean watchSeenRecently = isRecent(now, lastWatchSeenAt, confirmationWindowMs);
        boolean recentSyncWhileConnected = connected && isRecent(now, lastSentAt, confirmationWindowMs);
        if (watchSeenRecently) {
            return CompanionStatus.CONFIRMED;
        }
        if (recentSyncWhileConnected) {
            return CompanionStatus.NOT_CONFIRMED_RECENTLY;
        }
        if (lastWatchSeenAt > 0L || lastSentAt > 0L) {
            return CompanionStatus.NOT_CONFIRMED_RECENTLY;
        }
        return CompanionStatus.NOT_DETECTED;
    }

    private static boolean isRecent(long now, long timestamp, long windowMs) {
        return timestamp > 0L && now - timestamp <= windowMs;
    }

    public enum CompanionStatus {
        CONFIRMED,
        NOT_CONFIRMED_RECENTLY,
        NOT_DETECTED
    }
}
