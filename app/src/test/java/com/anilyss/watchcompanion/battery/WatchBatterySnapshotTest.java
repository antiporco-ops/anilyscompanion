package com.anilyss.watchcompanion.battery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WatchBatterySnapshotTest {

    @Test
    public void fromStoredBuildsFreshSnapshot() {
        WatchBatterySnapshot snapshot = WatchBatterySnapshot.fromStored(91, true, 4_000L, 9_500L);

        assertEquals(Integer.valueOf(91), snapshot.level);
        assertTrue(snapshot.charging);
        assertEquals(5_500L, snapshot.ageMs);
        assertTrue(snapshot.hasData);
    }

    @Test
    public void fromStoredHandlesMissingData() {
        WatchBatterySnapshot snapshot = WatchBatterySnapshot.fromStored(-1, true, 0L, 9_500L);

        assertNull(snapshot.level);
        assertFalse(snapshot.charging);
        assertEquals(Long.MAX_VALUE, snapshot.ageMs);
        assertFalse(snapshot.hasData);
    }

    @Test
    public void usesDedicatedWatchBatteryPath() {
        assertEquals("/watch_battery", WatchBatteryStore.DATA_PATH);
    }
}
