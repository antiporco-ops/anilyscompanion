package com.anilyss.watchcompanion;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.wear.remote.interactions.RemoteActivityHelper;

import com.anilyss.watchcompanion.battery.PhoneBatterySender;
import com.anilyss.watchcompanion.battery.PhoneBatteryAutoRefreshStore;
import com.anilyss.watchcompanion.battery.PhoneBatteryAutoRefreshSync;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PhoneBatterySettingsActivity extends AppCompatActivity {

    private static final String TAG = "AniLysBattery";
    private static final int OPEN_ON_PHONE_REASON_NO_WATCH = 1;
    private static final int OPEN_ON_PHONE_REASON_ERROR = 2;
    private static final String WEAR_APP_PACKAGE = "com.anilyss.watchcompanion";

    private ExecutorService bg;
    private RemoteActivityHelper remote;
    private RadioGroup autoRefreshGroup;
    private boolean suppressAutoRefreshSelection;
    private int previewMinutes = 10;
    private long previewUpdatedAt = 0L;
    private final DataClient.OnDataChangedListener settingsDataChangedListener = dataEvents -> {
        boolean shouldRefresh = false;
        try {
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED
                        && PhoneBatteryAutoRefreshSync.SETTINGS_PATH.equals(event.getDataItem().getUri().getPath())) {
                    shouldRefresh = true;
                    break;
                }
            }
        } finally {
            dataEvents.release();
        }
        if (shouldRefresh) {
            runOnUiThread(this::refreshSelectionFromStore);
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
        runOnUiThread(() -> {
            Log.d(TAG, "ui poke received minutes=" + payload.minutes + " updatedAt=" + payload.updatedAt);
            applyPreviewIfNewer(payload.minutes, payload.updatedAt);
        });
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_battery_settings);
        setTitle(R.string.phone_battery_title);

        bg = Executors.newSingleThreadExecutor();
        remote = new RemoteActivityHelper(this, bg);

        SwitchCompat featureSwitch = findViewById(R.id.switch_phone_battery_enabled);
        autoRefreshGroup = findViewById(R.id.radio_group_phone_battery_auto_refresh);
        MaterialButton openWearButton = findViewById(R.id.btn_open_anilys_wear_on_watch);
        MaterialButton backButton = findViewById(R.id.btn_back_phone_battery);

        boolean enabled = PhoneBatterySender.isFeatureEnabled(getApplicationContext());
        featureSwitch.setChecked(enabled);
        featureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PhoneBatterySender.setFeatureEnabled(getApplicationContext(), isChecked);
            Log.i(TAG, "Phone battery feature changed: enabled=" + isChecked);
            if (isChecked) {
                PhoneBatterySender.sendIfNeeded(getApplicationContext(), "manual");
            }
        });

        refreshSelectionFromStore();
        autoRefreshGroup.setOnCheckedChangeListener((group, selectedId) -> {
            if (suppressAutoRefreshSelection) {
                return;
            }
            int selectedMinutes = selectedId == R.id.radio_phone_battery_auto_refresh_5
                    ? 5
                    : selectedId == R.id.radio_phone_battery_auto_refresh_15
                    ? 15
                    : 10;
            int storeMinutes = PhoneBatteryAutoRefreshStore.readMinutes(getApplicationContext());
            long storeUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(getApplicationContext());
            int effectiveMinutes = previewUpdatedAt > storeUpdatedAt ? previewMinutes : storeMinutes;
            if (selectedMinutes == effectiveMinutes) {
                return;
            }
            Log.d(TAG, "local selection minutes=" + selectedMinutes);
            PhoneBatteryAutoRefreshSync.setLocalAndSync(getApplicationContext(), selectedMinutes);
            Log.i(TAG, "Phone battery auto-refresh interval changed: " + selectedMinutes + "m");
        });

        openWearButton.setOnClickListener(v -> openAniLysWearListingOnWatch());
        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getDataClient(this).addListener(settingsDataChangedListener);
        Wearable.getMessageClient(this).addListener(settingsUiPokeListener);
        refreshSelectionFromStore();
    }

    @Override
    protected void onPause() {
        Wearable.getMessageClient(this).removeListener(settingsUiPokeListener);
        Wearable.getDataClient(this).removeListener(settingsDataChangedListener);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bg != null) {
            bg.shutdownNow();
        }
    }

    private void refreshSelectionFromStore() {
        if (autoRefreshGroup == null) {
            return;
        }
        int currentMinutes = PhoneBatteryAutoRefreshStore.readMinutes(getApplicationContext());
        long storeUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(getApplicationContext());
        boolean usePreview = previewUpdatedAt > storeUpdatedAt;
        int effectiveMinutes = usePreview ? previewMinutes : currentMinutes;
        if (!usePreview && previewUpdatedAt <= storeUpdatedAt) {
            previewUpdatedAt = 0L;
        }
        int checkedId = effectiveMinutes == 5
                ? R.id.radio_phone_battery_auto_refresh_5
                : effectiveMinutes == 15
                ? R.id.radio_phone_battery_auto_refresh_15
                : R.id.radio_phone_battery_auto_refresh_10;
        if (autoRefreshGroup.getCheckedRadioButtonId() == checkedId) {
            return;
        }
        suppressAutoRefreshSelection = true;
        autoRefreshGroup.check(checkedId);
        suppressAutoRefreshSelection = false;
        if (usePreview) {
            Log.d(TAG, "ui preview applied minutes=" + effectiveMinutes + " updatedAt=" + previewUpdatedAt);
        } else {
            Log.d(TAG, "store state applied minutes=" + effectiveMinutes + " updatedAt=" + storeUpdatedAt);
        }
    }

    private void applyPreviewIfNewer(int minutes, long updatedAt) {
        long storeUpdatedAt = PhoneBatteryAutoRefreshStore.readUpdatedAt(getApplicationContext());
        long latestKnownUpdatedAt = Math.max(storeUpdatedAt, previewUpdatedAt);
        if (updatedAt <= latestKnownUpdatedAt) {
            Log.d(TAG, "stale ui poke ignored updatedAt=" + updatedAt + " latest=" + latestKnownUpdatedAt);
            return;
        }
        previewMinutes = PhoneBatteryAutoRefreshStore.sanitizeMinutes(minutes);
        previewUpdatedAt = updatedAt;
        refreshSelectionFromStore();
    }

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
        final Uri marketUri = Uri.parse("market://details?id=" + WEAR_APP_PACKAGE);
        final Uri webUri = Uri.parse("https://play.google.com/store/apps/details?id=" + WEAR_APP_PACKAGE);

        final Intent marketIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(marketUri);
        final Intent webIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(webUri);

        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> handleConnectedNodes(nodes, marketIntent, webIntent, webUri))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getConnectedNodes FAILURE, fallback to phone.", e);
                    showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_ERROR);
                });
    }

    private void handleConnectedNodes(
            List<Node> nodes,
            Intent marketIntent,
            Intent webIntent,
            Uri webUri
    ) {
        if (nodes == null || nodes.isEmpty()) {
            Log.i(TAG, "No watch connected; fallback to phone listing.");
            Toast.makeText(this, getString(R.string.toast_no_wearos), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, getString(R.string.toast_check_watch), Toast.LENGTH_SHORT).show();
        tryStartRemoteActivityWithTimeout(
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
        );
    }

    private void tryStartRemoteActivityWithTimeout(
            Intent intent,
            String nodeId,
            String opLogId,
            Runnable onSuccess,
            Runnable onFailure
    ) {
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
        } else {
            runOnUiThread(action);
        }
    }

    private void openWebOnPhone(Uri webUri) {
        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
    }

    private void showOpenOnPhoneDialog(Uri webUri, int reason) {
        runOnMainThread(() -> {
            if (webUri == null || isFinishing() || isDestroyed()) {
                return;
            }

            int messageRes = reason == OPEN_ON_PHONE_REASON_NO_WATCH
                    ? R.string.dialog_open_on_phone_no_watch_message
                    : R.string.dialog_open_on_phone_error_message;

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_open_on_phone_title)
                    .setMessage(messageRes)
                    .setPositiveButton(R.string.dialog_open_on_phone_positive, (dialog, which) -> openWebOnPhone(webUri))
                    .setNegativeButton(R.string.dialog_open_on_phone_negative, (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }
}
