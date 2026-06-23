package com.anilyss.watchcompanion.battery;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PhoneBatteryFullAlertWorker extends Worker {

    private static final String TAG = "AniLysFullAlert";

    public PhoneBatteryFullAlertWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String reason = getInputData().getString("reason");
        if (reason == null || reason.isEmpty()) {
            reason = "rolling_worker";
        }
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.normalizeStoredState(getApplicationContext(), "worker_start:" + reason);
        Log.i(TAG, "worker_run reason=" + reason
                + " monitorPhoneEnabled=" + state.monitorPhoneEnabled
                + " monitorWatchEnabled=" + state.monitorWatchEnabled
                + " alertPhoneOnPhoneEnabled=" + state.alertPhoneOnPhoneEnabled
                + " alertPhoneOnWatchEnabled=" + state.alertPhoneOnWatchEnabled
                + " alertWatchOnPhoneEnabled=" + state.alertWatchOnPhoneEnabled
                + " alertWatchOnWatchEnabled=" + state.alertWatchOnWatchEnabled);
        PhoneBatteryFullAlert.evaluateCurrentState(getApplicationContext(), reason);
        PhoneBatteryFullAlert.scheduleRollingMonitor(getApplicationContext(), "worker_done:" + reason);
        Log.i(TAG, "worker_done reason=" + reason);
        return Result.success();
    }
}
