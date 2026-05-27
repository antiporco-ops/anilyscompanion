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
        Log.i(TAG, "worker_start enabled=" + PhoneBatteryFullAlert.isEnabled(getApplicationContext()));
        PhoneBatteryFullAlert.evaluateCurrentState(getApplicationContext(), "monitor");
        Log.i(TAG, "worker_done");
        return Result.success();
    }
}
