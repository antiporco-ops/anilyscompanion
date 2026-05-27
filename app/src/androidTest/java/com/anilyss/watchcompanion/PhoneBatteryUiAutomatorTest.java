package com.anilyss.watchcompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.app.NotificationManagerCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.anilyss.watchcompanion.battery.PhoneBatteryFullAlert;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PhoneBatteryUiAutomatorTest {

    private static final String APP_PACKAGE = "com.anilyss.watchcompanion";
    private static final String ALERT_TAG = "AniLysFullAlert";
    private static final long UI_TIMEOUT_MS = 10_000L;
    private static final long LOG_TIMEOUT_MS = 5_000L;

    private UiDevice device;
    private Context appContext;

    @Before
    public void setUp() throws Exception {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearAlertLogs();
        resetBatterySimulation();
        setFullAlertEnabled(false);
        cancelAppNotifications();
        device.pressHome();
        device.waitForIdle();
    }

    @After
    public void tearDown() throws Exception {
        setFullAlertEnabled(false);
        cancelAppNotifications();
        resetBatterySimulation();
        clearAlertLogs();
    }

    @Test
    public void opensBatteryTabAndShowsDiagnostics() {
        launchMainActivity();
        openBatteryTabFromBottomNav();

        assertAppObject("phone_battery_status");
        assertAppObject("phone_battery_last_sync");
        assertAppObject("phone_battery_companion_status");
        assertAppObject("phone_battery_connection_status");
        assertAppObject("switch_phone_full_alert_enabled");
        assertAppObject("phone_full_alert_status");
        assertAppObject("phone_full_alert_debug");
    }

    @Test
    public void fullAlertToggleUpdatesDebugBlock() throws Exception {
        launchBatteryTabDirectly();
        grantNotificationPermissionIfPossible();

        UiObject2 fullAlertToggle = assertAppObject("switch_phone_full_alert_enabled");
        boolean initialChecked = fullAlertToggle.isChecked();

        fullAlertToggle.click();
        device.waitForIdle();

        UiObject2 toggled = assertAppObject("switch_phone_full_alert_enabled");
        assertEquals(!initialChecked, toggled.isChecked());
        assertTrue(
                getAppObjectText("phone_full_alert_debug").contains(
                        !initialChecked
                                ? appContext.getString(R.string.phone_full_alert_debug_yes)
                                : appContext.getString(R.string.phone_full_alert_debug_no)
                )
        );

        toggled.click();
        device.waitForIdle();

        UiObject2 restored = assertAppObject("switch_phone_full_alert_enabled");
        assertEquals(initialChecked, restored.isChecked());
    }

    @Test
    public void postsFullAlertOncePerChargeCycleWhenPermissionGranted() throws Exception {
        grantNotificationPermissionIfPossible();
        Assume.assumeTrue("Notification permission must be granted for this test",
                PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext));

        setBatteryState(94, 2);
        launchBatteryTabDirectly();
        setFullAlertEnabled(true);
        evaluateAlert("ui_test_arm");

        clearAlertLogs();
        setBatteryState(100, 5);
        evaluateAlert("ui_test_full");

        String postedLogs = waitForAlertLog("notify_posted reason=ui_test_full");
        assertTrue(postedLogs.contains("notify_attempt reason=ui_test_full"));

        launchBatteryTabDirectly();
        String debugText = getAppObjectText("phone_full_alert_debug");
        assertTrue(debugText.contains("true"));
        assertTrue(debugText.contains("ui_test_full"));

        clearAlertLogs();
        evaluateAlert("ui_test_full_repeat");
        String repeatLogs = waitForAlertLog("eval_skip reason=ui_test_full_repeat skip=not_eligible");
        assertFalse(repeatLogs.contains("notify_posted reason=ui_test_full_repeat"));
    }

    @Test
    public void skipsFullAlertWhenNotificationPermissionIsMissing() throws Exception {
        Assume.assumeTrue("Permission revocation requires Android 13+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);

        revokeNotificationPermission();
        setBatteryState(94, 2);
        launchBatteryTabDirectly();
        setFullAlertEnabled(true);
        evaluateAlert("ui_test_arm_denied");

        clearAlertLogs();
        setBatteryState(100, 5);
        evaluateAlert("ui_test_full_denied");

        String deniedLogs = waitForAlertLog("notify_skip reason=ui_test_full_denied skip=permission_missing");
        assertFalse(deniedLogs.contains("notify_posted reason=ui_test_full_denied"));

        launchBatteryTabDirectly();
        String debugText = getAppObjectText("phone_full_alert_debug");
        assertTrue(debugText.contains(appContext.getString(R.string.phone_full_alert_debug_permission_missing)));
        assertTrue(debugText.contains("ui_test_full_denied"));
    }

    private void launchMainActivity() {
        Intent intent = appContext.getPackageManager().getLaunchIntentForPackage(APP_PACKAGE);
        assertNotNull("Launch intent for app package not found", intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        assertAppObject("main_bottom_navigation");
    }

    private void launchBatteryTabDirectly() {
        Intent intent = MainActivity.createOpenBatteryIntent(appContext);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        assertAppObject("phone_battery_status");
    }

    private void openBatteryTabFromBottomNav() {
        UiObject2 batteryTab = waitForAny(By.res(APP_PACKAGE, "nav_battery"), By.text(appContext.getString(R.string.nav_battery)));
        assertNotNull("Battery tab not found", batteryTab);
        batteryTab.click();
        device.wait(Until.hasObject(By.res(APP_PACKAGE, "phone_battery_status")), UI_TIMEOUT_MS);
    }

    private void setFullAlertEnabled(boolean enabled) {
        PhoneBatteryFullAlert.setEnabled(appContext, enabled);
    }

    private void evaluateAlert(String reason) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> PhoneBatteryFullAlert.evaluateCurrentState(appContext, reason)
        );
        device.waitForIdle();
    }

    private void grantNotificationPermissionIfPossible() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        device.executeShellCommand("pm grant " + APP_PACKAGE + " " + Manifest.permission.POST_NOTIFICATIONS);
        waitForPermission(true);
    }

    private void revokeNotificationPermission() throws Exception {
        device.executeShellCommand("pm revoke " + APP_PACKAGE + " " + Manifest.permission.POST_NOTIFICATIONS);
        waitForPermission(false);
    }

    private void waitForPermission(boolean granted) {
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext) == granted) {
                return;
            }
            SystemClock.sleep(200L);
        }
        assertEquals(granted, PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext));
    }

    private void setBatteryState(int level, int status) throws Exception {
        device.executeShellCommand("dumpsys battery unplug");
        device.executeShellCommand("dumpsys battery set level " + level);
        device.executeShellCommand("dumpsys battery set status " + status);
    }

    private void resetBatterySimulation() throws Exception {
        device.executeShellCommand("dumpsys battery reset");
    }

    private void clearAlertLogs() throws Exception {
        device.executeShellCommand("logcat -c");
    }

    private String waitForAlertLog(String needle) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + LOG_TIMEOUT_MS;
        String logs = "";
        while (SystemClock.elapsedRealtime() < deadline) {
            logs = readAlertLogs();
            if (logs.contains(needle)) {
                return logs;
            }
            SystemClock.sleep(250L);
        }
        assertTrue("Expected log not found: " + needle + "\n" + logs, logs.contains(needle));
        return logs;
    }

    private String readAlertLogs() throws Exception {
        return device.executeShellCommand("logcat -d -s " + ALERT_TAG + ":I *:S");
    }

    private void cancelAppNotifications() {
        NotificationManagerCompat.from(appContext).cancelAll();
        NotificationManager manager = appContext.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancelAll();
        }
    }

    private UiObject2 assertAppObject(String resName) {
        UiObject2 object = waitForObject(By.res(APP_PACKAGE, resName));
        assertNotNull("Missing object with res id " + resName, object);
        return object;
    }

    private String getAppObjectText(String resName) {
        UiObject2 object = assertAppObject(resName);
        return object.getText() != null ? object.getText() : "";
    }

    private UiObject2 waitForAny(BySelector primary, BySelector fallback) {
        UiObject2 object = waitForObject(primary);
        return object != null ? object : waitForObject(fallback);
    }

    private UiObject2 waitForObject(BySelector selector) {
        return device.wait(Until.findObject(selector), UI_TIMEOUT_MS);
    }
}
