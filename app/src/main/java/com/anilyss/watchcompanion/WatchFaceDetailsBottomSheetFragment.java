package com.anilyss.watchcompanion;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.lang.reflect.Method;

public class WatchFaceDetailsBottomSheetFragment extends BottomSheetDialogFragment {

    public interface DetailsActionListener {
        void onDetailsInstallRequested();
    }

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_SUMMARY = "arg_summary";
    private static final String ARG_DESCRIPTION = "arg_description";
    private static final String ARG_MEDIA_URL = "arg_media_url";
    private static final String ARG_MEDIA_RES_ID = "arg_media_res_id";
    private static final String ARG_GALLERY_URLS = "arg_gallery_urls";
    private static final String ARG_GALLERY_RES_IDS = "arg_gallery_res_ids";
    private static final String ARG_PROMO_TITLE = "arg_promo_title";
    private static final String ARG_PROMO_MESSAGE = "arg_promo_message";
    private static final String ARG_PROMO_CODE = "arg_promo_code";
    private static final String ARG_PROMO_REDEEM_URL = "arg_promo_redeem_url";
    private static final String ARG_PROMO_EXPIRES_TEXT = "arg_promo_expires_text";
    private static final String ARG_PROMO_DISCLAIMER = "arg_promo_disclaimer";

    @Nullable
    private DetailsActionListener detailsActionListener;

    public static WatchFaceDetailsBottomSheetFragment newInstance(
            @Nullable String title,
            @Nullable String summary,
            @Nullable String description,
            @Nullable String mediaUrl,
            int mediaResId,
            @Nullable ArrayList<String> galleryUrls,
            @Nullable int[] galleryResIds,
            @Nullable CatalogModels.PromotionInfo promotionInfo
    ) {
        WatchFaceDetailsBottomSheetFragment fragment = new WatchFaceDetailsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUMMARY, summary);
        args.putString(ARG_DESCRIPTION, description);
        args.putString(ARG_MEDIA_URL, mediaUrl);
        args.putInt(ARG_MEDIA_RES_ID, mediaResId);
        args.putStringArrayList(ARG_GALLERY_URLS, galleryUrls);
        args.putIntArray(ARG_GALLERY_RES_IDS, galleryResIds);
        if (promotionInfo != null && promotionInfo.isEnabled()) {
            args.putString(ARG_PROMO_TITLE, promotionInfo.getTitle());
            args.putString(ARG_PROMO_MESSAGE, promotionInfo.getMessage());
            args.putString(ARG_PROMO_CODE, promotionInfo.getCode());
            args.putString(ARG_PROMO_REDEEM_URL, promotionInfo.getRedeemUrl());
            args.putString(ARG_PROMO_EXPIRES_TEXT, promotionInfo.getExpiresText());
            args.putString(ARG_PROMO_DISCLAIMER, promotionInfo.getDisclaimer());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (parent instanceof DetailsActionListener) {
            detailsActionListener = (DetailsActionListener) parent;
        } else if (context instanceof DetailsActionListener) {
            detailsActionListener = (DetailsActionListener) context;
        }
    }

