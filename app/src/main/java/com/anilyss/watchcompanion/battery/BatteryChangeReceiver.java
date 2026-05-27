package com.anilyss.watchcompanion.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BatteryChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "AniLysBattery";
    private static final String ALERT_TAG = "AniLysFullAlert";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context appContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        if (appContext == null) {
            return;
        }
        String action = intent != null ? intent.getAction() : null;
        String reason;
        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            reason = "power_connected";
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            reason = "power_disconnected";
        } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            reason = "battery_low";
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            reason = "battery_okay";
        } else {
            return;
        }

        Log.i(ALERT_TAG, "receiver_signal reason=" + reason + " action=queue_check");
        PhoneBatteryFullAlert.requestImmediateCheck(appContext, reason);
        PhoneBatterySender.syncPeriodicRefresh(appContext);
        if (!PhoneBatterySender.isFeatureEnabled(appContext)) {
            Log.d(TAG, "Battery receiver disabled by user; ignoring " + reason);
            return;
        }
        Log.d(TAG, "Battery receiver event -> " + reason);
        PhoneBatterySender.sendIfNeeded(appContext, reason);
    }
}
