package com.anilyss.watchcompanion;

import android.content.DialogInterface;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.anilyss.watchcompanion.settings.AppLanguageStore;
import com.anilyss.watchcompanion.settings.AppLanguageSync;
import com.anilyss.watchcompanion.settings.AppThemeStore;
import com.anilyss.watchcompanion.settings.WatchAppVersionStore;
import com.anilyss.watchcompanion.settings.WatchAppVersionSync;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    public interface MaintenancePanelController {
        boolean isMaintenanceSessionActive();

        void openMaintenancePanelForSession();

        void closeMaintenancePanelAndEndSession();
    }

    private static final String TAG = "WFCompanion";
    private static final int MAINTENANCE_TAP_TARGET = 7;
    private static final long MAINTENANCE_TAP_RESET_TIMEOUT_MS = 4_500L;

    private boolean suppressThemeSelection;
    private boolean suppressLanguageSelection;

    @Nullable
    private TextView phoneVersionValue;
    @Nullable
    private TextView watchVersionValue;
    @Nullable
    private MaterialButton debugButton;

    private boolean watchConnectionKnown;
    private boolean watchConnected;
    private int maintenanceTapCount;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetMaintenanceTapRunnable = () -> maintenanceTapCount = 0;

    private final SharedPreferences.OnSharedPreferenceChangeListener watchVersionListener =
            (prefs, key) -> {
                if (!WatchAppVersionStore.isVersionKey(key) || !isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(this::renderWatchVersion);
            };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RadioGroup themeGroup = view.findViewById(R.id.settings_theme_group);
        RadioGroup languageGroup = view.findViewById(R.id.settings_language_group);

        phoneVersionValue = view.findViewById(R.id.settings_phone_version_value);
        watchVersionValue = view.findViewById(R.id.settings_watch_version_value);
        debugButton = view.findViewById(R.id.settings_debug_button);
        bindDebugButton();

        View phoneVersionRow = view.findViewById(R.id.settings_phone_version_row);
        phoneVersionRow.setOnClickListener(v -> onMaintenanceTap());

        renderPhoneVersion();
        renderWatchVersion();

        String currentTheme = AppThemeStore.readThemeMode(requireContext().getApplicationContext());
        String currentLanguage = AppLanguageStore.readLanguageTag(requireContext().getApplicationContext());

        suppressThemeSelection = true;
        if (AppThemeStore.THEME_LIGHT.equals(currentTheme)) {
            themeGroup.check(R.id.settings_theme_light);
        } else if (AppThemeStore.THEME_DARK.equals(currentTheme)) {
            themeGroup.check(R.id.settings_theme_dark);
        } else {
            themeGroup.check(R.id.settings_theme_system);
        }
        suppressThemeSelection = false;

        suppressLanguageSelection = true;
        if (AppLanguageStore.LANGUAGE_EN.equals(currentLanguage)) {
            languageGroup.check(R.id.settings_language_en);
        } else if (AppLanguageStore.LANGUAGE_PT.equals(currentLanguage)) {
            languageGroup.check(R.id.settings_language_pt);
        } else if (AppLanguageStore.LANGUAGE_ES.equals(currentLanguage)) {
            languageGroup.check(R.id.settings_language_es);
        } else {
            languageGroup.check(R.id.settings_language_system);
        }
        suppressLanguageSelection = false;

        themeGroup.setOnCheckedChangeListener((group, checkedThemeId) -> {
            if (suppressThemeSelection) {
                return;
            }
            String selectedTheme = AppThemeStore.THEME_SYSTEM;
            if (checkedThemeId == R.id.settings_theme_light) {
                selectedTheme = AppThemeStore.THEME_LIGHT;
            } else if (checkedThemeId == R.id.settings_theme_dark) {
                selectedTheme = AppThemeStore.THEME_DARK;
            }

            boolean changed = AppThemeStore.writeThemeMode(requireContext().getApplicationContext(), selectedTheme);
            if (!changed) {
                return;
            }
            AppThemeStore.applyStoredTheme(requireContext().getApplicationContext());
        });

        languageGroup.setOnCheckedChangeListener((group, checkedLanguageId) -> {
            if (suppressLanguageSelection) {
                return;
            }
            String selectedLanguage = AppLanguageStore.LANGUAGE_SYSTEM;
            if (checkedLanguageId == R.id.settings_language_en) {
                selectedLanguage = AppLanguageStore.LANGUAGE_EN;
            } else if (checkedLanguageId == R.id.settings_language_pt) {
                selectedLanguage = AppLanguageStore.LANGUAGE_PT;
            } else if (checkedLanguageId == R.id.settings_language_es) {
                selectedLanguage = AppLanguageStore.LANGUAGE_ES;
            }

            String appliedLanguage = AppLanguageStore.readLanguageTag(requireContext().getApplicationContext());
            if (selectedLanguage.equals(appliedLanguage)) {
                return;
            }

            Log.d(TAG, "phone language selected tag=" + selectedLanguage);
            AppLanguageSync.setLocalAndSync(requireContext().getApplicationContext(), selectedLanguage);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Context appContext = requireContext().getApplicationContext();
        WatchAppVersionStore.seedIfMissing(appContext, "1.2.3", 23L);
        WatchAppVersionStore.addChangeListener(appContext, watchVersionListener);
        updateDebugButtonVisibility();
        refreshWatchConnectionState();
        WatchAppVersionSync.requestNow(appContext);
    }

    @Override
    public void onStop() {
        mainHandler.removeCallbacks(resetMaintenanceTapRunnable);
        Context context = getContext();
        if (context != null) {
            WatchAppVersionStore.removeChangeListener(context.getApplicationContext(), watchVersionListener);
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        phoneVersionValue = null;
        watchVersionValue = null;
        debugButton = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void renderPhoneVersion() {
        if (phoneVersionValue == null || !isAdded()) {
            return;
        }

        String versionName = "-";
        try {
            Context context = requireContext();
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            if (packageInfo.versionName != null && !packageInfo.versionName.trim().isEmpty()) {
                versionName = packageInfo.versionName.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read phone app version", e);
        }

        phoneVersionValue.setText(normalizeVersionName(versionName));
    }

    private void refreshWatchConnectionState() {
        Context context = getContext();
        if (context == null) {
            watchConnectionKnown = false;
            watchConnected = false;
            renderWatchVersion();
            return;
        }

        Wearable.getNodeClient(context)
                .getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (!isAdded()) {
                        return;
                    }
                    watchConnectionKnown = true;
                    watchConnected = nodes != null && !nodes.isEmpty();
                    renderWatchVersion();
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }
                    watchConnectionKnown = true;
                    watchConnected = false;
                    renderWatchVersion();
                });
    }

    private void renderWatchVersion() {
        if (watchVersionValue == null || !isAdded()) {
            return;
        }

        WatchAppVersionStore.Snapshot snapshot =
                WatchAppVersionStore.read(requireContext().getApplicationContext());
        if (!snapshot.hasValue()) {
            watchVersionValue.setText(R.string.settings_watch_version_not_detected);
            return;
        }

        watchVersionValue.setText(normalizeVersionName(snapshot.versionName));
    }

    @NonNull
    private String normalizeVersionName(@Nullable String versionName) {
        if (versionName == null) {
            return "-";
        }
        String normalized = versionName.trim();
        return normalized.isEmpty() ? "-" : normalized;
    }

    private void onMaintenanceTap() {
        maintenanceTapCount += 1;
        mainHandler.removeCallbacks(resetMaintenanceTapRunnable);
        if (maintenanceTapCount >= MAINTENANCE_TAP_TARGET) {
            maintenanceTapCount = 0;
            MaintenancePanelController controller = resolveMaintenancePanelController();
            if (controller != null && controller.isMaintenanceSessionActive()) {
                controller.openMaintenancePanelForSession();
                updateDebugButtonVisibility();
                return;
            }
            showPinDialog();
            return;
        }
        mainHandler.postDelayed(resetMaintenanceTapRunnable, MAINTENANCE_TAP_RESET_TIMEOUT_MS);
    }

    private void showPinDialog() {
        if (!isAdded()) {
            return;
        }

        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setHint(getString(R.string.maintenance_pin_hint));
        inputLayout.setPadding(24, 8, 24, 0);

        TextInputEditText input = new TextInputEditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());

        inputLayout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog pinDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.maintenance_pin_title)
                .setView(inputLayout)
                .setPositiveButton(android.R.string.ok, (dlg, which) -> {
                    String pin = input.getText() != null ? input.getText().toString() : "";
                    if (!isPinValid(pin)) {
                        Toast.makeText(requireContext(), R.string.maintenance_pin_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MaintenancePanelController controller = resolveMaintenancePanelController();
                    if (controller != null) {
                        controller.openMaintenancePanelForSession();
                        updateDebugButtonVisibility();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dlg, which) -> dlg.dismiss())
                .create();

        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey =
                    event != null
                            && event.getAction() == KeyEvent.ACTION_UP
                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            boolean isDoneAction =
                    actionId == EditorInfo.IME_ACTION_DONE
                            || actionId == EditorInfo.IME_ACTION_GO
                            || actionId == EditorInfo.IME_ACTION_SEND;
            if (!isEnterKey && !isDoneAction) {
                return false;
            }
            if (pinDialog.isShowing()) {
                pinDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            }
            return true;
        });

        pinDialog.setOnShowListener(d -> input.requestFocus());
        pinDialog.show();
    }

    private void bindDebugButton() {
        if (debugButton == null) {
            return;
        }
        debugButton.setOnClickListener(v -> {
            MaintenancePanelController controller = resolveMaintenancePanelController();
            if (controller != null) {
                controller.openMaintenancePanelForSession();
            }
        });
        updateDebugButtonVisibility();
    }

    private void updateDebugButtonVisibility() {
        if (debugButton == null) {
            return;
        }
        MaintenancePanelController controller = resolveMaintenancePanelController();
        boolean visible = controller != null && controller.isMaintenanceSessionActive();
        debugButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean isPinValid(@NonNull String pin) {
        String normalized = pin.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String digest = sha256(normalized);
        return digest.equalsIgnoreCase(BuildConfig.MAINTENANCE_PIN_SHA256);
    }

    @NonNull
    private String sha256(@NonNull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format(Locale.US, "%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to hash PIN", e);
            return "";
        }
    }

    @Nullable
    private MaintenancePanelController resolveMaintenancePanelController() {
        if (getActivity() instanceof MaintenancePanelController) {
            return (MaintenancePanelController) getActivity();
        }
        return null;
    }
}
