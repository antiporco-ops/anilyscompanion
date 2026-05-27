package com.anilyss.watchcompanion;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.graphics.Rect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.remote.interactions.RemoteActivityHelper;

import com.anilyss.watchcompanion.settings.AppLanguageStore;
import com.bumptech.glide.Glide;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FacesFragment extends Fragment
        implements ClosedTestingBottomSheetFragment.InstallActionListener,
        WatchFaceDetailsBottomSheetFragment.DetailsActionListener {

    private static final String TAG = "WFCompanion";
    private static final String DEFAULT_COMING_SOON_GROUP_URL =
            "https://groups.google.com/g/anilyss-testers";
    private static final int OPEN_ON_PHONE_REASON_NO_WATCH = 1;
    private static final int OPEN_ON_PHONE_REASON_ERROR = 2;
    private static final int WATCH_CONNECTION_UNKNOWN = 0;
    private static final int WATCH_CONNECTION_DETECTED = 1;
    private static final int WATCH_CONNECTION_NOT_CONNECTED = 2;
    private static final int WATCH_CONNECTION_ERROR = 3;
    private static final int GRID_SPAN_MIN = 2;
    private static final int GRID_SPAN_MAX = 3;

    private ExecutorService bg;
    private RemoteActivityHelper remote;

    private CatalogRepository catalogRepository;
    @Nullable
    private CatalogModels.CatalogData seedCatalog;
    @Nullable
    private CatalogModels.CatalogData effectiveCatalog;

    private String[] currentLabels = new String[0];
    private String[] currentPackages = new String[0];
    private int[] currentTileResIds = new int[0];
    private int[] currentPreviewResIds = new int[0];
    private String[] currentTileUrls = new String[0];
    private String[] currentPreviewUrls = new String[0];

    private int selectedIndex = -1;
    @Nullable
    private String selectedCatalogId;
    @Nullable
    private String selectedCatalogPackage;

    @Nullable
    private ImageView selectedPreview;
    @Nullable
    private TextView selectedTitle;
    @Nullable
    private TextView watchStatus;
    @Nullable
    private TextView footerNote;
    @Nullable
    private MaterialButton btnInstall;
    @Nullable
    private MaterialButton btnLearnMore;
    private int watchConnectionState = WATCH_CONNECTION_UNKNOWN;

    @Nullable
    private WatchFaceTileAdapter watchFaceTileAdapter;
    @Nullable
    private RecyclerView watchFaceGrid;
    @Nullable
    private GridLayoutManager watchFaceGridLayoutManager;

    private final View.OnLayoutChangeListener gridLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateGridSpan();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_faces, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        selectedPreview = view.findViewById(R.id.image_view);
        selectedTitle = view.findViewById(R.id.selected_title);
        watchStatus = view.findViewById(R.id.watch_status);
        footerNote = view.findViewById(R.id.footer_note);
        btnInstall = view.findViewById(R.id.btn_install_on_watch);
        btnLearnMore = view.findViewById(R.id.btn_learn_more);

        NestedScrollView catalogScroll = view.findViewById(R.id.catalog_scroll);
        View fixedHeroContainer = view.findViewById(R.id.fixed_hero_container);
        applyTopSystemBarInset(fixedHeroContainer, catalogScroll);
        syncCatalogTopInset(catalogScroll, fixedHeroContainer);
        if (catalogScroll != null && fixedHeroContainer != null) {
            catalogScroll.post(() -> syncCatalogTopInset(catalogScroll, fixedHeroContainer));
            fixedHeroContainer.addOnLayoutChangeListener(
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                            syncCatalogTopInset(catalogScroll, fixedHeroContainer)
            );
        }

        Context context = requireContext();
        bg = Executors.newSingleThreadExecutor();
        remote = new RemoteActivityHelper(context, bg);
        logRemoteInteractionDiagnosticsVersions();

        catalogRepository = new CatalogRepository(context);
        final String languageCode = resolveCurrentLanguageCode();
        seedCatalog = catalogRepository.loadSeedCatalog(languageCode);
        CatalogModels.CatalogData cachedCatalog = catalogRepository.loadCachedCatalog(languageCode);

        effectiveCatalog = buildEffectiveCatalog(seedCatalog, cachedCatalog);
        if (effectiveCatalog == null) {
            effectiveCatalog = seedCatalog;
        }

        updateCurrentArraysFromCatalog(effectiveCatalog);

        Log.i(TAG, "onViewCreated arrays loaded: labels=" + currentLabels.length
                + ", packages=" + currentPackages.length
                + ", tiles=" + currentTileResIds.length
                + ", previews=" + currentPreviewResIds.length);

        selectedIndex = resolveSelectedIndex();
        if (selectedIndex < 0) {
            selectedIndex = firstEnabledIndex();
        }
        applySelection(selectedIndex);

        RecyclerView rv = view.findViewById(R.id.watchface_grid);
        watchFaceGrid = rv;
        watchFaceGridLayoutManager = new GridLayoutManager(context, GRID_SPAN_MIN);
        rv.setLayoutManager(watchFaceGridLayoutManager);
        rv.addOnLayoutChangeListener(gridLayoutChangeListener);
        rv.post(this::updateGridSpan);
        rv.setNestedScrollingEnabled(false);

        watchFaceTileAdapter = new WatchFaceTileAdapter(
                currentLabels,
                currentPackages,
                currentTileResIds,
                currentPreviewResIds,
                currentTileUrls,
                selectedIndex,
                pos -> {
                    final String clickedPkg =
                            (pos >= 0 && pos < currentPackages.length && currentPackages[pos] != null)
                                    ? currentPackages[pos].trim() : "";
                    final String clickedLabel =
                            (pos >= 0 && pos < currentLabels.length && currentLabels[pos] != null)
                                    ? currentLabels[pos] : "";
                    Log.i(TAG, "tile clicked pos=" + pos
                            + ", label=\"" + clickedLabel + "\""
                            + ", pkg=\"" + clickedPkg + "\""
                            + ", len=" + clickedPkg.length());
                    selectedIndex = pos;
                    rememberSelectionKey(getCatalogItemAt(pos));
                    if (watchFaceTileAdapter != null) {
                        watchFaceTileAdapter.setSelected(pos);
                    }
                    applySelection(pos);
                });

        rv.setAdapter(watchFaceTileAdapter);

        if (btnInstall != null) {
            btnInstall.setOnClickListener(v -> handlePrimaryActionClick());
        }
        if (btnLearnMore != null) {
            btnLearnMore.setOnClickListener(v -> showDetailsSheet());
            View learnMoreParent = btnLearnMore.getParent() instanceof View
                    ? (View) btnLearnMore.getParent()
                    : view;
            ensureMinTouchTarget(learnMoreParent, btnLearnMore, dpToPx(48));
        }

        catalogRepository.fetchRemoteCatalogAsync(languageCode, new CatalogRepository.CatalogCallback() {
            @Override
            public void onResult(@Nullable CatalogModels.CatalogData remoteCatalog) {
                if (!isAdded() || remoteCatalog == null) {
                    return;
                }

                CatalogModels.CatalogData merged = buildEffectiveCatalog(seedCatalog, remoteCatalog);
                if (merged == null) {
                    return;
                }
                effectiveCatalog = merged;

                updateCurrentArraysFromCatalog(effectiveCatalog);

                selectedIndex = resolveSelectedIndex();
                if (selectedIndex < 0 || selectedIndex >= currentPackages.length) {
                    selectedIndex = firstEnabledIndex();
                }

                if (watchFaceTileAdapter != null) {
                    watchFaceTileAdapter.updateData(
                            currentLabels,
                            currentTileResIds,
                            currentPreviewResIds,
                            currentTileUrls,
                            currentPackages
                    );
                    watchFaceTileAdapter.setSelected(selectedIndex);
                }
                applySelection(selectedIndex);
            }
        });

        refreshWatchStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshWatchStatus();
    }

    @Override
    public void onDestroyView() {
        if (watchFaceGrid != null) {
            watchFaceGrid.removeOnLayoutChangeListener(gridLayoutChangeListener);
        }
        watchFaceGrid = null;
        watchFaceGridLayoutManager = null;
        selectedPreview = null;
        selectedTitle = null;
        watchStatus = null;
        footerNote = null;
        btnInstall = null;
        btnLearnMore = null;
        watchFaceTileAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bg != null) {
            bg.shutdownNow();
            bg = null;
        }
    }

    private void refreshWatchStatus() {
        watchConnectionState = WATCH_CONNECTION_UNKNOWN;
        updateWatchStatus(getCatalogItemAt(selectedIndex));

        Context context = getContext();
        if (context == null) {
            return;
        }
        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (!isAdded()) return;
                    boolean has = nodes != null && !nodes.isEmpty();
                    watchConnectionState = has
                            ? WATCH_CONNECTION_DETECTED
                            : WATCH_CONNECTION_NOT_CONNECTED;
                    updateWatchStatus(getCatalogItemAt(selectedIndex));
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    watchConnectionState = WATCH_CONNECTION_ERROR;
                    updateWatchStatus(getCatalogItemAt(selectedIndex));
                });
    }

    private void updateWatchStatus(@Nullable CatalogModels.CatalogItem item) {
        if (watchStatus == null) {
            return;
        }

        if (item != null && item.getStatus() == CatalogModels.CatalogStatus.COMING_SOON) {
            watchStatus.setText(R.string.status_detected_coming_soon);
            return;
        }

        switch (watchConnectionState) {
            case WATCH_CONNECTION_DETECTED:
                if (item == null) {
                    watchStatus.setText(R.string.status_detected);
                    return;
                }
                switch (item.getStatus()) {
                    case CLOSED_TESTING:
                        watchStatus.setText(R.string.status_detected_closed_testing);
                        break;
                    case COMING_SOON:
                        watchStatus.setText(R.string.status_detected_coming_soon);
                        break;
                    case PRODUCTION:
                    default:
                        watchStatus.setText(R.string.status_detected);
                        break;
                }
                break;
            case WATCH_CONNECTION_NOT_CONNECTED:
                watchStatus.setText(formatStatusAsTwoLines(getString(R.string.status_not_connected)));
                break;
            case WATCH_CONNECTION_ERROR:
                watchStatus.setText(R.string.status_error);
                break;
            case WATCH_CONNECTION_UNKNOWN:
            default:
                watchStatus.setText(R.string.status_placeholder);
                break;
        }
    }

    @NonNull
    private String formatStatusAsTwoLines(@NonNull String statusText) {
        String normalized = statusText.trim().replace("\n", " ");
        int splitAt = normalized.indexOf(". ");
        if (splitAt <= 0) {
            return normalized;
        }
        return normalized.substring(0, splitAt + 1) + "\n" + normalized.substring(splitAt + 2);
    }

    private int firstEnabledIndex() {
        int n = Math.min(currentLabels.length, currentPackages.length);
        for (int i = 0; i < n; i++) {
            String p = currentPackages[i];
            if (p != null && p.trim().length() > 0) return i;
        }
        return -1;
    }

    private void applySelection(int index) {
        final String logLabel =
                (index >= 0 && index < currentLabels.length && currentLabels[index] != null)
                        ? currentLabels[index] : "";
        final String logPkg =
                (index >= 0 && index < currentPackages.length && currentPackages[index] != null)
                        ? currentPackages[index].trim() : "";
        Log.i(TAG, "applySelection index=" + index
                + ", selectedIndex=" + selectedIndex
                + ", label=\"" + logLabel + "\""
                + ", pkg=\"" + logPkg + "\""
                + ", len=" + logPkg.length());

        if (index < 0 || index >= currentPackages.length) {
            bindHeroMedia(null, 0, null);
            if (selectedTitle != null) {
                selectedTitle.setText(getString(R.string.selected_none));
            }
            if (btnLearnMore != null) {
                btnLearnMore.setVisibility(View.GONE);
            }
            updatePrimaryButton(null);
            updateWatchStatus(null);
            updateFooterNote(null);
            return;
        }

        int previewRes = (index < currentPreviewResIds.length) ? currentPreviewResIds[index] : 0;
        String previewUrl = (index < currentPreviewUrls.length) ? currentPreviewUrls[index] : null;
        CatalogModels.CatalogItem selectedItem = getCatalogItemAt(index);
        bindHeroMedia(selectedItem, previewRes, previewUrl);

        String name = (selectedItem != null && selectedItem.getTitle() != null)
                ? selectedItem.getTitle()
                : ((index < currentLabels.length && currentLabels[index] != null)
                        ? currentLabels[index] : "");
        if (selectedTitle != null) {
            selectedTitle.setText(getString(R.string.selected_format, name));
        }
        if (btnLearnMore != null) {
            btnLearnMore.setVisibility(
                    selectedItem != null && selectedItem.isLearnMoreEnabled()
                            ? View.VISIBLE
                            : View.GONE
            );
        }
        rememberSelectionKey(selectedItem);
        updatePrimaryButton(selectedItem);
        updateWatchStatus(selectedItem);
        updateFooterNote(selectedItem);
    }

    private String resolveCurrentLanguageCode() {
        String language = AppLanguageStore.readLanguageTag(requireContext().getApplicationContext());
        if (AppLanguageStore.LANGUAGE_SYSTEM.equals(language)) {
            Locale locale = getResources().getConfiguration().getLocales().isEmpty()
                    ? Locale.getDefault()
                    : getResources().getConfiguration().getLocales().get(0);
            language = locale != null ? locale.getLanguage() : AppLanguageStore.LANGUAGE_EN;
        }
        if (language == null || language.trim().isEmpty()) {
            return AppLanguageStore.LANGUAGE_EN;
        }
        return language;
    }

    @Nullable
    private CatalogModels.CatalogData buildEffectiveCatalog(
            @Nullable CatalogModels.CatalogData seed,
            @Nullable CatalogModels.CatalogData remote
    ) {
        if (remote == null || remote.getItems().isEmpty()) {
            return seed;
        }

        List<CatalogModels.CatalogItem> seedItems =
                (seed != null) ? seed.getItems() : null;
        List<CatalogModels.CatalogItem> remoteItems = remote.getItems();

        Map<String, CatalogModels.CatalogItem> seedByPackage = new HashMap<>();
        if (seedItems != null) {
            for (CatalogModels.CatalogItem s : seedItems) {
                String pkg = s.getPackageName();
                if (pkg != null) {
                    seedByPackage.put(pkg, s);
                }
            }
        }

        List<CatalogModels.CatalogItem> merged = new ArrayList<>(remoteItems.size());

        for (CatalogModels.CatalogItem remoteItem : remoteItems) {
            String pkg = remoteItem.getPackageName();
            CatalogModels.CatalogItem seedItem =
                    (pkg != null) ? seedByPackage.get(pkg) : null;

            String mergedTitle = remoteItem.getTitle();
            if (mergedTitle == null || mergedTitle.trim().isEmpty()) {
                if (seedItem != null && seedItem.getTitle() != null) {
                    mergedTitle = seedItem.getTitle();
                } else {
                    mergedTitle = remoteItem.getId();
                }
            }

            Integer tileResId = (seedItem != null) ? seedItem.getTileResId() : null;
            Integer previewResId = (seedItem != null) ? seedItem.getPreviewResId() : null;

            CatalogModels.CatalogItem mergedItem = new CatalogModels.CatalogItem(
                    remoteItem.getId(),
                    remoteItem.getPackageName(),
                    mergedTitle,
                    remoteItem.getShortDescription(),
                    remoteItem.getIconUrl(),
                    remoteItem.getTileUrl(),
                    remoteItem.getPreviewUrl(),
                    tileResId,
                    previewResId,
                    remoteItem.getStatus(),
                    remoteItem.getTestingInfo(),
                    remoteItem.getComingSoonGroupUrl(),
                    remoteItem.getHeroImageUrl(),
                    remoteItem.getHeroAnimatedImageUrl(),
                    remoteItem.getDetailsSummary(),
                    remoteItem.getDetailsDescription(),
                    remoteItem.getDetailImageUrls(),
                    remoteItem.isLearnMoreEnabled(),
                    remoteItem.getPromotionInfo()
            );

            merged.add(mergedItem);
        }

        if (merged.isEmpty()) {
            return seed;
        }

        return new CatalogModels.CatalogData(merged);
    }

    private void updateCurrentArraysFromCatalog(@Nullable CatalogModels.CatalogData catalog) {
        if (catalog != null) {
            currentLabels = catalog.toLabelsArray();
            currentPackages = catalog.toPackagesArray();
            currentTileResIds = catalog.toTileResArray();
            currentPreviewResIds = catalog.toPreviewResArray();
            currentTileUrls = catalog.toTileUrlArray();
            currentPreviewUrls = catalog.toPreviewUrlArray();
            return;
        }

        currentLabels = new String[0];
        currentPackages = new String[0];
        currentTileResIds = new int[0];
        currentPreviewResIds = new int[0];
        currentTileUrls = new String[0];
        currentPreviewUrls = new String[0];
    }

    private void applyTopSystemBarInset(
            @Nullable View fixedHeroContainer,
            @Nullable NestedScrollView catalogScroll
    ) {
        if (fixedHeroContainer == null) {
            return;
        }
        final int initialPaddingLeft = fixedHeroContainer.getPaddingLeft();
        final int initialPaddingTop = fixedHeroContainer.getPaddingTop();
        final int initialPaddingRight = fixedHeroContainer.getPaddingRight();
        final int initialPaddingBottom = fixedHeroContainer.getPaddingBottom();
        final int[] location = new int[2];

        ViewCompat.setOnApplyWindowInsetsListener(fixedHeroContainer, (view, insets) -> {
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            view.getLocationInWindow(location);
            int overlapTop = Math.max(0, statusTop - location[1]);
            int targetPaddingTop = initialPaddingTop + overlapTop;
            if (view.getPaddingTop() != targetPaddingTop) {
                view.setPadding(
                        initialPaddingLeft,
                        targetPaddingTop,
                        initialPaddingRight,
                        initialPaddingBottom
                );
                syncCatalogTopInset(catalogScroll, view);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(fixedHeroContainer);
    }

    private void syncCatalogTopInset(
            @Nullable NestedScrollView catalogScroll,
            @Nullable View fixedHeroContainer
    ) {
        if (catalogScroll == null || fixedHeroContainer == null) {
            return;
        }
        int entryGapPx = fixedHeroContainer.getResources()
                .getDimensionPixelSize(R.dimen.faces_catalog_top_gap);
        int topInset = Math.max(0, fixedHeroContainer.getHeight() + entryGapPx);
        if (topInset <= 0 || catalogScroll.getPaddingTop() == topInset) {
            return;
        }
        catalogScroll.setPadding(
                catalogScroll.getPaddingLeft(),
                topInset,
                catalogScroll.getPaddingRight(),
                catalogScroll.getPaddingBottom()
        );
    }

    private void updateGridSpan() {
        if (watchFaceGrid == null || watchFaceGridLayoutManager == null || !isAdded()) {
            return;
        }

        int availableWidth = watchFaceGrid.getWidth()
                - watchFaceGrid.getPaddingLeft()
                - watchFaceGrid.getPaddingRight();
        if (availableWidth <= 0) {
            return;
        }

        int minTileWidth = getResources().getDimensionPixelSize(R.dimen.faces_grid_min_tile_width);
        int tileVisualGutter = getResources().getDimensionPixelSize(R.dimen.faces_grid_tile_visual_gutter);
        int conservativeMinTileWidth = minTileWidth + Math.max(0, tileVisualGutter);
        if (conservativeMinTileWidth <= 0) {
            return;
        }

        int computedSpan = availableWidth / conservativeMinTileWidth;
        int span = Math.max(GRID_SPAN_MIN, Math.min(GRID_SPAN_MAX, computedSpan));
        if (watchFaceGridLayoutManager.getSpanCount() != span) {
            watchFaceGridLayoutManager.setSpanCount(span);
        }
    }

    @Nullable
    private CatalogModels.CatalogItem getCatalogItemAt(int index) {
        if (effectiveCatalog == null) {
            return null;
        }
        List<CatalogModels.CatalogItem> items = effectiveCatalog.getItems();
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    private void rememberSelectionKey(@Nullable CatalogModels.CatalogItem item) {
        if (item == null) {
            selectedCatalogId = null;
            selectedCatalogPackage = null;
            return;
        }
        selectedCatalogId = item.getId();
        selectedCatalogPackage = item.getPackageName();
    }

    private int resolveSelectedIndex() {
        if (effectiveCatalog == null) {
            return -1;
        }

        List<CatalogModels.CatalogItem> items = effectiveCatalog.getItems();
        if (selectedCatalogId != null && !selectedCatalogId.trim().isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                if (selectedCatalogId.equals(items.get(i).getId())) {
                    return i;
                }
            }
        }
        if (selectedCatalogPackage != null && !selectedCatalogPackage.trim().isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                if (selectedCatalogPackage.equals(items.get(i).getPackageName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void bindHeroMedia(
            @Nullable CatalogModels.CatalogItem item,
            int fallbackPreviewResId,
            @Nullable String fallbackPreviewUrl
    ) {
        if (selectedPreview == null) {
            return;
        }

        int placeholderResId = fallbackPreviewResId != 0
                ? fallbackPreviewResId
                : R.drawable.icon_placeholder;
        String mediaUrl = resolveHomePreviewUrl(item, fallbackPreviewUrl);
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            String resolvedUrl = ImageCacheInvalidation.resolveLoadUrl(selectedPreview.getContext(), mediaUrl);
            Glide.with(selectedPreview.getContext())
                    .load(resolvedUrl)
                    .placeholder(placeholderResId)
                    .error(placeholderResId)
                    .into(selectedPreview);
            return;
        }

        if (fallbackPreviewResId != 0) {
            selectedPreview.setImageResource(fallbackPreviewResId);
        } else {
            selectedPreview.setImageResource(R.drawable.icon_placeholder);
        }
    }

    @Nullable
    private String resolveHomePreviewUrl(
            @Nullable CatalogModels.CatalogItem item,
            @Nullable String fallbackPreviewUrl
    ) {
        if (item != null) {
            String previewUrl = item.getPreviewUrl();
            if (previewUrl != null && !previewUrl.trim().isEmpty()) {
                return previewUrl;
            }
        }
        return fallbackPreviewUrl;
    }

    @Nullable
    private String resolveHeroMediaUrl(
            @Nullable CatalogModels.CatalogItem item,
            @Nullable String fallbackPreviewUrl
    ) {
        if (item == null) {
            return fallbackPreviewUrl;
        }

        String animatedUrl = item.getHeroAnimatedImageUrl();
        if (animatedUrl != null && !animatedUrl.trim().isEmpty()) {
            return animatedUrl;
        }
        String heroImageUrl = item.getHeroImageUrl();
        if (heroImageUrl != null && !heroImageUrl.trim().isEmpty()) {
            return heroImageUrl;
        }
        String previewUrl = item.getPreviewUrl();
        if (previewUrl != null && !previewUrl.trim().isEmpty()) {
            return previewUrl;
        }
        return fallbackPreviewUrl;
    }

    @NonNull
    private String resolveDetailsSummary(@Nullable CatalogModels.CatalogItem item) {
        if (item == null) {
            return getString(R.string.faces_details_summary_fallback);
        }

        String summary = item.getDetailsSummary();
        if (summary == null || summary.trim().isEmpty()) {
            summary = item.getShortDescription();
        }
        if (summary == null || summary.trim().isEmpty()) {
            summary = getString(R.string.faces_details_summary_fallback);
        }
        return summary;
    }

    @NonNull
    private String resolveDetailsDescription(@Nullable CatalogModels.CatalogItem item) {
        if (item == null) {
            return getString(R.string.faces_details_description_production);
        }

        String description = item.getDetailsDescription();
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }

        if (item.getStatus() == CatalogModels.CatalogStatus.CLOSED_TESTING) {
            CatalogModels.TestingInfo testingInfo = item.getTestingInfo();
            if (testingInfo != null
                    && testingInfo.getIntro() != null
                    && !testingInfo.getIntro().trim().isEmpty()) {
                return testingInfo.getIntro();
            }
            return getString(R.string.faces_details_description_closed_testing);
        }

        if (item.getStatus() == CatalogModels.CatalogStatus.COMING_SOON) {
            return getString(R.string.faces_details_description_coming_soon);
        }

        return getString(R.string.faces_details_description_production);
    }

    private ArrayList<String> buildDetailGalleryUrls(
            @Nullable CatalogModels.CatalogItem item,
            int index
    ) {
        if (item == null) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String url : item.getDetailImageUrls()) {
            if (url != null && !url.trim().isEmpty()) {
                urls.add(url.trim());
            }
        }
        if (!urls.isEmpty()) {
            return new ArrayList<>(urls);
        }

        String heroMediaUrl = resolveHeroMediaUrl(
                item,
                index >= 0 && index < currentPreviewUrls.length ? currentPreviewUrls[index] : null
        );
        String tileUrl = index >= 0 && index < currentTileUrls.length ? currentTileUrls[index] : null;
        String previewUrl = item.getPreviewUrl();
        if (previewUrl == null && index >= 0 && index < currentPreviewUrls.length) {
            previewUrl = currentPreviewUrls[index];
        }

        if (heroMediaUrl != null && !heroMediaUrl.trim().isEmpty()) {
            urls.add(heroMediaUrl.trim());
        }
        if (previewUrl != null && !previewUrl.trim().isEmpty()) {
            urls.add(previewUrl.trim());
        }
        if (tileUrl != null && !tileUrl.trim().isEmpty()) {
            urls.add(tileUrl.trim());
        }

        return new ArrayList<>(urls);
    }

    private int[] buildDetailGalleryResIds(int index) {
        List<Integer> resIds = new ArrayList<>(2);
        int previewResId = index >= 0 && index < currentPreviewResIds.length
                ? currentPreviewResIds[index]
                : 0;
        int tileResId = index >= 0 && index < currentTileResIds.length
                ? currentTileResIds[index]
                : 0;
        String previewUrl = index >= 0 && index < currentPreviewUrls.length
                ? currentPreviewUrls[index]
                : null;
        String tileUrl = index >= 0 && index < currentTileUrls.length
                ? currentTileUrls[index]
                : null;

        if (previewResId != 0 && (previewUrl == null || previewUrl.trim().isEmpty())) {
            resIds.add(previewResId);
        }
        if (tileResId != 0
                && tileResId != previewResId
                && (tileUrl == null || tileUrl.trim().isEmpty())) {
            resIds.add(tileResId);
        }

        int[] result = new int[resIds.size()];
        for (int i = 0; i < resIds.size(); i++) {
            result[i] = resIds.get(i);
        }
        return result;
    }

    private void updatePrimaryButton(@Nullable CatalogModels.CatalogItem item) {
        if (btnInstall == null) {
            return;
        }

        btnInstall.setText(R.string.cta_install_on_watch);
        btnInstall.setIconResource(R.drawable.watch_on);

        if (item == null) {
            btnInstall.setEnabled(false);
            btnInstall.setAlpha(1f);
            return;
        }

        btnInstall.setEnabled(true);
        btnInstall.setAlpha(1f);
    }

    private void updateFooterNote(@Nullable CatalogModels.CatalogItem item) {
        if (footerNote == null) {
            return;
        }

        if (item == null) {
            footerNote.setText(R.string.footer_note);
            return;
        }

        switch (item.getStatus()) {
            case CLOSED_TESTING:
                footerNote.setText(R.string.footer_note_closed_testing);
                break;
            case COMING_SOON:
                footerNote.setText(R.string.footer_note_coming_soon);
                break;
            case PRODUCTION:
            default:
                footerNote.setText(R.string.footer_note);
                break;
        }
    }

    private void handlePrimaryActionClick() {
        CatalogModels.CatalogItem selectedItem = getCatalogItemAt(selectedIndex);
        if (selectedItem == null) {
            openListingOnWatchForSelected();
            return;
        }

        switch (selectedItem.getStatus()) {
            case CLOSED_TESTING:
                showClosedTestingSheet(selectedItem);
                break;
            case COMING_SOON:
                showComingSoonSheet(selectedItem);
                break;
            case PRODUCTION:
            default:
                Log.i(TAG, "Install on watch click: selectedIndex=" + selectedIndex
                        + ", watchPkg=\"" + (((selectedIndex >= 0 && selectedIndex < currentPackages.length && currentPackages[selectedIndex] != null) ? currentPackages[selectedIndex].trim() : "")) + "\""
                        + ", len=" + (((selectedIndex >= 0 && selectedIndex < currentPackages.length && currentPackages[selectedIndex] != null) ? currentPackages[selectedIndex].trim() : "").length())
                        + ", label=\"" + (((selectedIndex >= 0 && selectedIndex < currentLabels.length && currentLabels[selectedIndex] != null) ? currentLabels[selectedIndex] : "")) + "\"");
                openListingOnWatchForSelected();
                break;
        }
    }

    private void showClosedTestingSheet(@NonNull CatalogModels.CatalogItem item) {
        if (!isAdded()) {
            return;
        }

        CatalogModels.TestingInfo testingInfo = item.getTestingInfo();
        ClosedTestingBottomSheetFragment sheet = ClosedTestingBottomSheetFragment.newInstance(
                item.getTitle(),
                item.getPreviewUrl(),
                item.getPreviewResId() != null ? item.getPreviewResId().intValue() : 0,
                testingInfo != null ? testingInfo.getIntro() : null,
                testingInfo != null ? testingInfo.getInstallUrl() : null,
                testingInfo != null ? testingInfo.getJoinGroupUrl() : null,
                testingInfo != null ? testingInfo.getOptInUrl() : null
        );
        sheet.show(getChildFragmentManager(), "closed_testing_sheet");
    }

    private void showComingSoonSheet(@NonNull CatalogModels.CatalogItem item) {
        if (!isAdded()) {
            return;
        }

        ComingSoonBottomSheetFragment sheet = ComingSoonBottomSheetFragment.newInstance(
                item.getTitle(),
                item.getPreviewUrl(),
                item.getPreviewResId() != null ? item.getPreviewResId().intValue() : 0,
                resolveComingSoonJoinGroupUrl(item)
        );
        sheet.show(getChildFragmentManager(), "coming_soon_sheet");
    }

    private void showDetailsSheet() {
        CatalogModels.CatalogItem item = getCatalogItemAt(selectedIndex);
        if (!isAdded() || item == null || !item.isLearnMoreEnabled()) {
            return;
        }

        int previewResId = item.getPreviewResId() != null ? item.getPreviewResId().intValue() : 0;
        if (previewResId == 0 && selectedIndex >= 0 && selectedIndex < currentPreviewResIds.length) {
            previewResId = currentPreviewResIds[selectedIndex];
        }

        WatchFaceDetailsBottomSheetFragment sheet = WatchFaceDetailsBottomSheetFragment.newInstance(
                item.getTitle(),
                resolveDetailsSummary(item),
                resolveDetailsDescription(item),
                resolveHeroMediaUrl(
                        item,
                        selectedIndex >= 0 && selectedIndex < currentPreviewUrls.length
                                ? currentPreviewUrls[selectedIndex]
                                : null
                ),
                previewResId,
                buildDetailGalleryUrls(item, selectedIndex),
                buildDetailGalleryResIds(selectedIndex),
                item.getPromotionInfo()
        );
        sheet.show(getChildFragmentManager(), "watchface_details_sheet");
    }

    @Override
    public void onInstallRequested(@Nullable String installUrl) {
        Uri installUri = sanitizeWatchWebUri(installUrl);
        if (installUri != null) {
            Log.i(TAG, "Closed testing install click: using installUrl=\"" + installUri + "\"");
            openWebUrlOnWatch(installUri);
            return;
        }

        if (installUrl != null && !installUrl.trim().isEmpty()) {
            Log.i(TAG, "Closed testing install click: invalid installUrl, falling back to package flow. raw=\""
                    + installUrl + "\"");
        }
        openListingOnWatchForSelected();
    }

    @Override
    public void onDetailsInstallRequested() {
        handlePrimaryActionClick();
    }

    @NonNull
    private String resolveComingSoonJoinGroupUrl(@NonNull CatalogModels.CatalogItem item) {
        Uri joinGroupUri = sanitizeWatchWebUri(item.getComingSoonGroupUrl());
        return joinGroupUri != null
                ? joinGroupUri.toString()
                : DEFAULT_COMING_SOON_GROUP_URL;
    }

    private void logRemoteInteractionDiagnosticsVersions() {
        Context context = getContext();
        if (context == null) return;
        Log.i(TAG, "Resolved deps: wear-remote-interactions="
                + BuildConfig.WEAR_REMOTE_INTERACTIONS_VERSION
                + ", play-services-wearable="
                + BuildConfig.PLAY_SERVICES_WEARABLE_VERSION);
        Log.i(TAG, "Runtime package versions: com.google.android.gms="
                + getInstalledPackageVersionSafe(context, "com.google.android.gms")
                + ", com.google.android.wearable.app="
                + getInstalledPackageVersionSafe(context, "com.google.android.wearable.app"));
    }

    private String getInstalledPackageVersionSafe(Context context, String packageName) {
        try {
            String versionName = context.getPackageManager().getPackageInfo(packageName, 0).versionName;
            return (versionName == null || versionName.trim().isEmpty()) ? "unknown" : versionName;
        } catch (Exception e) {
            return "not-installed";
        }
    }

    private void openListingOnWatchForSelected() {
        final String resolvedPkg =
                (selectedIndex >= 0
                        && selectedIndex < currentPackages.length
                        && currentPackages[selectedIndex] != null)
                        ? currentPackages[selectedIndex].trim() : "";
        Log.i(TAG, "openListingOnWatchForSelected ENTER selectedIndex=" + selectedIndex
                + ", resolvedPkg=\"" + resolvedPkg + "\""
                + ", len=" + resolvedPkg.length());

        Context context = getContext();
        if (context == null) return;

        if (selectedIndex < 0 || selectedIndex >= currentPackages.length) {
            Log.i(TAG, "openListingOnWatchForSelected early return: invalid selectedIndex=" + selectedIndex);
            Toast.makeText(context, getString(R.string.toast_select_wf), Toast.LENGTH_SHORT).show();
            return;
        }

        String pkg = (currentPackages[selectedIndex] == null) ? "" : currentPackages[selectedIndex].trim();
        if (pkg.isEmpty()) {
            Log.i(TAG, "openListingOnWatchForSelected early return: empty watchPkg at selectedIndex=" + selectedIndex);
            Toast.makeText(context, getString(R.string.toast_coming_soon), Toast.LENGTH_SHORT).show();
            return;
        }

        openOnWatch(pkg);
    }

    private void openOnWatch(String watchPkg) {
        final String safeWatchPkg = (watchPkg == null) ? "" : watchPkg.trim();
        if (safeWatchPkg.isEmpty()) {
            Context context = getContext();
            if (context == null) return;
            Log.i(TAG, "openOnWatch ABORT empty watchPkg");
            Toast.makeText(context, "Select a valid watch face", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri webUri = Uri.parse("https://play.google.com/store/apps/details?id=" + safeWatchPkg);
        openOnWatchInternal(safeWatchPkg, webUri, "production_package_based");
    }

    private void openWebUrlOnWatch(@NonNull Uri webUri) {
        openOnWatchInternal(null, webUri, "closed_testing_install_url");
    }

    private void openOnWatchInternal(
            @Nullable String watchPkg,
            @NonNull Uri webUri,
            @NonNull String launchPath
    ) {
        Context context = getContext();
        if (context == null || remote == null) return;

        final String safeWatchPkg = (watchPkg == null) ? "" : watchPkg.trim();
        final Intent webIntent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(webUri);

        Log.i(TAG, "openOnWatch ENTER path=" + launchPath
                + ", watchPkg=\"" + safeWatchPkg + "\""
                + ", remoteUri=\"" + webUri + "\"");

        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (!isAdded()) return;
                    Log.i(TAG, "getConnectedNodes success: size=" + (nodes == null ? 0 : nodes.size()));
                    if (nodes != null) {
                        for (int i = 0; i < nodes.size(); i++) {
                            Node n = nodes.get(i);
                            Log.i(TAG, "node[" + i + "]: id=" + n.getId() + ", nearby=" + n.isNearby());
                        }
                    }

                    if (nodes == null || nodes.isEmpty()) {
                        Log.i(TAG, "getConnectedNodes returned empty; watch is not connected/reachable now.");
                        Toast.makeText(requireContext(), getString(R.string.toast_no_wearos), Toast.LENGTH_SHORT).show();
                        showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_NO_WATCH);
                        return;
                    }

                    Node target = null;
                    for (Node n : nodes) {
                        if (n.isNearby()) {
                            target = n;
                            break;
                        }
                    }
                    if (target == null) target = nodes.get(0);

                    String nodeId = target.getId();
                    Toast.makeText(requireContext(), getString(R.string.toast_check_watch), Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Remote launch send prep: path=" + launchPath
                            + ", package=\"" + safeWatchPkg
                            + "\", nodeId=" + nodeId
                            + ", uri=\"" + webUri + "\"");
                    tryOpenWebOnWatchThenPhone(nodeId, safeWatchPkg, webIntent, webUri, launchPath);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getConnectedNodes FAILURE, fallback to phone. webUri=\"" + webUri + "\"", e);
                    showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_ERROR);
                });
    }

    private void tryOpenWebOnWatchThenPhone(
            String nodeId,
            String watchPkg,
            Intent webIntent,
            Uri webUri,
            String launchPath
    ) {
        Log.i(TAG, "Calling remote launch(web): path=" + launchPath
                + ", nodeId=" + nodeId
                + ", wfPackage=" + watchPkg
                + ", intent.data=" + webIntent.getData());
        startRemoteActivityWithPublicApi(
                webIntent,
                nodeId,
                launchPath,
                () -> Log.i(TAG, "Web listing opened on watch."),
                () -> {
                    Log.e(TAG, "Web attempt on watch failed; fallback to phone web listing. uri=\""
                            + webUri + "\"");
                    showOpenOnPhoneDialog(webUri, OPEN_ON_PHONE_REASON_ERROR);
                }
        );
    }

    private void startRemoteActivityWithPublicApi(
            Intent intent,
            String nodeId,
            String launchPath,
            Runnable onSuccess,
            Runnable onFailure
    ) {
        Context context = getContext();
        if (context == null || remote == null) {
            runOnMainThread(onFailure);
            return;
        }

        final Uri targetUri = intent.getData();
        final com.google.common.util.concurrent.ListenableFuture<Void> remoteOpenFuture;
        try {
            Log.i(TAG, "Remote launch START: path=" + launchPath
                    + ", nodeId=" + nodeId
                    + ", uri=\"" + targetUri + "\"");
            remoteOpenFuture = remote.startRemoteActivity(intent, nodeId);
        } catch (Exception e) {
            Log.e(TAG, "Remote launch send FAILURE: path=" + launchPath
                    + ", nodeId=" + nodeId
                    + ", uri=\"" + targetUri + "\"", e);
            runOnMainThread(onFailure);
            return;
        }

        remoteOpenFuture.addListener(() -> {
            try {
                remoteOpenFuture.get();
                Log.i(TAG, "Remote launch SUCCESS: path=" + launchPath
                        + ", nodeId=" + nodeId
                        + ", uri=\"" + targetUri + "\"");
                runOnMainThread(onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "Remote launch FAILURE: path=" + launchPath
                        + ", nodeId=" + nodeId
                        + ", uri=\"" + targetUri + "\"", e);
                runOnMainThread(onFailure);
            }
        }, bg);
    }

    private void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        if (isAdded()) {
            requireActivity().runOnUiThread(action);
        }
    }

    private int dpToPx(int dp) {
        Context context = getContext();
        if (context == null) {
            return dp;
        }
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private void ensureMinTouchTarget(@NonNull View parent, @NonNull View target, int minSizePx) {
        parent.post(() -> {
            if (!target.isShown()) {
                return;
            }
            Rect hitRect = new Rect();
            target.getHitRect(hitRect);
            int extraWidth = Math.max(0, minSizePx - hitRect.width());
            int extraHeight = Math.max(0, minSizePx - hitRect.height());
            hitRect.inset(-extraWidth / 2, -extraHeight / 2);
            parent.setTouchDelegate(new TouchDelegate(hitRect, target));
        });
    }

    @Nullable
    private Uri sanitizeWatchWebUri(@Nullable String url) {
        if (url == null) {
            return null;
        }

        String trimmedUrl = url.trim();
        if (trimmedUrl.isEmpty()) {
            return null;
        }

        Uri uri = Uri.parse(trimmedUrl).normalizeScheme();
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.trim().isEmpty()) {
            return null;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        return uri;
    }

    private void openWebOnPhone(Uri webUri) {
        Context context = getContext();
        if (context == null) return;
        startActivity(new Intent(Intent.ACTION_VIEW, webUri));
    }

    private void showOpenOnPhoneDialog(Uri webUri, int reason) {
        runOnMainThread(() -> {
            if (!isAdded() || webUri == null) {
                return;
            }
            if (requireActivity().isFinishing() || requireActivity().isDestroyed()) {
                Log.i(TAG, "Skipping open-on-phone dialog; activity is finishing/destroyed.");
                return;
            }

            int messageRes = reason == OPEN_ON_PHONE_REASON_NO_WATCH
                    ? R.string.dialog_open_on_phone_no_watch_message
                    : R.string.dialog_open_on_phone_error_message;

            Log.i(TAG, "Fallback to phone dialog: reason=" + reason
                    + ", webUri=\"" + webUri + "\"");

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_open_on_phone_title)
                    .setMessage(messageRes)
                    .setPositiveButton(R.string.dialog_open_on_phone_positive, (dialog, which) -> openWebOnPhone(webUri))
                    .setNegativeButton(R.string.dialog_open_on_phone_negative, (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }
}
