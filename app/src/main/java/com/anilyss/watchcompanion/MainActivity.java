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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WFCompanion";
    private static final long REMOTE_ACTIVITY_TIMEOUT_MS = 12000L;

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

        // Arrays (grid)
        labels   = getResources().getStringArray(R.array.watchface_labels);
        packages = getResources().getStringArray(R.array.watchface_packages);
        tiles    = getResources().obtainTypedArray(R.array.watchface_tiles);
        previews = getResources().obtainTypedArray(R.array.watchface_previews);

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
            selectedIndex = pos;
            if (adapter != null) adapter.setSelected(pos);
            applySelection(pos);
        });

        rv.setAdapter(adapter);

        // Clique do botão: abre a selecionada
        btnInstall.setOnClickListener(v -> openListingOnWatchForSelected());

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

    /**
     * Helper: safely get a package name at the given index.
     */
    private String safeGetPackage(int index) {
        if (index >= 0 && index < packages.length && packages[index] != null) {
            return packages[index].trim();
        }
        return "";
    }

    /**
     * Helper: safely get a label string at the given index.
     */
    private String safeGetLabel(int index) {
        if (index >= 0 && index < labels.length && labels[index] != null) {
            return labels[index];
        }
        return "";
    }

    /**
     * Helper: safely get the first nearby node, or the first node if none are nearby.
     */
    private Node safeSelectNode(java.util.List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        for (Node n : nodes) {
            if (n.isNearby()) {
                return n;
            }
        }
        return nodes.get(0);
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

    private void openListingOnWatchForSelected() {
        String pkg = safeGetPackage(selectedIndex);
        if (pkg.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_select_wf), Toast.LENGTH_SHORT).show();
            return;
        }
        openOnWatch(pkg);
    }

    private void openOnWatch(String watchPkg) {
        if (watchPkg == null || watchPkg.trim().isEmpty()) {
            Toast.makeText(this, "Select a valid watch face", Toast.LENGTH_SHORT).show();
            return;
        }

        final String safeWatchPkg = watchPkg.trim();
        final Uri marketUri = Uri.parse("market://details?id=" + safeWatchPkg);
        final Uri webUri = Uri.parse("https://play.google.com/store/apps/details?id=" + safeWatchPkg);

        final Intent marketIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(marketUri);

        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    Node target = safeSelectNode(nodes);
                    if (target == null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "No connected nodes; opening on phone");
                        }
                        Toast.makeText(this, getString(R.string.toast_no_wearos), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                        return;
                    }

                    Toast.makeText(this, getString(R.string.toast_check_watch), Toast.LENGTH_SHORT).show();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Opening on watch: pkg=" + safeWatchPkg + ", nodeId=" + target.getId());
                    }

                    final com.google.common.util.concurrent.ListenableFuture<Void> remoteOpenFuture =
                            remote.startRemoteActivity(marketIntent, target.getId());
                    final AtomicBoolean handled = new AtomicBoolean(false);

                    // Timeout: if remote activity doesn't complete in time, fall back to phone
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!remoteOpenFuture.isDone() && handled.compareAndSet(false, true)) {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Remote activity timeout; opening on phone");
                            }
                            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                        }
                    }, REMOTE_ACTIVITY_TIMEOUT_MS);

                    // Listen for completion
                    remoteOpenFuture.addListener(() -> {
                        try {
                            remoteOpenFuture.get();
                            if (handled.compareAndSet(false, true)) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Remote activity succeeded");
                                }
                            }
                        } catch (Exception e) {
                            if (handled.compareAndSet(false, true)) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Remote activity failed; opening on phone", e);
                                }
                                runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, webUri)));
                            }
                        }
                    }, bg);
                })
                .addOnFailureListener(e -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Failed to get connected nodes; opening on phone", e);
                    }
                    startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                });
    }
}
