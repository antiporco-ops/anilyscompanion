package com.anilyss.watchcompanion.battery;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PhoneBatteryRefreshWorker extends Worker {

    public PhoneBatteryRefreshWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appContext = getApplicationContext();
        if (!PhoneBatterySender.isFeatureEnabled(appContext)) {
            return Result.success();
        }
        PhoneBatterySender.sendIfNeeded(appContext, "periodic");
        PhoneBatterySender.syncPeriodicRefresh(appContext);
        return Result.success();
    }
}
