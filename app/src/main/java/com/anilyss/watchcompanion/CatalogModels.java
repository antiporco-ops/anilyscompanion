package com.anilyss.watchcompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CatalogModels {

    private CatalogModels() {
        // Utility holder for catalog model types.
    }

    public enum CatalogStatus {
        PRODUCTION("production"),
        CLOSED_TESTING("closed_testing"),
        COMING_SOON("coming_soon");

        private final String rawValue;

        CatalogStatus(String rawValue) {
            this.rawValue = rawValue;
        }

        public String getRawValue() {
            return rawValue;
        }

        public static CatalogStatus fromRaw(String rawValue) {
            if (rawValue == null) {
                return PRODUCTION;
            }
            String normalized = rawValue.trim().toLowerCase();
            for (CatalogStatus status : values()) {
                if (status.rawValue.equals(normalized)) {
                    return status;
                }
            }
            return PRODUCTION;
        }
    }

    public static final class TestingInfo {
        private final String intro;
        private final String joinGroupUrl;
        private final String optInUrl;
        private final String installUrl;

        public TestingInfo(
                String intro,
                String joinGroupUrl,
                String optInUrl,
                String installUrl
        ) {
            this.intro = intro;
            this.joinGroupUrl = joinGroupUrl;
            this.optInUrl = optInUrl;
            this.installUrl = installUrl;
        }

        public String getIntro() {
            return intro;
        }

        public String getJoinGroupUrl() {
            return joinGroupUrl;
        }

        public String getOptInUrl() {
            return optInUrl;
        }

        public String getInstallUrl() {
            return installUrl;
        }
    }

    public static final class PromotionInfo {
        private final boolean enabled;
        private final String title;
        private final String message;
        private final String code;
        private final String redeemUrl;
        private final String expiresText;
        private final String disclaimer;

        public PromotionInfo(
                boolean enabled,
                String title,
                String message,
                String code,
                String redeemUrl,
                String expiresText,
                String disclaimer
        ) {
            this.enabled = enabled;
            this.title = title;
            this.message = message;
            this.code = code;
            this.redeemUrl = redeemUrl;
            this.expiresText = expiresText;
            this.disclaimer = disclaimer;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }

        public String getRedeemUrl() {
            return redeemUrl;
        }

        public String getExpiresText() {
            return expiresText;
        }

        public String getDisclaimer() {
            return disclaimer;
        }
    }

    public static final class CatalogItem {
        private final String id;
        private final String packageName;
        private final String title;
        private final String shortDescription;
        private final String iconUrl;
        private final String tileUrl;
        private final String previewUrl;
        private final Integer tileResId;
        private final Integer previewResId;
        private final CatalogStatus status;
        private final TestingInfo testingInfo;
        private final String comingSoonGroupUrl;
        private final String heroImageUrl;
        private final String heroAnimatedImageUrl;
        private final String detailsSummary;
        private final String detailsDescription;
        private final List<String> detailImageUrls;
        private final boolean learnMoreEnabled;
        private final PromotionInfo promotionInfo;

        public CatalogItem(
                String id,
                String packageName,
                String title,
                String shortDescription,
                String iconUrl,
                String tileUrl,
                String previewUrl,
                Integer tileResId,
                Integer previewResId,
                CatalogStatus status,
                TestingInfo testingInfo,
                String comingSoonGroupUrl,
                String heroImageUrl,
                String heroAnimatedImageUrl,
                String detailsSummary,
                String detailsDescription,
                List<String> detailImageUrls,
                boolean learnMoreEnabled,
                PromotionInfo promotionInfo
        ) {
            this.id = Objects.requireNonNull(id, "id");
            this.packageName = Objects.requireNonNull(packageName, "packageName");
            this.title = Objects.requireNonNull(title, "title");
            this.shortDescription = shortDescription;
            this.iconUrl = iconUrl;
            this.tileUrl = tileUrl;
            this.previewUrl = previewUrl;
            this.tileResId = tileResId;
            this.previewResId = previewResId;
            this.status = Objects.requireNonNull(status, "status");
            this.testingInfo = testingInfo;
            this.comingSoonGroupUrl = comingSoonGroupUrl;
            this.heroImageUrl = heroImageUrl;
            this.heroAnimatedImageUrl = heroAnimatedImageUrl;
            this.detailsSummary = detailsSummary;
            this.detailsDescription = detailsDescription;
            List<String> safeDetailImageUrls =
                    detailImageUrls != null ? new ArrayList<>(detailImageUrls) : new ArrayList<>();
            this.detailImageUrls = Collections.unmodifiableList(safeDetailImageUrls);
            this.learnMoreEnabled = learnMoreEnabled;
            this.promotionInfo = promotionInfo;
        }

        public String getId() {
            return id;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getTitle() {
            return title;
        }

        public String getShortDescription() {
            return shortDescription;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getTileUrl() {
            return tileUrl;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        public Integer getTileResId() {
            return tileResId;
        }

        public Integer getPreviewResId() {
            return previewResId;
        }

        public CatalogStatus getStatus() {
            return status;
        }

        public TestingInfo getTestingInfo() {
            return testingInfo;
        }

        public String getComingSoonGroupUrl() {
            return comingSoonGroupUrl;
        }

        public String getHeroImageUrl() {
            return heroImageUrl;
        }

        public String getHeroAnimatedImageUrl() {
            return heroAnimatedImageUrl;
        }

        public String getDetailsSummary() {
            return detailsSummary;
        }

        public String getDetailsDescription() {
            return detailsDescription;
        }

        public List<String> getDetailImageUrls() {
            return detailImageUrls;
        }

        public boolean isLearnMoreEnabled() {
            return learnMoreEnabled;
        }

        public PromotionInfo getPromotionInfo() {
            return promotionInfo;
        }
    }

    public static final class CatalogData {
        private final List<CatalogItem> items;

        public CatalogData(List<CatalogItem> items) {
            Objects.requireNonNull(items, "items");
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }

        public List<CatalogItem> getItems() {
            return items;
        }

        public String[] toLabelsArray() {
            String[] labels = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                labels[i] = items.get(i).getTitle();
            }
            return labels;
        }

        public String[] toPackagesArray() {
            String[] packages = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                packages[i] = items.get(i).getPackageName();
            }
            return packages;
        }

        public int[] toTileResArray() {
            int[] tileResArray = new int[items.size()];
            for (int i = 0; i < items.size(); i++) {
                CatalogItem item = items.get(i);
                Integer tileRes = item.getTileResId();
                tileResArray[i] = (tileRes != null) ? tileRes : 0;
            }
            return tileResArray;
        }

        public int[] toPreviewResArray() {
            int[] previewResArray = new int[items.size()];
            for (int i = 0; i < items.size(); i++) {
                CatalogItem item = items.get(i);
                Integer previewRes = item.getPreviewResId();
                previewResArray[i] = (previewRes != null) ? previewRes : 0;
            }
            return previewResArray;
        }

        public String[] toTileUrlArray() {
            String[] urls = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                String url = items.get(i).getTileUrl();
                if (url != null) {
                    url = url.trim();
                    if (url.isEmpty()) {
                        url = null;
                    }
                }
                urls[i] = url;
            }
            return urls;
        }

        public String[] toPreviewUrlArray() {
            String[] urls = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                String url = items.get(i).getPreviewUrl();
                if (url != null) {
                    url = url.trim();
                    if (url.isEmpty()) {
                        url = null;
                    }
                }
                urls[i] = url;
            }
            return urls;
        }
    }
}
