package com.anilyss.watchcompanion.settings;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class WatchAppVersionSync {

    private static final String TAG = "WFCompanion";
    public static final String REQUEST_PATH = "/request_watch_app_version";
    public static final String RESPONSE_PATH = "/watch_app_version_response";

    private WatchAppVersionSync() {
    }

    public static void requestNow(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Wearable.getNodeClient(appContext).getConnectedNodes()
                .addOnSuccessListener(nodes -> sendRequestToNodes(appContext, nodes))
                .addOnFailureListener(error ->
                        Log.w(TAG, "watch version request failed to resolve nodes", error)
                );
    }

    private static void sendRequestToNodes(@NonNull Context context, List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        byte[] payload = "v1".getBytes(StandardCharsets.UTF_8);
        for (Node node : nodes) {
            Wearable.getMessageClient(context)
                    .sendMessage(node.getId(), REQUEST_PATH, payload)
                    .addOnFailureListener(error ->
                            Log.w(TAG, "watch version request failed for node=" + node.getId(), error)
                    );
        }
    }
}