    @Override
    public void onDetach() {
        detailsActionListener = null;
        super.onDetach();
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.bottom_sheet_watchface_details, container, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        applyBottomSheetMaxWidth(dialog);
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String title = args.getString(ARG_TITLE);
        String summary = args.getString(ARG_SUMMARY);
        String description = args.getString(ARG_DESCRIPTION);
        String mediaUrl = args.getString(ARG_MEDIA_URL);
        int mediaResId = args.getInt(ARG_MEDIA_RES_ID, 0);
        ArrayList<String> galleryUrls = args.getStringArrayList(ARG_GALLERY_URLS);
        int[] galleryResIds = args.getIntArray(ARG_GALLERY_RES_IDS);

        ImageView previewView = view.findViewById(R.id.details_preview);
        TextView headerTitleView = view.findViewById(R.id.details_sheet_title);
        TextView titleView = view.findViewById(R.id.details_title);
        TextView summaryView = view.findViewById(R.id.details_summary);
        TextView descriptionView = view.findViewById(R.id.details_description);
        HorizontalScrollView galleryScroll = view.findViewById(R.id.details_gallery_scroll);
        LinearLayout galleryContainer = view.findViewById(R.id.details_gallery_container);
        MaterialButton installButton = view.findViewById(R.id.details_install);
        ImageButton closeButton = view.findViewById(R.id.details_close);
        MaterialCardView promotionCard = view.findViewById(R.id.details_promotion_card);
        TextView promotionTitle = view.findViewById(R.id.details_promotion_title);
        TextView promotionMessage = view.findViewById(R.id.details_promotion_message);
        LinearLayout promotionCodeRow = view.findViewById(R.id.details_promotion_code_row);
        TextView promotionCode = view.findViewById(R.id.details_promotion_code);
        TextView promotionExpires = view.findViewById(R.id.details_promotion_expires);
        TextView promotionDisclaimer = view.findViewById(R.id.details_promotion_disclaimer);
        MaterialButton promotionReveal = view.findViewById(R.id.details_promotion_reveal);
        MaterialButton promotionCopy = view.findViewById(R.id.details_promotion_copy);
        MaterialButton promotionRedeem = view.findViewById(R.id.details_promotion_redeem);

        int placeholderResId = mediaResId != 0 ? mediaResId : R.drawable.icon_placeholder;
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {
            String resolvedUrl = ImageCacheInvalidation.resolveLoadUrl(requireContext(), mediaUrl);
            Glide.with(this)
                    .load(resolvedUrl)
                    .placeholder(placeholderResId)
                    .error(placeholderResId)
                    .into(previewView);
        } else {
            previewView.setImageResource(placeholderResId);
        }

        String resolvedTitle = title != null && !title.trim().isEmpty() ? title : getString(R.string.app_name);
        headerTitleView.setText(resolvedTitle);
        titleView.setText(resolvedTitle);
        summaryView.setText(summary != null && !summary.trim().isEmpty()
                ? summary
                : getString(R.string.faces_details_summary_fallback));
        descriptionView.setText(description != null && !description.trim().isEmpty()
                ? description
                : getString(R.string.faces_details_description_production));
        closeButton.setOnClickListener(v -> dismissAllowingStateLoss());
        installButton.setOnClickListener(v -> {
            dismissAllowingStateLoss();
            if (detailsActionListener != null) {
                detailsActionListener.onDetailsInstallRequested();
            }
        });
        bindPromotion(
                promotionCard,
                promotionTitle,
                promotionMessage,
                promotionCodeRow,
                promotionCode,
                promotionExpires,
                promotionDisclaimer,
                promotionReveal,
                promotionCopy,
                promotionRedeem,
                args.getString(ARG_PROMO_TITLE),
                args.getString(ARG_PROMO_MESSAGE),
                args.getString(ARG_PROMO_CODE),
                args.getString(ARG_PROMO_REDEEM_URL),
                args.getString(ARG_PROMO_EXPIRES_TEXT),
                args.getString(ARG_PROMO_DISCLAIMER)
        );

        populateGallery(galleryContainer, galleryScroll, galleryUrls, galleryResIds, placeholderResId);
    }

    private void bindPromotion(
            @NonNull MaterialCardView card,
            @NonNull TextView titleView,
            @NonNull TextView messageView,
            @NonNull LinearLayout codeRow,
            @NonNull TextView codeView,
            @NonNull TextView expiresView,
            @NonNull TextView disclaimerView,
            @NonNull MaterialButton revealButton,
            @NonNull MaterialButton copyButton,
            @NonNull MaterialButton redeemButton,
            @Nullable String title,
            @Nullable String message,
            @Nullable String code,
            @Nullable String redeemUrl,
            @Nullable String expiresText,
            @Nullable String disclaimer
    ) {
        String normalizedCode = normalizeOptionalString(code);
        String normalizedRedeemUrl = normalizeOptionalString(redeemUrl);
        if (normalizedCode == null && normalizedRedeemUrl == null) {
            card.setVisibility(View.GONE);
            return;
        }

        String resolvedRedeemUrl = resolveRedeemUrl(normalizedCode, normalizedRedeemUrl);
        card.setVisibility(View.VISIBLE);
        titleView.setText(defaultIfBlank(title, getString(R.string.promotion_default_title)));
        messageView.setText(defaultIfBlank(message, getString(R.string.promotion_default_message)));
        codeView.setText(normalizedCode != null ? normalizedCode : "");
        codeRow.setVisibility(View.GONE);
        redeemButton.setVisibility(View.GONE);
        revealButton.setVisibility(View.VISIBLE);

        expiresView.setVisibility(View.GONE);
        disclaimerView.setText(defaultIfBlank(disclaimer, getString(R.string.promotion_default_disclaimer)));
        disclaimerView.setVisibility(View.GONE);

        revealButton.setOnClickListener(v -> {
            if (normalizedCode != null) {
                codeRow.setVisibility(View.VISIBLE);
            }
            if (resolvedRedeemUrl != null) {
                redeemButton.setVisibility(View.VISIBLE);
            }
            disclaimerView.setVisibility(View.VISIBLE);
            revealButton.setVisibility(View.GONE);
            card.post(() -> scrollPromotionIntoView(card));
        });
        copyButton.setOnClickListener(v -> copyPromotionCode(normalizedCode));
        redeemButton.setOnClickListener(v -> openRedeemUrl(resolvedRedeemUrl));
    }

    private void scrollPromotionIntoView(@NonNull View promotionCard) {
        View parent = promotionCard;
        while (parent.getParent() instanceof View) {
            parent = (View) parent.getParent();
            if (parent instanceof NestedScrollView) {
                ((NestedScrollView) parent).smoothScrollTo(0, promotionCard.getBottom());
                return;
            }
        }
    }

    private void bindOptionalText(@NonNull TextView view, @Nullable String value) {
        String normalized = normalizeOptionalString(value);
        if (normalized == null) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(normalized);
        view.setVisibility(View.VISIBLE);
    }

