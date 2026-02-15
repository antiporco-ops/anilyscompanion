package com.anilyss.watchcompanion;

import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

public class WatchFaceTileAdapter extends RecyclerView.Adapter<WatchFaceTileAdapter.VH> {

    public interface OnSelectListener {
        void onSelected(int position);
    }

    private final String[] labels;
    private final String[] packages;
    private final TypedArray tiles;
    private final TypedArray previews;
    private int selected = -1;
    private final OnSelectListener listener;

    public WatchFaceTileAdapter(String[] labels, String[] packages, TypedArray tiles, TypedArray previews, int selected, OnSelectListener listener) {
        this.labels = labels;
        this.packages = packages;
        this.tiles = tiles;
        this.previews = previews;
        this.selected = selected;
        this.listener = listener;
    }

    public void setSelected(int pos) {
        int old = selected;
        selected = pos;
        if (old >= 0) notifyItemChanged(old);
        notifyItemChanged(pos);
    }

    public int getSelected() { return selected; }

    public boolean isEnabled(int pos) {
        return packages[pos] != null && !packages[pos].trim().isEmpty();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_watchface_tile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.label.setText(labels[pos]);
        int resId = tiles.getResourceId(pos, 0);
        if (resId != 0) h.image.setImageResource(resId);

        boolean enabled = isEnabled(pos);
        h.card.setEnabled(enabled);
        h.card.setAlpha(enabled ? 1f : 0.45f);

        // “selecionado” = borda
        if (pos == selected) {
            h.card.setStrokeWidth(6);
        } else {
            h.card.setStrokeWidth(0);
        }

        h.card.setOnClickListener(v -> {
            if (!enabled) return;
            listener.onSelected(pos);
        });
    }

    @Override
    public int getItemCount() {
        return Math.min(
                Math.min(labels.length, packages.length),
                Math.min(tiles.length(), previews.length())
        );
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView image;
        TextView label;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.tile_card);
            image = itemView.findViewById(R.id.tile_image);
            label = itemView.findViewById(R.id.tile_label);
        }
    }
}
