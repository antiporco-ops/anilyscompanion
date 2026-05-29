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
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
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
    private static final int[] LOW_LIMIT_OPTIONS = {15, 20, 25};

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
    private SwitchCompat fullAlertSwitch;
    @Nullable
    private SwitchCompat alertSoundSwitch;
    @Nullable
    private SwitchCompat alertVibrationSwitch;
    @Nullable
    private TextView fullAlertStatusText;
    @Nullable
    private TextView whyProtectBatteryTitleText;
    @Nullable
    private TextView whyProtectBatteryBodyText;
    private boolean suppressAutoRefreshSelection;
    private boolean suppressLimitSelection;
    private int previewMinutes = 10;
    private long previewUpdatedAt = 0L;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) {
                    return;
                }
                if (granted) {
                    PhoneBatteryFullAlert.evaluateCurrentState(
                            requireContext().getApplicationContext(),
                            "permission_granted"
                    );
                }
                refreshFullAlertUi();
            });

    private final DataClient.OnDataChangedListener settingsDataChangedListener = dataEvents -> {
        boolean shouldRefresh = false;
        try {
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED
                        && (PhoneBatteryAutoRefreshSync.SETTINGS_PATH.equals(event.getDataItem().getUri().getPath())
                        || WatchBatteryStore.DATA_PATH.equals(event.getDataItem().getUri().getPath()))) {
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
        lastSyncText = view.findViewById(R.id.phone_battery_last_sync);
        companionStatusText = view.findViewById(R.id.phone_battery_companion_status);
        connectionStatusText = view.findViewById(R.id.phone_battery_connection_status);
        watchBatteryValueText = view.findViewById(R.id.watch_battery_value);
        watchBatteryDetailText = view.findViewById(R.id.watch_battery_detail);
        fullAlertSwitch = view.findViewById(R.id.switch_phone_full_alert_enabled);
        alertSoundSwitch = view.findViewById(R.id.switch_battery_alert_sound);
        alertVibrationSwitch = view.findViewById(R.id.switch_battery_alert_vibration);
        fullAlertStatusText = view.findViewById(R.id.phone_full_alert_status);
        whyProtectBatteryTitleText = view.findViewById(R.id.battery_alert_why_title);
        whyProtectBatteryBodyText = view.findViewById(R.id.battery_alert_why_body);
        MaterialButton syncNowButton = view.findViewById(R.id.btn_phone_battery_sync_now);
        MaterialButton openWearButton = view.findViewById(R.id.btn_open_anilys_wear_on_watch);

        Context appContext = requireContext().getApplicationContext();
        normalizeLimitPreferences(appContext);
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

        if (fullAlertSwitch != null) {
            fullAlertSwitch.setChecked(PhoneBatteryFullAlert.isEnabled(appContext));
            fullAlertSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    PhoneBatteryFullAlert.setPhoneMonitorEnabled(appContext, true);
                }
                PhoneBatteryFullAlert.setEnabled(appContext, isChecked);
                if (isChecked) {
                    if (shouldRequestNotificationPermission(appContext)) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    } else {
                        PhoneBatteryFullAlert.evaluateCurrentState(appContext, "toggle_enabled");
                    }
                }
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (alertSoundSwitch != null) {
            alertSoundSwitch.setChecked(PhoneBatteryFullAlert.isSoundEnabled(appContext));
            alertSoundSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    PhoneBatteryFullAlert.setSoundEnabled(appContext, isChecked));
        }
        if (alertVibrationSwitch != null) {
            alertVibrationSwitch.setChecked(PhoneBatteryFullAlert.isVibrationEnabled(appContext));
            alertVibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    PhoneBatteryFullAlert.setVibrationEnabled(appContext, isChecked));
        }
        if (whyProtectBatteryTitleText != null && whyProtectBatteryBodyText != null) {
            whyProtectBatteryBodyText.setVisibility(View.GONE);
            whyProtectBatteryTitleText.setOnClickListener(v -> toggleWhyProtectBattery());
        }
        if (highLimitGroup != null) {
            highLimitGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (suppressLimitSelection) {
                    return;
                }
                PhoneBatteryFullAlert.setHighLimitPercent(appContext, highLimitForCheckedId(checkedId));
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        if (lowLimitGroup != null) {
            lowLimitGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (suppressLimitSelection) {
                    return;
                }
                PhoneBatteryFullAlert.setLowLimitPercent(appContext, lowLimitForCheckedId(checkedId));
                PhoneBatteryProtectionSync.setLocalAndSync(appContext);
                refreshFullAlertUi();
            });
        }
        bindAutoRefreshOption(autoRefresh5Button, 5);
        bindAutoRefreshOption(autoRefresh10Button, 10);
        bindAutoRefreshOption(autoRefresh15Button, 15);
        PhoneBatteryProtectionSync.publishCurrent(appContext);
        refreshSelectionFromStore();
        refreshDiagnostics();
        refreshFullAlertUi();

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
        fullAlertSwitch = null;
        alertSoundSwitch = null;
        alertVibrationSwitch = null;
        fullAlertStatusText = null;
        whyProtectBatteryTitleText = null;
        whyProtectBatteryBodyText = null;
        autoRefresh5Button = null;
        autoRefresh10Button = null;
        autoRefresh15Button = null;
        highLimitGroup = null;
        lowLimitGroup = null;
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
        SwitchCompat fullAlertToggle = fullAlertSwitch;
        SwitchCompat soundToggle = alertSoundSwitch;
        SwitchCompat vibrationToggle = alertVibrationSwitch;
        TextView fullAlertStatusView = fullAlertStatusText;
        RadioGroup highGroup = highLimitGroup;
        RadioGroup lowGroup = lowLimitGroup;
        if (fullAlertToggle == null || fullAlertStatusView == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        boolean enabled = PhoneBatteryFullAlert.isEnabled(appContext);
        boolean monitorEnabled = PhoneBatteryFullAlert.isPhoneMonitorEnabled(appContext);
        boolean permissionGranted = PhoneBatteryFullAlert.isNotificationPermissionGranted(appContext);
        if (enabled && !monitorEnabled) {
            PhoneBatteryFullAlert.setPhoneMonitorEnabled(appContext, true);
            monitorEnabled = true;
        }

        if (fullAlertToggle.isChecked() != enabled) {
            fullAlertToggle.setChecked(enabled);
        }
        if (soundToggle != null && soundToggle.isChecked() != PhoneBatteryFullAlert.isSoundEnabled(appContext)) {
            soundToggle.setChecked(PhoneBatteryFullAlert.isSoundEnabled(appContext));
        }
        if (vibrationToggle != null && vibrationToggle.isChecked() != PhoneBatteryFullAlert.isVibrationEnabled(appContext)) {
            vibrationToggle.setChecked(PhoneBatteryFullAlert.isVibrationEnabled(appContext));
        }
        suppressLimitSelection = true;
        if (highGroup != null) {
            highGroup.check(checkedIdForHighLimit(PhoneBatteryFullAlert.readHighLimitPercent(appContext)));
        }
        if (lowGroup != null) {
            lowGroup.check(checkedIdForLowLimit(PhoneBatteryFullAlert.readLowLimitPercent(appContext)));
        }
        suppressLimitSelection = false;
        if (enabled && !permissionGranted) {
            fullAlertStatusView.setText(R.string.phone_full_alert_status_permission_needed);
            fullAlertStatusView.setVisibility(View.VISIBLE);
        } else if (enabled && !monitorEnabled) {
            fullAlertStatusView.setText(R.string.battery_alert_status_monitor_off);
            fullAlertStatusView.setVisibility(View.VISIBLE);
        } else {
            fullAlertStatusView.setVisibility(View.GONE);
        }
    }

    private void normalizeLimitPreferences(Context appContext) {
        int high = PhoneBatteryFullAlert.readHighLimitPercent(appContext);
        int normalizedHigh = nearestOption(high, HIGH_LIMIT_OPTIONS, 85);
        int low = PhoneBatteryFullAlert.readLowLimitPercent(appContext);
        int normalizedLow = nearestOption(low, LOW_LIMIT_OPTIONS, 20);
        if (normalizedHigh != high || normalizedLow != low) {
            SharedPreferences prefs = appContext.getSharedPreferences(
                    PhoneBatterySender.PREFS_NAME,
                    Context.MODE_PRIVATE
            );
            prefs.edit()
                    .putInt(PhoneBatteryFullAlert.KEY_HIGH_LIMIT_PERCENT, normalizedHigh)
                    .putInt(PhoneBatteryFullAlert.KEY_LOW_LIMIT_PERCENT, normalizedLow)
                    .apply();
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

    private int checkedIdForHighLimit(int limit) {
        int normalized = nearestOption(limit, HIGH_LIMIT_OPTIONS, 85);
        if (normalized == 80) {
            return R.id.radio_battery_alert_high_80;
        }
        if (normalized == 90) {
            return R.id.radio_battery_alert_high_90;
        }
        return R.id.radio_battery_alert_high_85;
    }

    private int checkedIdForLowLimit(int limit) {
        int normalized = nearestOption(limit, LOW_LIMIT_OPTIONS, 20);
        if (normalized == 15) {
            return R.id.radio_battery_alert_low_15;
        }
        if (normalized == 25) {
            return R.id.radio_battery_alert_low_25;
        }
        return R.id.radio_battery_alert_low_20;
    }

    private int highLimitForCheckedId(int checkedId) {
        if (checkedId == R.id.radio_battery_alert_high_80) {
            return 80;
        }
        if (checkedId == R.id.radio_battery_alert_high_90) {
            return 90;
        }
        return 85;
    }

    private int lowLimitForCheckedId(int checkedId) {
        if (checkedId == R.id.radio_battery_alert_low_15) {
            return 15;
        }
        if (checkedId == R.id.radio_battery_alert_low_25) {
            return 25;
        }
        return 20;
    }

    private boolean shouldRequestNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PhoneBatteryFullAlert.isNotificationPermissionGranted(context);
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
            watchBatteryDetailText.setVisibility(View.GONE);
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
