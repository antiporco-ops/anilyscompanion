package com.anilyss.watchcompanion;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

public class WatchFaceTileAdapter extends RecyclerView.Adapter<WatchFaceTileAdapter.VH> {
    private static final int STROKE_WIDTH_SELECTED_DP = 2;
    private static final int STROKE_WIDTH_UNSELECTED_DP = 1;
    private static final float ELEVATION_SELECTED_DP = 3f;
    private static final float ELEVATION_UNSELECTED_DP = 0f;
    private static final float LABEL_ALPHA_SELECTED = 1f;
    private static final float LABEL_ALPHA_UNSELECTED = 0.9f;

    public interface OnSelectListener {
        void onSelected(int position);
    }

    private String[] labels;
    private String[] packages;
    private int[] tileResIds;
    private int[] previewResIds;
    private String[] tileUrls;
    private int selected = -1;
    private final OnSelectListener listener;

    public WatchFaceTileAdapter(
            String[] labels,
            String[] packages,
            int[] tileResIds,
            int[] previewResIds,
            String[] tileUrls,
            int selected,
            OnSelectListener listener
    ) {
        this.labels = labels != null ? labels : new String[0];
        this.packages = packages != null ? packages : new String[0];
        this.tileResIds = tileResIds != null ? tileResIds : new int[0];
        this.previewResIds = previewResIds != null ? previewResIds : new int[0];
        this.tileUrls = tileUrls != null ? tileUrls : new String[0];
        this.selected = selected;
        this.listener = listener;
    }

    public void updateData(
            String[] labels,
            int[] tileResIds,
            int[] previewResIds,
            String[] tileUrls,
            String[] packages
    ) {
        this.labels = labels != null ? labels : new String[0];
        this.tileResIds = tileResIds != null ? tileResIds : new int[0];
        this.previewResIds = previewResIds != null ? previewResIds : new int[0];
        this.tileUrls = tileUrls != null ? tileUrls : new String[0];
        this.packages = packages != null ? packages : new String[0];
        int count = getItemCount();
        if (selected >= count) {
            selected = -1;
        }
        notifyDataSetChanged();
    }

    public void setSelected(int pos) {
        int count = getItemCount();
        int newPos = (pos >= 0 && pos < count) ? pos : -1;
        int old = selected;
        selected = newPos;
        if (old >= 0 && old < count) {
            notifyItemChanged(old);
        }
        if (newPos >= 0) {
            notifyItemChanged(newPos);
        }
    }

    public int getSelected() {
        return selected;
    }

    public boolean isEnabled(int pos) {
        return pos >= 0
                && pos < packages.length
                && packages[pos] != null
                && !packages[pos].trim().isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_watchface_tile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.label.setText(labels[pos]);
        int resId = (pos >= 0 && pos < tileResIds.length) ? tileResIds[pos] : 0;
        String url = (pos >= 0 && pos < tileUrls.length) ? tileUrls[pos] : null;

        int placeholderResId = (resId != 0) ? resId : R.drawable.icon_placeholder;

        if (url != null && !url.trim().isEmpty()) {
            String resolvedUrl = ImageCacheInvalidation.resolveLoadUrl(h.image.getContext(), url);
            Glide.with(h.image.getContext())
                    .load(resolvedUrl)
                    .placeholder(placeholderResId)
                    .error(placeholderResId)
                    .into(h.image);
        } else {
            if (resId != 0) {
                h.image.setImageResource(resId);
            } else {
                h.image.setImageResource(R.drawable.icon_placeholder);
            }
        }

        boolean enabled = isEnabled(pos);
        h.card.setEnabled(enabled);
        h.card.setAlpha(enabled ? 1f : 0.45f);
        applyCardVisualState(h, pos == selected);

        h.card.setOnClickListener(v -> {
            if (!enabled) {
                return;
            }
            if (listener != null) {
                listener.onSelected(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(
                Math.min(labels.length, packages.length),
                Math.min(tileResIds.length, previewResIds.length)
        );
    }

    private void applyCardVisualState(@NonNull VH holder, boolean isSelected) {
        int strokeColor = MaterialColors.getColor(
                holder.card,
                isSelected
                        ? com.google.android.material.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOutlineVariant
        );
        int backgroundColor = MaterialColors.getColor(
                holder.card,
                isSelected
                        ? com.google.android.material.R.attr.colorSurfaceContainerHighest
                        : com.google.android.material.R.attr.colorSurfaceContainerHigh
        );
        int labelColor = MaterialColors.getColor(
                holder.label,
                isSelected
                        ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorOnSurfaceVariant
        );
        holder.card.setStrokeColor(strokeColor);
        holder.card.setStrokeWidth(dpToPx(holder.card, isSelected ? STROKE_WIDTH_SELECTED_DP : STROKE_WIDTH_UNSELECTED_DP));
        holder.card.setCardElevation(dpToPxFloat(holder.card, isSelected ? ELEVATION_SELECTED_DP : ELEVATION_UNSELECTED_DP));
        holder.card.setCardBackgroundColor(backgroundColor);
        holder.label.setTextColor(labelColor);
        holder.label.setAlpha(isSelected ? LABEL_ALPHA_SELECTED : LABEL_ALPHA_UNSELECTED);
        holder.selectedIndicator.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        holder.selectedIndicator.setAlpha(isSelected ? 1f : 0f);
    }

    private static int dpToPx(@NonNull View view, int dp) {
        return Math.round(dpToPxFloat(view, dp));
    }

    private static float dpToPxFloat(@NonNull View view, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                view.getResources().getDisplayMetrics()
        );
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView image;
        TextView label;
        View selectedIndicator;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.tile_card);
            image = itemView.findViewById(R.id.tile_image);
            label = itemView.findViewById(R.id.tile_label);
            selectedIndicator = itemView.findViewById(R.id.tile_selected_indicator);
        }
    }
}
