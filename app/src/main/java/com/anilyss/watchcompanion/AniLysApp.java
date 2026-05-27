package com.anilyss.watchcompanion;

import android.app.Application;

import com.anilyss.watchcompanion.battery.PhoneBatteryFullAlert;
import com.anilyss.watchcompanion.battery.PhoneBatterySender;
import com.anilyss.watchcompanion.settings.AppLanguageSync;

public class AniLysApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLanguageSync.applyStoredLanguage(this);
        PhoneBatterySender.syncPeriodicRefresh(this);
        if (PhoneBatteryFullAlert.isEnabled(this)) {
            PhoneBatteryFullAlert.requestImmediateCheck(this, "app_start");
        }
    }

}
