package com.anilyss.watchcompanion;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.remote.interactions.RemoteActivityHelper;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.button.MaterialButton;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WFCompanion";

    private ExecutorService bg;
    private RemoteActivityHelper remote;

    private String[] labels;
    private String[] packages;
    private TypedArray tiles;
    private TypedArray previews;

    private int selectedIndex = -1;

    private ImageView selectedPreview;
    private TextView selectedTitle;
    private TextView watchStatus;
    private MaterialButton btnInstall;

    private WatchFaceTileAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views (ids do layout novo)
        selectedPreview = findViewById(R.id.image_view);
        selectedTitle   = findViewById(R.id.selected_title);
        watchStatus     = findViewById(R.id.watch_status);
        btnInstall      = findViewById(R.id.btn_install_on_watch);

        // Botão SEMPRE disponível (fallback no telefone se não houver relógio)
        btnInstall.setEnabled(true);

        // RemoteActivityHelper (Wear Remote Interactions)
        bg = Executors.newSingleThreadExecutor();
        remote = new RemoteActivityHelper(this, bg);
        logRemoteInteractionDiagnosticsVersions();

        // Arrays (grid)
        labels   = getResources().getStringArray(R.array.watchface_labels);
        packages = getResources().getStringArray(R.array.watchface_packages);
        tiles    = getResources().obtainTypedArray(R.array.watchface_tiles);
        previews = getResources().obtainTypedArray(R.array.watchface_previews);
        Log.i(TAG, "onCreate arrays loaded: labels=" + labels.length
                + ", packages=" + packages.length
                + ", tiles=" + tiles.length()
                + ", previews=" + previews.length());

        // Seleção padrão: primeira “válida” (package não vazio)
        selectedIndex = firstEnabledIndex();
        applySelection(selectedIndex);

        // RecyclerView grid
        RecyclerView rv = findViewById(R.id.watchface_grid);

        int span = 2;
        try {
            span = getResources().getInteger(R.integer.watchface_grid_span);
        } catch (Exception ignored) {}

        rv.setLayoutManager(new GridLayoutManager(this, span));

        adapter = new WatchFaceTileAdapter(labels, packages, tiles, previews, selectedIndex, pos -> {
            final String clickedPkg =
                    (pos >= 0 && pos < packages.length && packages[pos] != null) ? packages[pos].trim() : "";
            final String clickedLabel =
                    (pos >= 0 && pos < labels.length && labels[pos] != null) ? labels[pos] : "";
            Log.i(TAG, "tile clicked pos=" + pos
                    + ", label=\"" + clickedLabel + "\""
                    + ", pkg=\"" + clickedPkg + "\""
                    + ", len=" + clickedPkg.length());
            selectedIndex = pos;
            if (adapter != null) adapter.setSelected(pos);
            applySelection(pos);
        });

        rv.setAdapter(adapter);

        // Clique do botão: abre a selecionada
        btnInstall.setOnClickListener(v -> {
            Log.i(TAG, "Install on watch click: selectedIndex=" + selectedIndex
                    + ", watchPkg=\"" + (((selectedIndex >= 0 && selectedIndex < packages.length && packages[selectedIndex] != null) ? packages[selectedIndex].trim() : "")) + "\""
                    + ", len=" + (((selectedIndex >= 0 && selectedIndex < packages.length && packages[selectedIndex] != null) ? packages[selectedIndex].trim() : "").length())
                    + ", label=\"" + (((selectedIndex >= 0 && selectedIndex < labels.length && labels[selectedIndex] != null) ? labels[selectedIndex] : "")) + "\"");
            openListingOnWatchForSelected();
        });

        // Status (não bloqueia UI)
        refreshWatchStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bg != null) bg.shutdownNow();
        if (tiles != null) tiles.recycle();
        if (previews != null) previews.recycle();
    }

    private void refreshWatchStatus() {
        if (watchStatus != null) {
            watchStatus.setText(R.string.status_placeholder);
        }

        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    boolean has = nodes != null && !nodes.isEmpty();
                    if (watchStatus != null) {
                        watchStatus.setText(has
                                ? getString(R.string.status_detected)
                                : getString(R.string.status_not_connected));
                    }
                })
                .addOnFailureListener(e -> {
                    if (watchStatus != null) {
                        watchStatus.setText(getString(R.string.status_error));
                    }
                });
    }

    private int firstEnabledIndex() {
        int n = Math.min(labels.length, packages.length);
        for (int i = 0; i < n; i++) {
            String p = packages[i];
            if (p != null && p.trim().length() > 0) return i;
        }
        return -1;
    }

    private void applySelection(int index) {
        final String logLabel =
                (index >= 0 && index < labels.length && labels[index] != null) ? labels[index] : "";
        final String logPkg =
                (index >= 0 && index < packages.length && packages[index] != null) ? packages[index].trim() : "";
        Log.i(TAG, "applySelection index=" + index
                + ", selectedIndex=" + selectedIndex
                + ", label=\"" + logLabel + "\""
                + ", pkg=\"" + logPkg + "\""
                + ", len=" + logPkg.length());

        // Preview/label da selecionada
        if (index < 0 || index >= packages.length) {
            if (selectedPreview != null) selectedPreview.setImageResource(R.drawable.icon_placeholder);
            if (selectedTitle != null) selectedTitle.setText(getString(R.string.selected_none));
            return;
        }

        // Troca imagem de preview (quadrado de cima)
        int previewRes = (previews != null) ? previews.getResourceId(index, 0) : 0;
        if (previewRes != 0 && selectedPreview != null) {
            selectedPreview.setImageResource(previewRes);
        } else if (selectedPreview != null) {
            selectedPreview.setImageResource(R.drawable.icon_placeholder);
        }

        // Texto “Selected/Selecionada: X” (formatado por locale)
        String name = (index < labels.length && labels[index] != null) ? labels[index] : "";
        if (selectedTitle != null) {
            selectedTitle.setText(getString(R.string.selected_format, name));
        }
    }

    private void logRemoteInteractionDiagnosticsVersions() {
        Log.i(TAG, "Resolved deps: wear-remote-interactions="
                + BuildConfig.WEAR_REMOTE_INTERACTIONS_VERSION
                + ", play-services-wearable="
                + BuildConfig.PLAY_SERVICES_WEARABLE_VERSION);
        Log.i(TAG, "Runtime package versions: com.google.android.gms="
                + getInstalledPackageVersionSafe("com.google.android.gms")
                + ", com.google.android.wearable.app="
                + getInstalledPackageVersionSafe("com.google.android.wearable.app"));
    }

    private String getInstalledPackageVersionSafe(String packageName) {
        try {
            String versionName = getPackageManager().getPackageInfo(packageName, 0).versionName;
            return (versionName == null || versionName.trim().isEmpty()) ? "unknown" : versionName;
        } catch (Exception e) {
            return "not-installed";
        }
    }

    private Boolean queryRemoteActivityHelperAvailability(String nodeId) {
        for (Method method : RemoteActivityHelper.class.getMethods()) {
            if (!"isRemoteActivityHelperAvailable".equals(method.getName())) {
                continue;
            }

            Object[] args = buildAvailabilityMethodArgs(method.getParameterTypes(), nodeId);
            if (args == null) {
                Log.i(TAG, "Skipping unsupported isRemoteActivityHelperAvailable signature: " + method);
                continue;
            }

            try {
                Object target = Modifier.isStatic(method.getModifiers()) ? null : remote;
                Object rawResult = method.invoke(target, args);
                Boolean parsedResult = parseAvailabilityResult(rawResult);
                Log.i(TAG, "isRemoteActivityHelperAvailable invoked: signature=" + method
                        + ", rawType=" + (rawResult == null ? "null" : rawResult.getClass().getName())
                        + ", parsedAvailable=" + parsedResult);
                return parsedResult;
            } catch (Exception e) {
                Log.e(TAG, "Failed invoking isRemoteActivityHelperAvailable using " + method, e);
                return null;
            }
        }

        Log.i(TAG, "RemoteActivityHelper availability API not present in this dependency version.");
        return null;
    }

    private Object[] buildAvailabilityMethodArgs(Class<?>[] parameterTypes, String nodeId) {
        if (parameterTypes.length == 0) {
            return new Object[0];
        }
        if (parameterTypes.length == 1) {
            if (String.class.equals(parameterTypes[0])) {
                return new Object[] { nodeId };
            }
            if (Context.class.isAssignableFrom(parameterTypes[0])) {
                return new Object[] { this };
            }
        }
        if (parameterTypes.length == 2
                && Context.class.isAssignableFrom(parameterTypes[0])
                && String.class.equals(parameterTypes[1])) {
            return new Object[] { this, nodeId };
        }
        return null;
    }

    private Boolean parseAvailabilityResult(Object rawResult) {
        if (rawResult instanceof Boolean) {
            return (Boolean) rawResult;
        }
        if (rawResult instanceof Integer) {
            return ((Integer) rawResult) == RemoteActivityHelper.STATUS_AVAILABLE;
        }
        return null;
    }

    private void openListingOnWatchForSelected() {
        final String resolvedPkg =
                (selectedIndex >= 0 && selectedIndex < packages.length && packages[selectedIndex] != null)
                        ? packages[selectedIndex].trim() : "";
        Log.i(TAG, "openListingOnWatchForSelected ENTER selectedIndex=" + selectedIndex
                + ", resolvedPkg=\"" + resolvedPkg + "\""
                + ", len=" + resolvedPkg.length());

        if (selectedIndex < 0 || selectedIndex >= packages.length) {
            Log.i(TAG, "openListingOnWatchForSelected early return: invalid selectedIndex=" + selectedIndex);
            Toast.makeText(this, getString(R.string.toast_select_wf), Toast.LENGTH_SHORT).show();
            return;
        }

        String pkg = (packages[selectedIndex] == null) ? "" : packages[selectedIndex].trim();
        if (pkg.isEmpty()) {
            Log.i(TAG, "openListingOnWatchForSelected early return: empty watchPkg at selectedIndex=" + selectedIndex);
            Toast.makeText(this, getString(R.string.toast_coming_soon), Toast.LENGTH_SHORT).show();
            return;
        }

        openOnWatch(pkg);
    }

    private void openOnWatch(String watchPkg) {
        final String safeWatchPkg = (watchPkg == null) ? "" : watchPkg.trim();
        Log.i(TAG, "openOnWatch ENTER watchPkg=\"" + safeWatchPkg + "\" len=" + safeWatchPkg.length());
        if (safeWatchPkg.isEmpty()) {
            Log.i(TAG, "openOnWatch ABORT empty watchPkg");
            Toast.makeText(this, "Select a valid watch face", Toast.LENGTH_SHORT).show();
            return;
        }

        final Uri marketUri = Uri.parse("market://details?id=" + safeWatchPkg);
        final Uri webUri    = Uri.parse("https://play.google.com/store/apps/details?id=" + safeWatchPkg);

        final Intent marketIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(marketUri);

        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    Log.i(TAG, "getConnectedNodes success: size=" + (nodes == null ? 0 : nodes.size()));
                    if (nodes != null) {
                        for (int i = 0; i < nodes.size(); i++) {
                            Node n = nodes.get(i);
                            Log.i(TAG, "node[" + i + "]: id=" + n.getId() + ", nearby=" + n.isNearby());
                        }
                    }

                    if (nodes == null || nodes.isEmpty()) {
                        Log.i(TAG, "getConnectedNodes returned empty; watch is not connected/reachable now.");
                        Toast.makeText(this, getString(R.string.toast_no_wearos), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                        return;
                    }

                    Node target = null;
                    for (Node n : nodes) {
                        if (n.isNearby()) { target = n; break; }
                    }
                    if (target == null) target = nodes.get(0);

                    String nodeId = target.getId();
                    Boolean helperAvailable = queryRemoteActivityHelperAvailability(nodeId);
                    Log.i(TAG, "RemoteActivityHelper availability check: nodeId=" + nodeId
                            + ", watchPkg=" + safeWatchPkg
                            + ", available=" + helperAvailable);
                    if (Boolean.FALSE.equals(helperAvailable)) {
                        Log.i(TAG, "RemoteActivityHelper unavailable; fallback to phone web listing.");
                        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                        return;
                    }
                    if (helperAvailable == null) {
                        Log.i(TAG, "RemoteActivityHelper availability unknown; proceeding with startRemoteActivity.");
                    }

                    Toast.makeText(this, getString(R.string.toast_check_watch), Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Calling startRemoteActivity: nodeId=" + nodeId
                            + ", wfPackage=" + safeWatchPkg
                            + ", intent.data=" + marketIntent.getData());

                    // Só o market:// no relógio
                    final com.google.common.util.concurrent.ListenableFuture<Void> remoteOpenFuture =
                            remote.startRemoteActivity(marketIntent, nodeId);
                    final AtomicBoolean handled = new AtomicBoolean(false);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!remoteOpenFuture.isDone() && handled.compareAndSet(false, true)) {
                            Log.e(TAG, "TIMEOUT opening listing on watch, fallback to phone.");
                            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                        }
                    }, 5000);

                    remoteOpenFuture.addListener(() -> {
                        try {
                            remoteOpenFuture.get();
                            if (handled.compareAndSet(false, true)) {
                                Log.i(TAG, "startRemoteActivity SUCCESS");
                            }
                        } catch (Exception e) {
                            if (handled.compareAndSet(false, true)) {
                                Log.e(TAG, "startRemoteActivity FAILURE, fallback to phone.", e);
                                runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, webUri)));
                            }
                        }
                    }, bg);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getConnectedNodes FAILURE, fallback to phone.", e);
                    startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                });
    }
}
