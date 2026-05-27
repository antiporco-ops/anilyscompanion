package com.anilyss.watchcompanion.battery;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PhoneBatteryCompanionDiagnosticsTest {

    private static final long NOW = 10_000_000L;
    private static final long FRESHNESS_WINDOW_MS = 5 * 60_000L;

    @Test
    public void resolve_confirmsRecentWatchEvidence() {
        PhoneBatteryCompanionDiagnostics.CompanionStatus status =
                PhoneBatteryCompanionDiagnostics.resolve(
                        NOW,
                        false,
                        NOW - 1_000L,
                        0L,
                        FRESHNESS_WINDOW_MS
                );

        assertEquals(PhoneBatteryCompanionDiagnostics.CompanionStatus.CONFIRMED, status);
    }

    @Test
    public void resolve_keepsRecentSyncWhileConnectedNeutral() {
        PhoneBatteryCompanionDiagnostics.CompanionStatus status =
                PhoneBatteryCompanionDiagnostics.resolve(
                        NOW,
                        true,
                        0L,
                        NOW - 1_000L,
                        FRESHNESS_WINDOW_MS
                );

        assertEquals(PhoneBatteryCompanionDiagnostics.CompanionStatus.NOT_CONFIRMED_RECENTLY, status);
    }

    @Test
    public void resolve_keepsFreshSendNeutralWhenDisconnected() {
        PhoneBatteryCompanionDiagnostics.CompanionStatus status =
                PhoneBatteryCompanionDiagnostics.resolve(
                        NOW,
                        false,
                        0L,
                        NOW - 1_000L,
                        FRESHNESS_WINDOW_MS
                );

        assertEquals(PhoneBatteryCompanionDiagnostics.CompanionStatus.NOT_CONFIRMED_RECENTLY, status);
    }

    @Test
    public void resolve_returnsNeutralWhenEvidenceIsStale() {
        PhoneBatteryCompanionDiagnostics.CompanionStatus status =
                PhoneBatteryCompanionDiagnostics.resolve(
                        NOW,
                        false,
                        NOW - (20 * 60_000L),
                        NOW - (20 * 60_000L),
                        FRESHNESS_WINDOW_MS
                );

        assertEquals(PhoneBatteryCompanionDiagnostics.CompanionStatus.NOT_CONFIRMED_RECENTLY, status);
    }

    @Test
    public void resolve_returnsNotDetectedWhenThereIsNoEvidence() {
        PhoneBatteryCompanionDiagnostics.CompanionStatus status =
                PhoneBatteryCompanionDiagnostics.resolve(
                        NOW,
                        false,
                        0L,
                        0L,
                        FRESHNESS_WINDOW_MS
                );

        assertEquals(PhoneBatteryCompanionDiagnostics.CompanionStatus.NOT_DETECTED, status);
    }
}
