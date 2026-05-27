package com.anilyss.watchcompanion.battery;

import android.content.Context;

import androidx.annotation.Nullable;

public final class WatchBatterySnapshot {

    @Nullable
    public final Integer level;
    public final boolean charging;
    public final long lastSyncAt;
    public final long ageMs;
    public final boolean hasData;

    private WatchBatterySnapshot(
            @Nullable Integer level,
            boolean charging,
            long lastSyncAt,
            long ageMs
    ) {
        this.level = level;
        this.charging = charging;
        this.lastSyncAt = lastSyncAt;
        this.ageMs = ageMs;
        this.hasData = level != null;
    }

    public static WatchBatterySnapshot readCurrent(Context context) {
        if (context == null) {
            return fromStored(-1, false, 0L, System.currentTimeMillis());
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        return fromStored(
                WatchBatteryStore.readLevel(appContext),
                WatchBatteryStore.readCharging(appContext),
                WatchBatteryStore.readTimestamp(appContext),
                System.currentTimeMillis()
        );
    }

    public static WatchBatterySnapshot fromStored(
            int rawLevel,
            boolean charging,
            long lastSyncAt,
            long now
    ) {
        Integer level = rawLevel >= 0 && rawLevel <= 100 ? rawLevel : null;
        long ageMs = lastSyncAt > 0L ? Math.max(0L, now - lastSyncAt) : Long.MAX_VALUE;
        return new WatchBatterySnapshot(level, level != null && charging, lastSyncAt, ageMs);
    }
}
