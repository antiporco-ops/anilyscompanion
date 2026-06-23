package com.anilyss.watchcompanion;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.wear.remote.interactions.RemoteActivityHelper;

import com.anilyss.watchcompanion.battery.PhoneBatteryAutoRefreshStore;
import com.anilyss.watchcompanion.battery.PhoneBatteryAutoRefreshSync;
import com.anilyss.watchcompanion.battery.PhoneBatteryCompanionDiagnostics;
import com.anilyss.watchcompanion.battery.PhoneBatteryCompanionStore;
import com.anilyss.watchcompanion.battery.PhoneBatteryFullAlert;
import com.anilyss.watchcompanion.battery.PhoneBatteryProtectionSync;
import com.anilyss.watchcompanion.battery.PhoneBatterySender;
import com.anilyss.watchcompanion.battery.WatchBatterySnapshot;
import com.anilyss.watchcompanion.battery.WatchBatteryStore;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatteryFragment extends Fragment {

    private static final String TAG = "AniLysBattery";
    private static final long JUST_NOW_WINDOW_MS = 60_000L;
    private static final int OPEN_ON_PHONE_REASON_NO_WATCH = 1;
    private static final int OPEN_ON_PHONE_REASON_ERROR = 2;
    private static final String WEAR_APP_PACKAGE = "com.anilyss.watchcompanion";
    private static final Uri WEAR_APP_DEEP_LINK_URI = Uri.parse("anilys://watchcompanion/battery");
    private static final int[] HIGH_LIMIT_OPTIONS = {80, 85, 90};
    private static final int[] LOW_LIMIT_OPTIONS = {25, 20, 15};
    private static final String KEY_NOTIFICATION_PERMISSION_PROMPTED =
            "batteryProtectionNotificationPermissionPromptedV2";

    private ExecutorService bg;
    private RemoteActivityHelper remote;
    @Nullable
    private RadioButton autoRefresh5Button;
    @Nullable
    private RadioButton autoRefresh10Button;
    @Nullable
    private RadioButton autoRefresh15Button;
    @Nullable
    private RadioGroup highLimitGroup;
    @Nullable
    private RadioGroup lowLimitGroup;
    @Nullable
    private RadioGroup limitModeGroup;
    @Nullable
    private LinearLayout presetLimitContainer;
    @Nullable
    private LinearLayout customLimitContainer;
    @Nullable
    private SeekBar customHighSeek;
    @Nullable
    private SeekBar customLowSeek;
    @Nullable
    private TextView customHighValueText;
    @Nullable
    private TextView customLowValueText;
    @Nullable
    private TextView lastSyncText;
    @Nullable
    private TextView companionStatusText;
    @Nullable
    private TextView connectionStatusText;
    @Nullable
    private TextView watchBatteryValueText;
    @Nullable
    private TextView watchBatteryDetailText;
    @Nullable
    private SwitchCompat phoneProtectionSwitch;
    @Nullable
    private SwitchCompat watchProtectionSwitch;
    @Nullable
    private SwitchCompat alertPhoneOnPhoneSwitch;
    @Nullable
    private SwitchCompat alertPhoneOnWatchSwitch;
    @Nullable
    private SwitchCompat alertWatchOnPhoneSwitch;
    @Nullable
    private SwitchCompat alertWatchOnWatchSwitch;
    @Nullable
    private SwitchCompat phoneAlertSoundSwitch;
    @Nullable
    private SwitchCompat phoneAlertVibrationSwitch;
    @Nullable
    private SwitchCompat watchAlertSoundSwitch;
    @Nullable
    private SwitchCompat watchAlertVibrationSwitch;
    @Nullable
    private MaterialButton notificationPermissionButton;
    @Nullable
    private TextView notificationPermissionStatusText;
    @Nullable
    private TextView protectionStatusText;
    @Nullable
    private MaterialButton reactivateProtectionButton;
    @Nullable
    private TextView whyProtectBatteryTitleText;
    @Nullable
    private TextView whyProtectBatteryBodyText;
    @Nullable
    private View destinationPhoneRow;
    @Nullable
    private View destinationWatchRow;
    @Nullable
    private View phoneEffectsDestination;
    @Nullable
    private View watchEffectsDestination;
    @Nullable
    private View phoneSoundRow;
    @Nullable
    private View phoneVibrationRow;
    @Nullable
    private View watchSoundRow;
    @Nullable
    private View watchVibrationRow;
    private boolean suppressAutoRefreshSelection;
    private boolean suppressLimitSelection;
    private int previewMinutes = 10;
    private long previewUpdatedAt = 0L;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                Log.i(TAG, "notification_permission_result granted=" + granted);
                refreshFullAlertUi();
                if (granted) {
                    PhoneBatteryFullAlert.ensureMonitoring(
                            requireContext().getApplicationContext(),
                            "permission_granted"
                    );
                }
            });
    private final DataClient.OnDataChangedListener settingsDataChangedListener = dataEvents -> {
        boolean shouldRefresh = false;
        try {
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED
                        && (PhoneBatteryAutoRefreshSync.SETTINGS_PATH.equals(event.getDataItem().getUri().getPath())
                        || WatchBatteryStore.DATA_PATH.equals(event.getDataItem().getUri().getPath())
                        || PhoneBatteryProtectionSync.SETTINGS_PATH.equals(event.getDataItem().getUri().getPath()))) {
                    shouldRefresh = true;
                    break;
                }
            }
        } finally {
            dataEvents.release();
        }
        if (shouldRefresh) {
            runOnMainThread(() -> {
                refreshSelectionFromStore();
                refreshDiagnostics();
                refreshFullAlertUi();
            });
        }
    };

    private final MessageClient.OnMessageReceivedListener settingsUiPokeListener = event -> {
        if (!PhoneBatteryAutoRefreshSync.UI_POKE_PATH.equals(event.getPath())) {
            return;
        }
        UiPokePayload payload = parseUiPokePayload(event);
        if (payload == null) {
            return;
        }
        runOnMainThread(() -> {
            Log.d(TAG, "ui poke received minutes=" + payload.minutes + " updatedAt=" + payload.updatedAt);
            applyPreviewIfNewer(payload.minutes, payload.updatedAt);
        });
    };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_battery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bg = Executors.newSingleThreadExecutor();
        remote = new RemoteActivityHelper(requireContext(), bg);

        SwitchCompat featureSwitch = view.findViewById(R.id.switch_phone_battery_enabled);
        autoRefresh5Button = view.findViewById(R.id.radio_phone_battery_auto_refresh_5);
        autoRefresh10Button = view.findViewById(R.id.radio_phone_battery_auto_refresh_10);
        autoRefresh15Button = view.findViewById(R.id.radio_phone_battery_auto_refresh_15);
        highLimitGroup = view.findViewById(R.id.radio_group_battery_alert_high_limit);
        lowLimitGroup = view.findViewById(R.id.radio_group_battery_alert_low_limit);
        limitModeGroup = view.findViewById(R.id.radio_group_battery_alert_limit_mode);
        presetLimitContainer = view.findViewById(R.id.battery_alert_preset_container);
        customLimitContainer = view.findViewById(R.id.battery_alert_custom_container);
        customHighSeek = view.findViewById(R.id.seek_battery_alert_custom_high);
        customLowSeek = view.findViewById(R.id.seek_battery_alert_custom_low);
        customHighValueText = view.findViewById(R.id.battery_alert_custom_high_value);
        customLowValueText = view.findViewById(R.id.battery_alert_custom_low_value);
        lastSyncText = view.findViewById(R.id.phone_battery_last_sync);
        companionStatusText = view.findViewById(R.id.phone_battery_companion_status);
        connectionStatusText = view.findViewById(R.id.phone_battery_connection_status);
        watchBatteryValueText = view.findViewById(R.id.watch_battery_value);
        watchBatteryDetailText = view.findViewById(R.id.watch_battery_detail);
        phoneProtectionSwitch = view.findViewById(R.id.switch_battery_protect_phone);
        watchProtectionSwitch = view.findViewById(R.id.switch_battery_protect_watch);
        alertPhoneOnPhoneSwitch = view.findViewById(R.id.switch_battery_alert_phone_on_phone);
        alertPhoneOnWatchSwitch = view.findViewById(R.id.switch_battery_alert_phone_on_watch);
        alertWatchOnPhoneSwitch = view.findViewById(R.id.switch_battery_alert_watch_on_phone);
        alertWatchOnWatchSwitch = view.findViewById(R.id.switch_battery_alert_watch_on_watch);
        phoneAlertSoundSwitch = view.findViewById(R.id.switch_battery_phone_alert_sound);
        phoneAlertVibrationSwitch = view.findViewById(R.id.switch_battery_phone_alert_vibration);
        watchAlertSoundSwitch = view.findViewById(R.id.switch_battery_watch_alert_sound);
        watchAlertVibrationSwitch = view.findViewById(R.id.switch_battery_watch_alert_vibration);
        notificationPermissionButton = view.findViewById(R.id.btn_battery_notification_permission);
        notificationPermissionStatusText =
                view.findViewById(R.id.battery_alert_notification_permission_status);
        protectionStatusText = view.findViewById(R.id.battery_alert_protection_status);
        reactivateProtectionButton = view.findViewById(R.id.btn_battery_reactivate);
        whyProtectBatteryTitleText = view.findViewById(R.id.battery_alert_why_title);
        whyProtectBatteryBodyText = view.findViewById(R.id.battery_alert_why_body);
        destinationPhoneRow = view.findViewById(R.id.battery_alert_destinations_phone_row);
        destinationWatchRow = view.findViewById(R.id.battery_alert_destinations_watch_row);
        phoneEffectsDestination = view.findViewById(R.id.battery_alert_effects_phone_destination);
        watchEffectsDestination = view.findViewById(R.id.battery_alert_effects_watch_destination);
        phoneSoundRow = view.findViewById(R.id.row_battery_phone_alert_sound);
        phoneVibrationRow = view.findViewById(R.id.row_battery_phone_alert_vibration);
        watchSoundRow = view.findViewById(R.id.row_battery_watch_alert_sound);
        watchVibrationRow = view.findViewById(R.id.row_battery_watch_alert_vibration);
        MaterialButton syncNowButton = view.findViewById(R.id.btn_phone_battery_sync_now);
        MaterialButton openWearButton = view.findViewById(R.id.btn_open_anilys_wear_on_watch);

        Context appContext = requireContext().getApplicationContext();
        PhoneBatteryFullAlert.normalizeStoredState(appContext, "battery_fragment_create");
        boolean enabled = PhoneBatterySender.isFeatureEnabled(appContext);
        featureSwitch.setChecked(enabled);
        syncNowButton.setEnabled(enabled);
        syncNowButton.setAlpha(enabled ? 1f : 0.55f);
        featureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PhoneBatterySender.setFeatureEnabled(appContext, isChecked);
            syncNowButton.setEnabled(isChecked);
            syncNowButton.setAlpha(isChecked ? 1f : 0.55f);
            Log.i(TAG, "Phone battery feature changed: enabled=" + isChecked);
            if (isChecked) {
                PhoneBatterySender.sendIfNeeded(appContext, "manual");
            }
            refreshDiagnostics();
        });

        if (phoneProtectionSwitch != null) {
            phoneProtectionSwitch.setChecked(PhoneBatteryFullAlert.isPhoneMonitorEnabled(appContext));
            phoneProtectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setPhoneMonitorEnabled(appContext, isChecked);
                if (isChecked) {
                    requestNotificationPermission(false);
                }
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (watchProtectionSwitch != null) {
            watchProtectionSwitch.setChecked(PhoneBatteryFullAlert.isWatchMonitorEnabled(appContext));
            watchProtectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setWatchMonitorEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (alertPhoneOnPhoneSwitch != null) {
            alertPhoneOnPhoneSwitch.setChecked(PhoneBatteryFullAlert.isAlertPhoneOnPhoneEnabled(appContext));
            alertPhoneOnPhoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setAlertPhoneOnPhoneEnabled(appContext, isChecked);
                if (isChecked) {
                    requestNotificationPermission(false);
                }
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (alertPhoneOnWatchSwitch != null) {
            alertPhoneOnWatchSwitch.setChecked(PhoneBatteryFullAlert.isAlertPhoneOnWatchEnabled(appContext));
            alertPhoneOnWatchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setAlertPhoneOnWatchEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (alertWatchOnPhoneSwitch != null) {
            alertWatchOnPhoneSwitch.setChecked(PhoneBatteryFullAlert.isAlertWatchOnPhoneEnabled(appContext));
            alertWatchOnPhoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setAlertWatchOnPhoneEnabled(appContext, isChecked);
                if (isChecked) {
                    requestNotificationPermission(false);
                }
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (alertWatchOnWatchSwitch != null) {
            alertWatchOnWatchSwitch.setChecked(PhoneBatteryFullAlert.isAlertWatchOnWatchEnabled(appContext));
            alertWatchOnWatchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setAlertWatchOnWatchEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (phoneAlertSoundSwitch != null) {
            phoneAlertSoundSwitch.setChecked(PhoneBatteryFullAlert.isPhoneSoundEnabled(appContext));
            phoneAlertSoundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setPhoneSoundEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (phoneAlertVibrationSwitch != null) {
            phoneAlertVibrationSwitch.setChecked(PhoneBatteryFullAlert.isPhoneVibrationEnabled(appContext));
            phoneAlertVibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setPhoneVibrationEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (watchAlertSoundSwitch != null) {
            watchAlertSoundSwitch.setChecked(PhoneBatteryFullAlert.isWatchSoundEnabled(appContext));
            watchAlertSoundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setWatchSoundEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (watchAlertVibrationSwitch != null) {
            watchAlertVibrationSwitch.setChecked(PhoneBatteryFullAlert.isWatchVibrationEnabled(appContext));
            watchAlertVibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PhoneBatteryFullAlert.setWatchVibrationEnabled(appContext, isChecked);
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        phoneSoundRow.setOnClickListener(v -> toggleSwitch(phoneAlertSoundSwitch));
        phoneVibrationRow.setOnClickListener(v -> toggleSwitch(phoneAlertVibrationSwitch));
        watchSoundRow.setOnClickListener(v -> toggleSwitch(watchAlertSoundSwitch));
        watchVibrationRow.setOnClickListener(v -> toggleSwitch(watchAlertVibrationSwitch));
        if (notificationPermissionButton != null) {
            notificationPermissionButton.setOnClickListener(v -> openNotificationSettings());
        }
        if (reactivateProtectionButton != null) {
            reactivateProtectionButton.setOnClickListener(v -> reactivateProtection(appContext));
        }
        if (whyProtectBatteryTitleText != null && whyProtectBatteryBodyText != null) {
            whyProtectBatteryBodyText.setVisibility(View.GONE);
            whyProtectBatteryTitleText.setOnClickListener(v -> toggleWhyProtectBattery());
        }
        bindCustomLimitSeekBars(appContext);
        bindAutoRefreshOption(autoRefresh5Button, 5);
        bindAutoRefreshOption(autoRefresh10Button, 10);
        bindAutoRefreshOption(autoRefresh15Button, 15);
        PhoneBatteryFullAlert.ensureMonitoring(appContext, "battery_fragment_open");
        PhoneBatteryProtectionSync.publishCurrent(appContext);
        refreshSelectionFromStore();
        refreshDiagnostics();
        refreshFullAlertUi();
        requestNotificationPermission(false);

        openWearButton.setOnClickListener(v -> openAniLysWearListingOnWatch());
        syncNowButton.setOnClickListener(v -> {
            PhoneBatterySender.sendIfNeeded(appContext, "manual");
            refreshDiagnostics();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Wearable.getDataClient(requireContext()).addListener(settingsDataChangedListener);
        Wearable.getMessageClient(requireContext()).addListener(settingsUiPokeListener);
        Context appContext = requireContext().getApplicationContext();
        PhoneBatteryFullAlert.normalizeStoredState(appContext, "battery_fragment_resume");
        PhoneBatteryProtectionSync.publishCurrent(appContext);
        refreshSelectionFromStore();
        refreshDiagnostics();
        refreshFullAlertUi();
    }

    @Override
    public void onPause() {
        Wearable.getMessageClient(requireContext()).removeListener(settingsUiPokeListener);
        Wearable.getDataClient(requireContext()).removeListener(settingsDataChangedListener);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bg != null) {
            bg.shutdownNow();
            bg = null;
        }
        lastSyncText = null;
        companionStatusText = null;
        connectionStatusText = null;
        watchBatteryValueText = null;
        watchBatteryDetailText = null;
        phoneAlertSoundSwitch = null;
        phoneAlertVibrationSwitch = null;
        watchAlertSoundSwitch = null;
        watchAlertVibrationSwitch = null;
        alertPhoneOnPhoneSwitch = null;
        alertPhoneOnWatchSwitch = null;
        alertWatchOnPhoneSwitch = null;
        alertWatchOnWatchSwitch = null;
        notificationPermissionButton = null;
        notificationPermissionStatusText = null;
        protectionStatusText = null;
        reactivateProtectionButton = null;
        phoneProtectionSwitch = null;
        watchProtectionSwitch = null;
        whyProtectBatteryTitleText = null;
        whyProtectBatteryBodyText = null;
        destinationPhoneRow = null;
        destinationWatchRow = null;
        phoneEffectsDestination = null;
        watchEffectsDestination = null;
        phoneSoundRow = null;
        phoneVibrationRow = null;
        watchSoundRow = null;
        watchVibrationRow = null;
        autoRefresh5Button = null;
        autoRefresh10Button = null;
        autoRefresh15Button = null;
        highLimitGroup = null;
        lowLimitGroup = null;
        limitModeGroup = null;
        presetLimitContainer = null;
        customLimitContainer = null;
        customHighSeek = null;
        customLowSeek = null;
        customHighValueText = null;
        customLowValueText = null;
    }

    private void refreshSelectionFromStore() {
        if (!isAdded()
                || autoRefresh5Button == null
                || autoRefresh10Button == null
                || autoRefresh15Button == null) {
            return;
        }
        int currentMinutes = PhoneBatteryAutoRefreshStore.readMinutes(requireContext().getApplicationContext());
        long storeUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(requireContext().getApplicationContext());
        boolean usePreview = previewUpdatedAt > storeUpdatedAt;
        int effectiveMinutes = usePreview ? previewMinutes : currentMinutes;
        if (!usePreview && previewUpdatedAt <= storeUpdatedAt) {
            previewUpdatedAt = 0L;
        }
        if (isAutoRefreshChecked(effectiveMinutes)) {
            return;
        }
        setAutoRefreshCheckedState(effectiveMinutes);
        if (usePreview) {
            Log.d(TAG, "ui preview applied minutes=" + effectiveMinutes + " updatedAt=" + previewUpdatedAt);
        } else {
            Log.d(TAG, "store state applied minutes=" + effectiveMinutes + " updatedAt=" + storeUpdatedAt);
        }
    }

    private void bindAutoRefreshOption(@Nullable RadioButton button, int minutes) {
        if (button == null) {
            return;
        }
        button.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (!isChecked || suppressAutoRefreshSelection || !isAdded()) {
                return;
            }
            setAutoRefreshCheckedState(minutes);
            applyAutoRefreshSelection(minutes);
        });
    }

    private void applyAutoRefreshSelection(int selectedMinutes) {
        int storeMinutes = PhoneBatteryAutoRefreshStore.readMinutes(requireContext().getApplicationContext());
        long storeUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(requireContext().getApplicationContext());
        int effectiveMinutes = previewUpdatedAt > storeUpdatedAt ? previewMinutes : storeMinutes;
        if (selectedMinutes == effectiveMinutes) {
            return;
        }
        Log.d(TAG, "local selection minutes=" + selectedMinutes);
        PhoneBatteryAutoRefreshSync.setLocalAndSync(requireContext().getApplicationContext(), selectedMinutes);
        Log.i(TAG, "Phone battery auto-refresh interval changed: " + selectedMinutes + "m");
        refreshDiagnostics();
    }

    private void setAutoRefreshCheckedState(int minutes) {
        if (autoRefresh5Button == null || autoRefresh10Button == null || autoRefresh15Button == null) {
            return;
        }
        suppressAutoRefreshSelection = true;
        autoRefresh5Button.setChecked(minutes == 5);
        autoRefresh10Button.setChecked(minutes == 10);
        autoRefresh15Button.setChecked(minutes == 15);
        suppressAutoRefreshSelection = false;
    }

    private boolean isAutoRefreshChecked(int minutes) {
        if (autoRefresh5Button == null || autoRefresh10Button == null || autoRefresh15Button == null) {
            return false;
        }
        return minutes == 5
                ? autoRefresh5Button.isChecked()
                : minutes == 15
                ? autoRefresh15Button.isChecked()
                : autoRefresh10Button.isChecked();
    }

    private void toggleWhyProtectBattery() {
        if (whyProtectBatteryBodyText == null) {
            return;
        }
        boolean expanded = whyProtectBatteryBodyText.getVisibility() == View.VISIBLE;
        whyProtectBatteryBodyText.setVisibility(expanded ? View.GONE : View.VISIBLE);
    }

    private void applyPreviewIfNewer(int minutes, long updatedAt) {
        if (!isAdded()) return;
        long storeUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(requireContext().getApplicationContext());
        long latestKnownUpdatedAt = Math.max(storeUpdatedAt, previewUpdatedAt);
        if (updatedAt <= latestKnownUpdatedAt) {
            Log.d(TAG, "stale ui poke ignored updatedAt=" + updatedAt + " latest=" + latestKnownUpdatedAt);
            return;
        }
        previewMinutes = PhoneBatteryAutoRefreshStore.sanitizeMinutes(minutes);
        previewUpdatedAt = updatedAt;
        refreshSelectionFromStore();
        refreshDiagnostics();
    }

    @Nullable
    private UiPokePayload parseUiPokePayload(MessageEvent event) {
        if (event == null || event.getData() == null || event.getData().length < 12) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(event.getData()).order(ByteOrder.BIG_ENDIAN);
        int minutes = PhoneBatteryAutoRefreshStore.sanitizeMinutes(buffer.getInt());
        long updatedAt = buffer.getLong();
        return new UiPokePayload(minutes, updatedAt);
    }

    private static final class UiPokePayload {
        final int minutes;
        final long updatedAt;

        UiPokePayload(int minutes, long updatedAt) {
            this.minutes = minutes;
            this.updatedAt = updatedAt;
        }
    }

    private void openAniLysWearListingOnWatch() {
        if (!isAdded()) return;
        final Intent appDeepLinkIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(WEAR_APP_DEEP_LINK_URI)
                .setPackage(WEAR_APP_PACKAGE);
        final Uri marketUri = Uri.parse("market://details?id=" + WEAR_APP_PACKAGE);
        final Uri webUri = Uri.parse("https://play.google.com/store/apps/details?id=" + WEAR_APP_PACKAGE);

        final Intent marketIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(marketUri);
        final Intent webIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(webUri);

        Wearable.getNodeClient(requireContext()).getConnectedNodes()
                .addOnSuccessListener(nodes -> handleConnectedNodes(nodes, appDeepLinkIntent, marketIntent, webIntent, webUri))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getConnectedNodes FAILURE, fallback to phone.", e);
                    showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_ERROR);
                });
    }

    private void handleConnectedNodes(
            List<Node> nodes,
            Intent appIntent,
            Intent marketIntent,
            Intent webIntent,
            Uri webUri
    ) {
        if (!isAdded()) return;
        if (nodes == null || nodes.isEmpty()) {
            Log.i(TAG, "No watch connected; fallback to phone listing.");
            Toast.makeText(requireContext(), getString(R.string.toast_no_wearos), Toast.LENGTH_SHORT).show();
            showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_NO_WATCH);
            return;
        }

        Node target = null;
        for (Node node : nodes) {
            if (node.isNearby()) {
                target = node;
                break;
            }
        }
        if (target == null) {
            target = nodes.get(0);
        }

        String nodeId = target.getId();
        Toast.makeText(requireContext(), getString(R.string.toast_check_watch), Toast.LENGTH_SHORT).show();
        tryStartRemoteActivityWithTimeout(
                appIntent,
                nodeId,
                "startRemoteActivity(deepLinkApp) nodeId=" + nodeId,
                () -> Log.i(TAG, "AniLys Wear deep link opened on watch."),
                () -> tryStartRemoteActivityWithTimeout(
                        marketIntent,
                        nodeId,
                        "startRemoteActivity(market) nodeId=" + nodeId,
                        () -> Log.i(TAG, "AniLys Wear market listing opened on watch."),
                        () -> tryStartRemoteActivityWithTimeout(
                                webIntent,
                                nodeId,
                                "startRemoteActivity(web) nodeId=" + nodeId,
                                () -> Log.i(TAG, "AniLys Wear web listing opened on watch."),
                                () -> showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_ERROR)
                        )
                )
        );
    }

    private void tryStartRemoteActivityWithTimeout(
            Intent intent,
            String nodeId,
            String opLogId,
            Runnable onSuccess,
            Runnable onFailure
    ) {
        if (remote == null || bg == null) {
            runOnMainThread(onFailure);
            return;
        }
        final com.google.common.util.concurrent.ListenableFuture<Void> remoteOpenFuture;
        try {
            remoteOpenFuture = remote.startRemoteActivity(intent, nodeId);
        } catch (Exception e) {
            Log.e(TAG, opLogId + " EXCEPTION", e);
            runOnMainThread(onFailure);
            return;
        }

        final AtomicBoolean handled = new AtomicBoolean(false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!remoteOpenFuture.isDone() && handled.compareAndSet(false, true)) {
                Log.e(TAG, opLogId + " TIMEOUT");
                onFailure.run();
            }
        }, 5000);

        remoteOpenFuture.addListener(() -> {
            try {
                remoteOpenFuture.get();
                if (handled.compareAndSet(false, true)) {
                    Log.i(TAG, opLogId + " SUCCESS");
                    runOnMainThread(onSuccess);
                }
            } catch (Exception e) {
                if (handled.compareAndSet(false, true)) {
                    Log.e(TAG, opLogId + " FAILURE", e);
                    runOnMainThread(onFailure);
                }
            }
        }, bg);
    }

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else if (isAdded()) {
            requireActivity().runOnUiThread(action);
        }
    }

    private void openWebOnPhone(Uri webUri) {
        if (!isAdded()) return;
        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
    }

    private void showOpenOnPhoneDialog(Uri webUri, int reason) {
        runOnMainThread(() -> {
            if (!isAdded() || webUri == null) {
                return;
            }
            if (requireActivity().isFinishing() || requireActivity().isDestroyed()) {
                return;
            }

            int messageRes = reason == OPEN_ON_PHONE_REASON_NO_WATCH
                    ? R.string.dialog_open_on_phone_no_watch_message
                    : R.string.dialog_open_on_phone_error_message;

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_open_on_phone_title)
                    .setMessage(messageRes)
                    .setPositiveButton(R.string.dialog_open_on_phone_positive, (dialog, which) -> openWebOnPhone(webUri))
                    .setNegativeButton(R.string.dialog_open_on_phone_negative, (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    private void refreshDiagnostics() {
        if (!isAdded()) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        TextView lastSyncView = lastSyncText;
        TextView companionView = companionStatusText;
        TextView connectionView = connectionStatusText;
        TextView watchValueView = watchBatteryValueText;
        TextView watchDetailView = watchBatteryDetailText;
        if (lastSyncView == null
                || companionView == null
                || connectionView == null
                || watchValueView == null
                || watchDetailView == null) {
            return;
        }

        long lastSentAt = PhoneBatterySender.readLastSentAt(appContext);
        long lastWatchSeenAt = PhoneBatteryCompanionStore.readLastWatchSeenAt(appContext);
        WatchBatterySnapshot watchBatterySnapshot = WatchBatterySnapshot.readCurrent(appContext);
        lastSyncView.setVisibility(View.GONE);
        applyWatchBatteryUi(watchBatterySnapshot);

        final boolean enabled = PhoneBatterySender.isFeatureEnabled(appContext);
        final long freshnessWindowMs = PhoneBatteryAutoRefreshStore.readMinutes(appContext) * 60_000L;
        Wearable.getNodeClient(requireContext()).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (!isAdded()) {
                        return;
                    }
                    boolean connected = nodes != null && !nodes.isEmpty();
                    PhoneBatteryCompanionDiagnostics.CompanionStatus companionStatus =
                            PhoneBatteryCompanionDiagnostics.resolve(
                                    System.currentTimeMillis(),
                                    connected,
                                    lastWatchSeenAt,
                                    lastSentAt,
                                    freshnessWindowMs
                            );
                    applyDiagnosticsUi(enabled, connected, companionStatus, lastSentAt, freshnessWindowMs);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    PhoneBatteryCompanionDiagnostics.CompanionStatus companionStatus =
                            PhoneBatteryCompanionDiagnostics.resolve(
                                    System.currentTimeMillis(),
                                    false,
                                    lastWatchSeenAt,
                                    lastSentAt,
                                    freshnessWindowMs
                            );
                    applyDiagnosticsUi(enabled, false, companionStatus, lastSentAt, freshnessWindowMs);
                });
    }

    private void refreshFullAlertUi() {
        if (!isAdded()) {
            return;
        }
        SwitchCompat phoneProtectionToggle = phoneProtectionSwitch;
        SwitchCompat watchProtectionToggle = watchProtectionSwitch;
        SwitchCompat phoneOnPhoneToggle = alertPhoneOnPhoneSwitch;
        SwitchCompat phoneOnWatchToggle = alertPhoneOnWatchSwitch;
        SwitchCompat watchOnPhoneToggle = alertWatchOnPhoneSwitch;
        SwitchCompat watchOnWatchToggle = alertWatchOnWatchSwitch;
        SwitchCompat phoneSoundToggle = phoneAlertSoundSwitch;
        SwitchCompat phoneVibrationToggle = phoneAlertVibrationSwitch;
        SwitchCompat watchSoundToggle = watchAlertSoundSwitch;
        SwitchCompat watchVibrationToggle = watchAlertVibrationSwitch;
        Context appContext = requireContext().getApplicationContext();
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.normalizeStoredState(appContext, "battery_fragment_refresh");
        if (phoneSoundToggle != null && phoneSoundToggle.isChecked() != PhoneBatteryFullAlert.isPhoneSoundEnabled(appContext)) {
            phoneSoundToggle.setChecked(PhoneBatteryFullAlert.isPhoneSoundEnabled(appContext));
        }
        if (phoneVibrationToggle != null && phoneVibrationToggle.isChecked() != PhoneBatteryFullAlert.isPhoneVibrationEnabled(appContext)) {
            phoneVibrationToggle.setChecked(PhoneBatteryFullAlert.isPhoneVibrationEnabled(appContext));
        }
        if (watchSoundToggle != null && watchSoundToggle.isChecked() != PhoneBatteryFullAlert.isWatchSoundEnabled(appContext)) {
            watchSoundToggle.setChecked(PhoneBatteryFullAlert.isWatchSoundEnabled(appContext));
        }
        if (watchVibrationToggle != null && watchVibrationToggle.isChecked() != PhoneBatteryFullAlert.isWatchVibrationEnabled(appContext)) {
            watchVibrationToggle.setChecked(PhoneBatteryFullAlert.isWatchVibrationEnabled(appContext));
        }
        if (phoneProtectionToggle != null) {
            boolean monitorPhoneEnabled = state.monitorPhoneEnabled;
            if (phoneProtectionToggle.isChecked() != monitorPhoneEnabled) {
                phoneProtectionToggle.setChecked(monitorPhoneEnabled);
            }
        }
        if (watchProtectionToggle != null) {
            boolean monitorWatchEnabled = state.monitorWatchEnabled;
            if (watchProtectionToggle.isChecked() != monitorWatchEnabled) {
                watchProtectionToggle.setChecked(monitorWatchEnabled);
            }
        }
        if (phoneOnPhoneToggle != null) {
            if (phoneOnPhoneToggle.isChecked() != state.alertPhoneOnPhoneEnabled) {
                phoneOnPhoneToggle.setChecked(state.alertPhoneOnPhoneEnabled);
            }
            phoneOnPhoneToggle.setEnabled(state.monitorPhoneEnabled);
        }
        if (phoneOnWatchToggle != null) {
            if (phoneOnWatchToggle.isChecked() != state.alertPhoneOnWatchEnabled) {
                phoneOnWatchToggle.setChecked(state.alertPhoneOnWatchEnabled);
            }
            phoneOnWatchToggle.setEnabled(state.monitorPhoneEnabled);
        }
        if (watchOnPhoneToggle != null) {
            if (watchOnPhoneToggle.isChecked() != state.alertWatchOnPhoneEnabled) {
                watchOnPhoneToggle.setChecked(state.alertWatchOnPhoneEnabled);
            }
            watchOnPhoneToggle.setEnabled(state.monitorWatchEnabled);
        }
        if (watchOnWatchToggle != null) {
            if (watchOnWatchToggle.isChecked() != state.alertWatchOnWatchEnabled) {
                watchOnWatchToggle.setChecked(state.alertWatchOnWatchEnabled);
            }
            watchOnWatchToggle.setEnabled(state.monitorWatchEnabled);
        }
        applyVisualEnabled(destinationPhoneRow, state.monitorPhoneEnabled);
        applyVisualEnabled(destinationWatchRow, state.monitorWatchEnabled);
        boolean phoneDestinationActive =
                (state.monitorPhoneEnabled && state.alertPhoneOnPhoneEnabled)
                        || (state.monitorWatchEnabled && state.alertWatchOnPhoneEnabled);
        boolean watchDestinationActive =
                (state.monitorPhoneEnabled && state.alertPhoneOnWatchEnabled)
                        || (state.monitorWatchEnabled && state.alertWatchOnWatchEnabled);
        applyVisualEnabled(phoneEffectsDestination, phoneDestinationActive);
        applyVisualEnabled(watchEffectsDestination, watchDestinationActive);
        applySwitchInteractiveState(phoneSoundToggle, phoneDestinationActive);
        applySwitchInteractiveState(phoneVibrationToggle, phoneDestinationActive);
        applySwitchInteractiveState(watchSoundToggle, watchDestinationActive);
        applySwitchInteractiveState(watchVibrationToggle, watchDestinationActive);
        applyRowInteractiveState(phoneSoundRow, phoneDestinationActive);
        applyRowInteractiveState(phoneVibrationRow, phoneDestinationActive);
        applyRowInteractiveState(watchSoundRow, watchDestinationActive);
        applyRowInteractiveState(watchVibrationRow, watchDestinationActive);
        updateCustomLimitControls(appContext);
        int protectionMessageRes = 0;
        if (!state.monitorPhoneEnabled && !state.monitorWatchEnabled) {
            protectionMessageRes = R.string.battery_alert_status_both_monitors_off;
        } else if (!state.monitorPhoneEnabled) {
            protectionMessageRes = R.string.battery_alert_status_phone_monitor_off;
        } else if (!state.monitorWatchEnabled) {
            protectionMessageRes = R.string.battery_alert_status_watch_monitor_off;
        } else if (!state.alertPhoneOnPhoneEnabled
                && !state.alertPhoneOnWatchEnabled
                && !state.alertWatchOnPhoneEnabled
                && !state.alertWatchOnWatchEnabled) {
            protectionMessageRes = R.string.battery_alert_status_all_destinations_off;
        }
        if (protectionStatusText != null) {
            protectionStatusText.setVisibility(protectionMessageRes != 0 ? View.VISIBLE : View.GONE);
            if (protectionMessageRes != 0) {
                protectionStatusText.setText(protectionMessageRes);
            }
        }
        if (reactivateProtectionButton != null) {
            reactivateProtectionButton.setVisibility(protectionMessageRes != 0 ? View.VISIBLE : View.GONE);
        }
        boolean permissionGranted = PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext);
        PhoneBatteryFullAlert.ChannelHealth channelHealth =
                PhoneBatteryFullAlert.readActiveChannelHealth(appContext);
        int problemMessageRes = 0;
        if (!permissionGranted
                || channelHealth == PhoneBatteryFullAlert.ChannelHealth.BLOCKED
                || channelHealth == PhoneBatteryFullAlert.ChannelHealth.MISSING) {
            problemMessageRes = R.string.battery_alert_notifications_blocked;
        } else if (channelHealth == PhoneBatteryFullAlert.ChannelHealth.SILENT) {
            problemMessageRes = R.string.battery_alert_notifications_silent;
        }
        boolean hasNotificationProblem = problemMessageRes != 0;
        if (notificationPermissionStatusText != null) {
            notificationPermissionStatusText.setVisibility(
                    hasNotificationProblem ? View.VISIBLE : View.GONE
            );
            if (hasNotificationProblem) {
                notificationPermissionStatusText.setText(problemMessageRes);
            }
        }
        if (notificationPermissionButton != null) {
            notificationPermissionButton.setVisibility(
                    hasNotificationProblem ? View.VISIBLE : View.GONE
            );
        }
    }

    private void reactivateProtection(Context appContext) {
        PhoneBatteryFullAlert.ProtectionState state =
                PhoneBatteryFullAlert.normalizeStoredState(appContext, "reactivate_button");
        if (!state.monitorPhoneEnabled) {
            PhoneBatteryFullAlert.setPhoneMonitorEnabled(appContext, true);
        }
        if (!state.monitorWatchEnabled) {
            PhoneBatteryFullAlert.setWatchMonitorEnabled(appContext, true);
        }
        if (!state.alertPhoneOnPhoneEnabled) {
            PhoneBatteryFullAlert.setAlertPhoneOnPhoneEnabled(appContext, true);
        }
        if (!state.alertPhoneOnWatchEnabled) {
            PhoneBatteryFullAlert.setAlertPhoneOnWatchEnabled(appContext, true);
        }
        if (!state.alertWatchOnPhoneEnabled) {
            PhoneBatteryFullAlert.setAlertWatchOnPhoneEnabled(appContext, true);
        }
        if (!state.alertWatchOnWatchEnabled) {
            PhoneBatteryFullAlert.setAlertWatchOnWatchEnabled(appContext, true);
        }
        PhoneBatteryProtectionSync.setLocalAndSync(appContext);
        PhoneBatteryFullAlert.rearmForUiTest(appContext, "ui_reactivate");
        requestNotificationPermission(true);
        refreshFullAlertUi();
    }

    private void bindCustomLimitSeekBars(Context appContext) {
        if (customHighSeek != null) {
            customHighSeek.setMax(2);
            customHighSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (customHighValueText != null) {
                        customHighValueText.setText(getString(
                                R.string.battery_alert_custom_high_format,
                                highLimitForIndex(progress)
                        ));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PhoneBatteryFullAlert.setLimitMode(appContext, PhoneBatteryFullAlert.LIMIT_MODE_PRESET);
                    PhoneBatteryFullAlert.setHighLimitPercent(
                            appContext,
                            highLimitForIndex(seekBar.getProgress())
                    );
                    PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                    refreshFullAlertUi();
                }
            });
        }
        if (customLowSeek != null) {
            customLowSeek.setMax(2);
            customLowSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (customLowValueText != null) {
                        customLowValueText.setText(getString(
                                R.string.battery_alert_custom_low_format,
                                lowLimitForIndex(progress)
                        ));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PhoneBatteryFullAlert.setLimitMode(appContext, PhoneBatteryFullAlert.LIMIT_MODE_PRESET);
                    PhoneBatteryFullAlert.setLowLimitPercent(
                            appContext,
                            lowLimitForIndex(seekBar.getProgress())
                    );
                    PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                    refreshFullAlertUi();
                }
            });
        }
    }

    private void updateCustomLimitControls(Context appContext) {
        int high = nearestOption(
                PhoneBatteryFullAlert.readHighLimitPercent(appContext),
                HIGH_LIMIT_OPTIONS,
                85
        );
        int low = nearestOption(
                PhoneBatteryFullAlert.readLowLimitPercent(appContext),
                LOW_LIMIT_OPTIONS,
                20
        );
        if (customHighSeek != null && customHighSeek.getProgress() != highIndexForLimit(high)) {
            customHighSeek.setProgress(highIndexForLimit(high));
        }
        if (customLowSeek != null && customLowSeek.getProgress() != lowIndexForLimit(low)) {
            customLowSeek.setProgress(lowIndexForLimit(low));
        }
        if (customHighValueText != null) {
            customHighValueText.setText(getString(R.string.battery_alert_custom_high_format, high));
        }
        if (customLowValueText != null) {
            customLowValueText.setText(getString(R.string.battery_alert_custom_low_format, low));
        }
    }

    private int highLimitForIndex(int index) {
        if (index <= 0) {
            return 80;
        }
        if (index >= 2) {
            return 90;
        }
        return 85;
    }

    private int lowLimitForIndex(int index) {
        if (index <= 0) {
            return 25;
        }
        if (index >= 2) {
            return 15;
        }
        return 20;
    }

    private int highIndexForLimit(int limit) {
        if (limit <= 80) {
            return 0;
        }
        if (limit >= 90) {
            return 2;
        }
        return 1;
    }

    private int lowIndexForLimit(int limit) {
        if (limit >= 25) {
            return 0;
        }
        if (limit <= 15) {
            return 2;
        }
        return 1;
    }

    private void requestNotificationPermission(boolean userInitiated) {
        if (!isAdded() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        Context appContext = requireContext().getApplicationContext();
        if ((!PhoneBatteryFullAlert.isAlertPhoneOnPhoneEnabled(appContext)
                && !PhoneBatteryFullAlert.isAlertWatchOnPhoneEnabled(appContext))
                || PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext)) {
            return;
        }
        SharedPreferences prefs = appContext.getSharedPreferences(
                PhoneBatterySender.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        boolean prompted = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false);
        if (userInitiated && prompted) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
            startActivity(intent);
            return;
        }
        if (!prompted || userInitiated) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, true).apply();
            Log.i(TAG, "notification_permission_request userInitiated=" + userInitiated);
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void toggleSwitch(@Nullable SwitchCompat toggle) {
        if (toggle != null && toggle.isEnabled()) {
            toggle.toggle();
        }
    }

    private void applyVisualEnabled(@Nullable View view, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setAlpha(enabled ? 1f : 0.5f);
    }

    private void applyRowInteractiveState(@Nullable View view, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
    }

    private void applySwitchInteractiveState(@Nullable SwitchCompat toggle, boolean enabled) {
        if (toggle == null) {
            return;
        }
        toggle.setEnabled(enabled);
    }

    private void openNotificationSettings() {
        if (!isAdded()) return;
        Context appContext = requireContext().getApplicationContext();
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext)
                && PhoneBatteryFullAlert.readActiveChannelHealth(appContext)
                != PhoneBatteryFullAlert.ChannelHealth.MISSING) {
            intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())
                    .putExtra(
                            Settings.EXTRA_CHANNEL_ID,
                            PhoneBatteryFullAlert.getActiveChannelId(appContext)
                    );
        } else {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        }
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            Log.w(TAG, "notification_settings_open_failed action=" + intent.getAction(), error);
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName())
            ));
        }
    }

    private int nearestOption(int value, int[] options, int tiePreference) {
        int nearest = options[0];
        int nearestDistance = Math.abs(value - nearest);
        for (int option : options) {
            int distance = Math.abs(value - option);
            if (distance < nearestDistance
                    || (distance == nearestDistance && option == tiePreference)) {
                nearest = option;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void applyDiagnosticsUi(
            boolean enabled,
            boolean connected,
            PhoneBatteryCompanionDiagnostics.CompanionStatus companionStatus,
            long lastSentAt,
            long freshnessWindowMs
    ) {
        if (!isAdded()) {
            return;
        }

        boolean watchConnected = connected
                && companionStatus == PhoneBatteryCompanionDiagnostics.CompanionStatus.CONFIRMED;
        String statusLabel = watchConnected
                ? getString(R.string.phone_battery_watch_connected)
                : getString(R.string.phone_battery_watch_unavailable);

        if (companionStatusText != null) {
            companionStatusText.setText(statusLabel);
        }
        if (connectionStatusText != null) {
            if (lastSentAt > 0L) {
                connectionStatusText.setText(getString(
                        R.string.phone_battery_last_sync_format,
                        formatLastSync(lastSentAt)
                ));
                connectionStatusText.setVisibility(View.VISIBLE);
            } else {
                connectionStatusText.setVisibility(View.GONE);
            }
        }
        if (lastSyncText != null) {
            lastSyncText.setVisibility(View.GONE);
        }
    }

    private String resolveCompanionLabel(PhoneBatteryCompanionDiagnostics.CompanionStatus companionStatus) {
        if (companionStatus == PhoneBatteryCompanionDiagnostics.CompanionStatus.CONFIRMED) {
            return getString(R.string.phone_battery_companion_detected);
        }
        if (companionStatus == PhoneBatteryCompanionDiagnostics.CompanionStatus.NOT_CONFIRMED_RECENTLY) {
            return getString(R.string.phone_battery_companion_not_confirmed_recently);
        }
        return getString(R.string.phone_battery_companion_not_detected);
    }

    private void applyWatchBatteryUi(WatchBatterySnapshot snapshot) {
        if (watchBatteryValueText != null) {
            watchBatteryValueText.setText(snapshot.hasData
                    ? getString(R.string.watch_battery_value_format, snapshot.level)
                    : getString(R.string.watch_battery_value_unknown));
        }
        if (watchBatteryDetailText != null) {
            watchBatteryDetailText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.watch_mock_charging_indicator)
            );
            String detail = resolveWatchBatteryDetail(snapshot);
            if (detail.isEmpty()) {
                watchBatteryDetailText.setText("");
                watchBatteryDetailText.setVisibility(View.GONE);
            } else {
                watchBatteryDetailText.setText(detail);
                watchBatteryDetailText.setVisibility(View.VISIBLE);
            }
        }
    }

    private String resolveWatchBatteryDetail(WatchBatterySnapshot snapshot) {
        if (!snapshot.hasData) {
            return "";
        }
        return snapshot.charging ? "\u26A1" : "";
    }

    private CharSequence formatLastSync(long lastSyncAt) {
        if (lastSyncAt <= 0L) {
            return getString(R.string.phone_battery_last_sync_never);
        }
        return DateFormat.getTimeFormat(requireContext()).format(new Date(lastSyncAt));
    }
}