    @Nullable
    private String resolveRedeemUrl(@Nullable String code, @Nullable String redeemUrl) {
        if (redeemUrl != null) {
            return redeemUrl;
        }
        if (code == null) {
            return null;
        }
        return new Uri.Builder()
                .scheme("https")
                .authority("play.google.com")
                .path("redeem")
                .appendQueryParameter("code", code)
                .build()
                .toString();
    }

    private void copyPromotionCode(@Nullable String code) {
        if (code == null) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.promotion_clipboard_label),
                    code
            ));
            Toast.makeText(requireContext(), R.string.promotion_code_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void openRedeemUrl(@Nullable String redeemUrl) {
        Uri uri = sanitizeWebUri(redeemUrl);
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.promotion_redeem_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(requireContext(), R.string.promotion_redeem_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private Uri sanitizeWebUri(@Nullable String url) {
        String normalized = normalizeOptionalString(url);
        if (normalized == null) {
            return null;
        }
        Uri uri = Uri.parse(normalized).normalizeScheme();
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

    private String defaultIfBlank(@Nullable String value, @NonNull String fallback) {
        String normalized = normalizeOptionalString(value);
        return normalized != null ? normalized : fallback;
    }

    @Nullable
    private String normalizeOptionalString(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void populateGallery(
            @NonNull LinearLayout container,
            @NonNull HorizontalScrollView galleryScroll,
            @Nullable ArrayList<String> galleryUrls,
            @Nullable int[] galleryResIds,
            int placeholderResId
    ) {
        int availableWidth = galleryScroll.getWidth()
                - galleryScroll.getPaddingLeft()
                - galleryScroll.getPaddingRight();
        if (availableWidth <= 0) {
            galleryScroll.post(() ->
                    populateGallery(container, galleryScroll, galleryUrls, galleryResIds, placeholderResId));
            return;
        }
        container.removeAllViews();
        Context context = container.getContext();
        int spacing = context.getResources().getDimensionPixelSize(R.dimen.bottom_sheet_gallery_item_spacing);
        int imageSize = resolveGalleryItemSize(context, availableWidth);

        if (galleryUrls != null) {
            for (String url : galleryUrls) {
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                container.addView(createGalleryCard(context, imageSize, spacing, url.trim(), 0, placeholderResId));
            }
        }

        Set<Integer> uniqueResIds = new LinkedHashSet<>();
        if (galleryResIds != null) {
            for (int resId : galleryResIds) {
                if (resId != 0) {
                    uniqueResIds.add(resId);
                }
            }
        }
        for (Integer resId : uniqueResIds) {
            container.addView(createGalleryCard(context, imageSize, spacing, null, resId, placeholderResId));
        }

        if (container.getChildCount() == 0) {
            container.addView(createGalleryCard(context, imageSize, 0, null, placeholderResId, placeholderResId));
        }
    }

    @NonNull
    private View createGalleryCard(
            @NonNull Context context,
            int imageSize,
            int spacingEnd,
            @Nullable String imageUrl,
            int imageResId,
            int placeholderResId
    ) {
        MaterialCardView card = new MaterialCardView(context);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(imageSize, imageSize);
        cardParams.setMarginEnd(spacingEnd);
        card.setLayoutParams(cardParams);
        card.setRadius(imageSize / 2f);
        card.setCardElevation(0f);
        card.setStrokeWidth(dpToPx(context, 1));
        card.setStrokeColor(resolveThemeColor(context, com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(
                resolveThemeColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        );

        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setContentDescription(null);
        card.addView(imageView);

        if (imageUrl != null && !imageUrl.isEmpty()) {
            String resolvedUrl = ImageCacheInvalidation.resolveLoadUrl(context, imageUrl);
            Glide.with(this)
                    .load(resolvedUrl)
                    .placeholder(placeholderResId)
                    .error(placeholderResId)
                    .into(imageView);
        } else if (imageResId != 0) {
            imageView.setImageResource(imageResId);
        } else {
            imageView.setImageResource(placeholderResId);
        }

        return card;
    }

    private int dpToPx(@NonNull Context context, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        ));
    }

    private int resolveGalleryItemSize(@NonNull Context context, int availableWidth) {
        int target = Math.max(0, availableWidth) / 4;
        int min = context.getResources().getDimensionPixelSize(R.dimen.bottom_sheet_gallery_item_min_size);
        int max = context.getResources().getDimensionPixelSize(R.dimen.bottom_sheet_gallery_item_max_size);
        return Math.max(min, Math.min(max, target));
    }

    private void applyBottomSheetMaxWidth(@NonNull BottomSheetDialog dialog) {
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.bottom_sheet_behavior_max_width);
        try {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            Method setMaxWidth = behavior.getClass().getMethod("setMaxWidth", int.class);
            setMaxWidth.invoke(behavior, maxWidth);
        } catch (Exception ignored) {
            // setMaxWidth is not available on older Material versions.
        }
    }

    private int resolveThemeColor(@NonNull Context context, int attrResId) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, value, true);
        if (value.resourceId != 0) {
            return context.getColor(value.resourceId);
        }
        return value.data;
    }
}
