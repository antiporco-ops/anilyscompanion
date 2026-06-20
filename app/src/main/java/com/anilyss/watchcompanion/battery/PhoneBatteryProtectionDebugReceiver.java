package com.anilyss.watchcompanion.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.anilyss.watchcompanion.BuildConfig;

public class PhoneBatteryProtectionDebugReceiver extends BroadcastReceiver {

    private static final String TAG = "AniLysFullAlert";
    private static final String ACTION = "com.anilyss.watchcompanion.action.DEBUG_PHONE_BATTERY_ALERT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "debug_receiver_ignored build=release");
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        String command = intent.getStringExtra("command");
        if ("clear".equalsIgnoreCase(command)) {
            PhoneBatteryFullAlert.clearPostedNotifications(appContext);
            return;
        }
        Boolean soundOverride = intent.hasExtra("sound")
                ? intent.getBooleanExtra("sound", true)
                : null;
        Boolean vibrationOverride = intent.hasExtra("vibration")
                ? intent.getBooleanExtra("vibration", true)
                : null;
        boolean success = PhoneBatteryFullAlert.simulateAlert(appContext, command, soundOverride, vibrationOverride);
        Log.i(TAG, "debug_receiver_complete command=" + command
                + " soundOverride=" + soundOverride
                + " vibrationOverride=" + vibrationOverride
                + " success=" + success);
    }
}
