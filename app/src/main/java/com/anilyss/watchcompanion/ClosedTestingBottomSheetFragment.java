package com.anilyss.watchcompanion;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.lang.reflect.Method;

public class ClosedTestingBottomSheetFragment extends BottomSheetDialogFragment {

    public interface InstallActionListener {
        void onInstallRequested(@Nullable String installUrl);
    }

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_PREVIEW_URL = "arg_preview_url";
    private static final String ARG_PREVIEW_RES_ID = "arg_preview_res_id";
    private static final String ARG_INTRO = "arg_intro";
    private static final String ARG_INSTALL_URL = "arg_install_url";
    private static final String ARG_JOIN_GROUP_URL = "arg_join_group_url";
    private static final String ARG_OPT_IN_URL = "arg_opt_in_url";

    @Nullable
    private InstallActionListener installActionListener;

    public static ClosedTestingBottomSheetFragment newInstance(
            @Nullable String title,
            @Nullable String previewUrl,
            int previewResId,
            @Nullable String intro,
            @Nullable String installUrl,
            @Nullable String joinGroupUrl,
            @Nullable String optInUrl
    ) {
        ClosedTestingBottomSheetFragment fragment = new ClosedTestingBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_PREVIEW_URL, previewUrl);
        args.putInt(ARG_PREVIEW_RES_ID, previewResId);
        args.putString(ARG_INTRO, intro);
        args.putString(ARG_INSTALL_URL, installUrl);
        args.putString(ARG_JOIN_GROUP_URL, joinGroupUrl);
        args.putString(ARG_OPT_IN_URL, optInUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (parent instanceof InstallActionListener) {
            installActionListener = (InstallActionListener) parent;
        } else if (context instanceof InstallActionListener) {
            installActionListener = (InstallActionListener) context;
        }
    }

    @Override
    public void onDetach() {
        installActionListener = null;
        super.onDetach();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        applyBottomSheetMaxWidth(dialog);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.bottom_sheet_closed_testing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String title = args.getString(ARG_TITLE);
        String previewUrl = args.getString(ARG_PREVIEW_URL);
        int previewResId = args.getInt(ARG_PREVIEW_RES_ID, 0);
        String intro = args.getString(ARG_INTRO);
        String installUrl = args.getString(ARG_INSTALL_URL);

        ImageView previewView = view.findViewById(R.id.testing_preview);
        TextView titleView = view.findViewById(R.id.testing_title);
        TextView introView = view.findViewById(R.id.testing_intro);
        TextView joinGroupHintView = view.findViewById(R.id.testing_join_group_hint);
        TextView optInHintView = view.findViewById(R.id.testing_opt_in_hint);
        MaterialButton joinGroupButton = view.findViewById(R.id.testing_join_group);
        MaterialButton optInButton = view.findViewById(R.id.testing_opt_in);
        MaterialButton installButton = view.findViewById(R.id.testing_install);
        ImageButton closeButton = view.findViewById(R.id.testing_close);

        int placeholderResId = previewResId != 0 ? previewResId : R.drawable.icon_placeholder;
        if (previewUrl != null && !previewUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(previewUrl)
                    .placeholder(placeholderResId)
                    .error(placeholderResId)
                    .into(previewView);
        } else {
            previewView.setImageResource(placeholderResId);
        }

        titleView.setText(title != null && !title.trim().isEmpty() ? title : getString(R.string.app_name));
        introView.setText(
                intro != null && !intro.trim().isEmpty()
                        ? intro
                        : getString(R.string.testing_intro_fallback)
        );

        closeButton.setOnClickListener(v -> dismissAllowingStateLoss());
        bindExternalAction(joinGroupButton, args.getString(ARG_JOIN_GROUP_URL));
        bindExternalAction(optInButton, args.getString(ARG_OPT_IN_URL));
        joinGroupHintView.setVisibility(joinGroupButton.getVisibility());
        optInHintView.setVisibility(optInButton.getVisibility());
        bindInstallAction(installButton, installUrl);
    }

    private void bindExternalAction(@NonNull MaterialButton button, @Nullable String url) {
        Uri uri = sanitizeExternalUri(url);
        if (uri == null) {
            button.setVisibility(View.GONE);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> openExternalUrl(uri));
    }

    private void bindInstallAction(@NonNull MaterialButton button, @Nullable String installUrl) {
        if (installActionListener == null) {
            button.setVisibility(View.GONE);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> {
            installActionListener.onInstallRequested(installUrl);
            dismissAllowingStateLoss();
        });
    }

    @Nullable
    private Uri sanitizeExternalUri(@Nullable String url) {
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

    private void openExternalUrl(@NonNull Uri uri) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        if (tryLaunchCustomTab(context, uri)) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return;
        }

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private boolean tryLaunchCustomTab(@NonNull Context context, @NonNull Uri uri) {
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        try {
            customTabsIntent.launchUrl(context, uri);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void applyBottomSheetMaxWidth(@NonNull BottomSheetDialog dialog) {
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.bottom_sheet_behavior_max_width);
        try {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            Method setMaxWidth = behavior.getClass().getMethod("setMaxWidth", int.class);
            setMaxWidth.invoke(behavior, maxWidth);
        } catch (Exception ignored) {
            // setMaxWidth may not exist depending on Material Components version.
        }
    }
}
