package com.anilyss.watchcompanion;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CatalogRepository {

    private static final String TAG = "CatalogRepository";
    private static final String CACHE_FILE_NAME = "watchfaces_cache.json";
    private static final String CATALOG_URL =
            "https://raw.githubusercontent.com/antiporco-ops/anilyscompanion/main/catalog/watchfaces.json";
    private static final int SORT_ORDER_FALLBACK = Integer.MAX_VALUE;

    private final Context appContext;

    public CatalogRepository(Context context) {
        this.appContext = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    public CatalogModels.CatalogData loadSeedCatalog(String languageCode) {
        // languageCode is intentionally unused here: Android resources already resolve localized strings.
        Resources res = appContext.getResources();
        TypedArray tiles = null;
        TypedArray previews = null;

        try {
            String[] labels = res.getStringArray(R.array.watchface_labels);
            String[] packages = res.getStringArray(R.array.watchface_packages);
            tiles = res.obtainTypedArray(R.array.watchface_tiles);
            previews = res.obtainTypedArray(R.array.watchface_previews);

            int itemCount = labels.length;
            if (itemCount != packages.length || itemCount != tiles.length() || itemCount != previews.length()) {
                throw new IllegalStateException(
                        "Mismatched watchface resource array lengths: labels=" + itemCount
                                + ", packages=" + packages.length
                                + ", tiles=" + tiles.length()
                                + ", previews=" + previews.length()
                );
            }

            List<CatalogModels.CatalogItem> items = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                String title = labels[i];
                String packageName = packages[i];
                int tileResId = tiles.getResourceId(i, 0);
                int previewResId = previews.getResourceId(i, 0);

                // seed uses packageName as id
                items.add(new CatalogModels.CatalogItem(
                        packageName,
                        packageName,
                        title,
                        null,
                        null,
                        null,
                        null,
                        Integer.valueOf(tileResId),
                        Integer.valueOf(previewResId),
                        CatalogModels.CatalogStatus.PRODUCTION,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                ));
            }

            return new CatalogModels.CatalogData(items);
        } finally {
            if (tiles != null) {
                tiles.recycle();
            }
            if (previews != null) {
                previews.recycle();
            }
        }
    }

    @Nullable
    public CatalogModels.CatalogData loadCachedCatalog(String languageCode) {
        File cacheFile = new File(appContext.getFilesDir(), CACHE_FILE_NAME);
        if (!cacheFile.exists()) {
            return null;
        }

        StringBuilder jsonBuilder = new StringBuilder();
        try (
                FileInputStream fis = new FileInputStream(cacheFile);
                InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr)
        ) {
            char[] buffer = new char[2048];
            int readCount;
            while ((readCount = reader.read(buffer)) != -1) {
                jsonBuilder.append(buffer, 0, readCount);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to read cache file", e);
            return null;
        }

        String jsonText = jsonBuilder.toString();
        try {
            JSONArray root = new JSONArray(jsonText);
            String requestedLanguage =
                    (languageCode == null || languageCode.trim().isEmpty()) ? "en" : languageCode;
            List<SortableCatalogItem> parsedItems = new ArrayList<>(root.length());

            for (int i = 0; i < root.length(); i++) {
                JSONObject obj = root.getJSONObject(i);

                boolean enabled = obj.optBoolean("enabled", true);
                JSONObject flags = obj.optJSONObject("flags");
                boolean hidden = flags != null && flags.optBoolean("hidden", false);
                if (!enabled || hidden) {
                    continue;
                }

                String id = obj.getString("id");
                String packageName = obj.getString("package");

                String title = resolveLocalizedText(obj.optJSONObject("title"), requestedLanguage);
                if (title == null) {
                    title = id;
                }

                String shortDescription = resolveLocalizedText(obj.optJSONObject("shortDescription"), requestedLanguage);

                String iconUrl = null;
                String tileUrl = null;
                String previewUrl = null;
                String heroImageUrl = normalizeOptionalString(obj.optString("heroImage", null));
                String heroAnimatedImageUrl =
                        normalizeOptionalString(obj.optString("heroAnimatedImage", null));
                if (heroAnimatedImageUrl == null) {
                    heroAnimatedImageUrl = normalizeOptionalString(obj.optString("heroMedia", null));
                }
                JSONObject imagesObj = obj.optJSONObject("images");
                if (imagesObj != null) {
                    iconUrl = normalizeOptionalString(imagesObj.optString("icon", null));
                    tileUrl = normalizeOptionalString(imagesObj.optString("tile", null));
                    previewUrl = normalizeOptionalString(imagesObj.optString("preview", null));
                    if (heroImageUrl == null) {
                        heroImageUrl = normalizeOptionalString(imagesObj.optString("hero", null));
                    }
                    if (heroAnimatedImageUrl == null) {
                        heroAnimatedImageUrl =
                                normalizeOptionalString(imagesObj.optString("heroAnimated", null));
                    }
                    if (heroAnimatedImageUrl == null) {
                        heroAnimatedImageUrl =
                                normalizeOptionalString(imagesObj.optString("heroMedia", null));
                    }
                }

                String detailsSummary =
                        resolveLocalizedText(obj.optJSONObject("detailsSummary"), requestedLanguage);
                String detailsDescription =
                        resolveLocalizedText(obj.optJSONObject("detailsDescription"), requestedLanguage);
                if (detailsSummary == null) {
                    detailsSummary = normalizeOptionalString(obj.optString("detailsSummary", null));
                }
                if (detailsDescription == null) {
                    detailsDescription =
                            normalizeOptionalString(obj.optString("detailsDescription", null));
                }
                boolean learnMoreEnabled = obj.optBoolean("learnMoreEnabled", true);
                List<String> detailImageUrls =
                        parseStringArray(obj.optJSONArray("detailImages"));
                if (detailImageUrls.isEmpty() && imagesObj != null) {
                    detailImageUrls = parseStringArray(imagesObj.optJSONArray("detailImages"));
                }
                String comingSoonGroupUrl = null;
                JSONObject comingSoonObj = obj.optJSONObject("comingSoon");
                if (comingSoonObj != null) {
                    comingSoonGroupUrl =
                            normalizeOptionalString(comingSoonObj.optString("groupUrl", null));
                }

                CatalogModels.CatalogStatus status =
                        CatalogModels.CatalogStatus.fromRaw(obj.optString("status", null));
                CatalogModels.TestingInfo testingInfo =
                        parseTestingInfo(obj.optJSONObject("testing"), requestedLanguage);
                CatalogModels.PromotionInfo promotionInfo =
                        parsePromotionInfo(obj.optJSONObject("promotion"), requestedLanguage);

                parsedItems.add(new SortableCatalogItem(
                        new CatalogModels.CatalogItem(
                                id,
                                packageName,
                                title,
                                shortDescription,
                                iconUrl,
                                tileUrl,
                                previewUrl,
                                null,
                                null,
                                status,
                                testingInfo,
                                comingSoonGroupUrl,
                                heroImageUrl,
                                heroAnimatedImageUrl,
                                detailsSummary,
                                detailsDescription,
                                detailImageUrls,
                                learnMoreEnabled,
                                promotionInfo
                        ),
                        readSortOrder(obj),
                        i
                ));
            }

            if (parsedItems.isEmpty()) {
                return null;
            }
            Collections.sort(parsedItems, Comparator
                    .comparingInt((SortableCatalogItem item) -> item.sortOrder)
                    .thenComparingInt(item -> item.sourceIndex));
            List<CatalogModels.CatalogItem> items = new ArrayList<>(parsedItems.size());
            for (SortableCatalogItem parsedItem : parsedItems) {
                items.add(parsedItem.item);
            }
            return new CatalogModels.CatalogData(items);
        } catch (JSONException | RuntimeException e) {
            Log.w(TAG, "Failed to parse cached catalog JSON", e);
            return null;
        }
    }

    public void fetchRemoteCatalogAsync(final String languageCode, final CatalogCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                CatalogModels.CatalogData result = null;
                HttpURLConnection connection = null;

                try {
                    URL url = new URL(CATALOG_URL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setDoInput(true);

                    int responseCode = connection.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) {
                        StringBuilder jsonBuilder = new StringBuilder();
                        try (
                                InputStream is = new BufferedInputStream(connection.getInputStream());
                                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                                BufferedReader reader = new BufferedReader(isr)
                        ) {
                            char[] buffer = new char[2048];
                            int readCount;
                            while ((readCount = reader.read(buffer)) != -1) {
                                jsonBuilder.append(buffer, 0, readCount);
                            }
                        }

                        String jsonText = jsonBuilder.toString();
                        File cacheFile = new File(appContext.getFilesDir(), CACHE_FILE_NAME);
                        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                            byte[] bytes = jsonText.getBytes(StandardCharsets.UTF_8);
                            fos.write(bytes);
                            fos.flush();
                            result = loadCachedCatalog(languageCode);
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to write catalog cache file", e);
                        }
                    } else {
                        Log.w(TAG, "Remote catalog HTTP error: " + responseCode);
                    }
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, "Failed to fetch remote catalog", e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }

                if (callback != null) {
                    final CatalogModels.CatalogData callbackData = result;
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(callbackData);
                        }
                    });
                }
            }
        }).start();
    }

    public boolean clearCachedCatalog() {
        File cacheFile = new File(appContext.getFilesDir(), CACHE_FILE_NAME);
        if (!cacheFile.exists()) {
            return true;
        }
        try {
            return cacheFile.delete();
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to delete catalog cache file", e);
            return false;
        }
    }

    public interface CatalogCallback {
        void onResult(@Nullable CatalogModels.CatalogData data);
    }

    @Nullable
    private String resolveLocalizedText(@Nullable JSONObject localizedObj, String requestedLanguage) {
        if (localizedObj == null) {
            return null;
        }
        String language = resolveSupportedLanguage(requestedLanguage);
        String resolved = normalizeOptionalString(localizedObj.optString(language, null));
        if (resolved != null) {
            return resolved;
        }
        return normalizeOptionalString(localizedObj.optString("en", null));
    }

    @Nullable
    private String resolveLocalizedText(JSONObject obj, String key, String requestedLanguage) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) {
            return null;
        }
        Object raw = obj.opt(key);
        if (raw instanceof JSONObject) {
            return resolveLocalizedText((JSONObject) raw, requestedLanguage);
        }
        if (raw instanceof String) {
            return normalizeOptionalString((String) raw);
        }
        return null;
    }

    private String resolveSupportedLanguage(String requestedLanguage) {
        String normalized = normalizeOptionalString(requestedLanguage);
        if ("pt".equals(normalized) || "es".equals(normalized)) {
            return normalized;
        }
        return "en";
    }

    @Nullable
    private String normalizeOptionalString(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int readSortOrder(JSONObject obj) {
        if (!obj.has("sort") || obj.isNull("sort")) {
            return SORT_ORDER_FALLBACK;
        }
        return obj.optInt("sort", SORT_ORDER_FALLBACK);
    }

    private List<String> parseStringArray(@Nullable JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }

        List<String> items = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String value = normalizeOptionalString(array.optString(i, null));
            if (value != null) {
                items.add(value);
            }
        }
        return items;
    }

    @Nullable
    private CatalogModels.TestingInfo parseTestingInfo(
            @Nullable JSONObject testingObj,
            String requestedLanguage
    ) {
        if (testingObj == null) {
            return null;
        }

        String intro = resolveLocalizedText(testingObj.optJSONObject("intro"), requestedLanguage);
        String joinGroupUrl = normalizeOptionalString(testingObj.optString("joinGroupUrl", null));
        String optInUrl = normalizeOptionalString(testingObj.optString("optInUrl", null));
        String installUrl = normalizeOptionalString(testingObj.optString("installUrl", null));

        if (intro == null && joinGroupUrl == null && optInUrl == null && installUrl == null) {
            return null;
        }

        return new CatalogModels.TestingInfo(intro, joinGroupUrl, optInUrl, installUrl);
    }

    @Nullable
    private CatalogModels.PromotionInfo parsePromotionInfo(
            @Nullable JSONObject promotionObj,
            String requestedLanguage
    ) {
        if (promotionObj == null || !promotionObj.optBoolean("enabled", false)) {
            return null;
        }

        String code = normalizeOptionalString(promotionObj.optString("code", null));
        String redeemUrl = normalizeOptionalString(promotionObj.optString("redeemUrl", null));
        if (code == null && redeemUrl == null) {
            return null;
        }

        return new CatalogModels.PromotionInfo(
                true,
                resolveLocalizedText(promotionObj, "title", requestedLanguage),
                resolveLocalizedText(promotionObj, "message", requestedLanguage),
                code,
                redeemUrl,
                resolveLocalizedText(promotionObj, "expiresText", requestedLanguage),
                resolveLocalizedText(promotionObj, "disclaimer", requestedLanguage)
        );
    }

    private static final class SortableCatalogItem {
        private final CatalogModels.CatalogItem item;
        private final int sortOrder;
        private final int sourceIndex;

        private SortableCatalogItem(
                CatalogModels.CatalogItem item,
                int sortOrder,
                int sourceIndex
        ) {
            this.item = item;
            this.sortOrder = sortOrder;
            this.sourceIndex = sourceIndex;
        }
    }
}
