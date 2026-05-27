package com.anilyss.watchcompanion;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

public class MaintenanceBottomSheetFragment extends BottomSheetDialogFragment {

    public interface ActionListener {
        void onUpdateCatalogNow();

        void onClearCatalogCache();

        void onClearImageCache();

        void onReloadAppData();
    }

    @Nullable
    private ActionListener actionListener;

    public static MaintenanceBottomSheetFragment newInstance() {
        return new MaintenanceBottomSheetFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (parent instanceof ActionListener) {
            actionListener = (ActionListener) parent;
        } else if (context instanceof ActionListener) {
            actionListener = (ActionListener) context;
        }
    }

    @Override
    public void onDetach() {
        actionListener = null;
        super.onDetach();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.bottom_sheet_maintenance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnUpdateCatalog = view.findViewById(R.id.maintenance_update_catalog_now);
        MaterialButton btnClearCatalogCache = view.findViewById(R.id.maintenance_clear_catalog_cache);
        MaterialButton btnClearImageCache = view.findViewById(R.id.maintenance_clear_image_cache);
        MaterialButton btnReloadAppData = view.findViewById(R.id.maintenance_reload_app_data);

        btnUpdateCatalog.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onUpdateCatalogNow();
            }
        });
        btnClearCatalogCache.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onClearCatalogCache();
            }
        });
        btnClearImageCache.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onClearImageCache();
            }
        });
        btnReloadAppData.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onReloadAppData();
            }
        });
    }
}
