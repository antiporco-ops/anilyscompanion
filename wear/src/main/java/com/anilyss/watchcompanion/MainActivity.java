package com.anilyss.watchcompanion;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements DataClient.OnDataChangedListener {

    private static final String TAG = "AniLysWearBattery";
    private static final String PHONE_BATTERY_PATH = "/phone_battery";
    private static final String REQUEST_PHONE_BATTERY_PATH = "/request_phone_battery";

    private TextView levelText;
    private TextView chargeStateText;
    private TextView updatedText;
    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        levelText = findViewById(R.id.phone_battery_level);
        chargeStateText = findViewById(R.id.phone_battery_charge_state);
        updatedText = findViewById(R.id.phone_battery_updated);
        refreshButton = findViewById(R.id.refresh_button);

        refreshButton.setOnClickListener(view -> requestPhoneBattery());
        renderSnapshot(PhoneBatteryStore.read(this));
        requestPhoneBattery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getDataClient(this).addListener(this);
        renderSnapshot(PhoneBatteryStore.read(this));
    }

    @Override
    protected void onPause() {
        Wearable.getDataClient(this).removeListener(this);
        super.onPause();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        try {
            boolean updated = false;
            for (DataEvent event : dataEvents) {
                if (event.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }
                String path = event.getDataItem().getUri().getPath();
                if (!PHONE_BATTERY_PATH.equals(path)) {
                    continue;
                }
                PhoneBatteryStore.Snapshot snapshot = readSnapshot(event);
                if (snapshot == null) {
                    continue;
                }
                PhoneBatteryStore.write(this, snapshot.level, snapshot.charging, snapshot.timestamp);
                updated = true;
            }
            if (updated) {
                runOnUiThread(() -> renderSnapshot(PhoneBatteryStore.read(this)));
            }
        } finally {
            dataEvents.release();
        }
    }

    private PhoneBatteryStore.Snapshot readSnapshot(DataEvent event) {
        int level = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getInt("level", -1);
        if (level < 0 || level > 100) {
            return null;
        }
        boolean charging = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getBoolean("charging", false);
        long timestamp = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getLong("ts", 0L);
        long safeTimestamp = timestamp > 0L ? timestamp : System.currentTimeMillis();
        return new PhoneBatteryStore.Snapshot(level, charging, safeTimestamp);
    }

    private void requestPhoneBattery() {
        Wearable.getNodeClient(this)
                .getConnectedNodes()
                .addOnSuccessListener(this::sendRequestToNodes)
                .addOnFailureListener(error -> Log.w(TAG, "Failed to resolve connected phone nodes", error));
    }

    private void sendRequestToNodes(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            Log.d(TAG, "No connected phone nodes for battery request");
            return;
        }
        for (Node node : nodes) {
            Wearable.getMessageClient(this)
                    .sendMessage(node.getId(), REQUEST_PHONE_BATTERY_PATH, new byte[0])
                    .addOnFailureListener(error ->
                            Log.w(TAG, "Failed to request phone battery from node=" + node.getId(), error));
        }
    }

    private void renderSnapshot(PhoneBatteryStore.Snapshot snapshot) {
        if (snapshot == null || !snapshot.hasData()) {
            levelText.setText(R.string.phone_battery_unknown_value);
            chargeStateText.setText(R.string.phone_battery_no_data);
            updatedText.setVisibility(View.GONE);
            return;
        }

        levelText.setText(getString(R.string.phone_battery_value_format, snapshot.level));
        chargeStateText.setText(snapshot.charging
                ? R.string.phone_battery_charging
                : R.string.phone_battery_on_battery);
        updatedText.setText(getString(
                R.string.phone_battery_updated_format,
                DateFormat.getTimeFormat(this).format(new Date(snapshot.timestamp))
        ));
        updatedText.setVisibility(View.VISIBLE);
    }
}
