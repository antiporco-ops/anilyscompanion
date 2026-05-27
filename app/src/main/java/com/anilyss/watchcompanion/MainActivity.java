package com.anilyss.watchcompanion;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.anilyss.watchcompanion.settings.AppLanguageStore;
import com.anilyss.watchcompanion.settings.AppThemeStore;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SettingsFragment.MaintenancePanelController {

    private static final String TAG_FACES = "tab_faces";
    private static final String TAG_BATTERY = "tab_battery";
    private static final String TAG_SETTINGS = "tab_settings";
    private static final String STATE_SELECTED_TAB_ID = "state_selected_tab_id";
    public static final String EXTRA_OPEN_TAB_ID = "open_tab_id";
    private static final String PREFS_MAINTENANCE = "maintenance_debug";
    private static final String KEY_DEBUG_UNLOCKED = "maintenance_debug_unlocked";

    @Nullable
    private View maintenancePanel;
    private boolean maintenancePanelVisible;
    private boolean maintenanceDebugUnlocked;
    @Nullable
    private CatalogRepository catalogRepository;
    @Nullable
    private ExecutorService ioExecutor;
    private boolean imageCacheClearInProgress;
    private boolean reloadPendingAfterImageCacheClear;

    public static Intent createOpenBatteryIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_OPEN_TAB_ID, R.id.nav_battery);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppThemeStore.applyStoredTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ioExecutor = Executors.newSingleThreadExecutor();
        catalogRepository = new CatalogRepository(getApplicationContext());
        maintenancePanel = findViewById(R.id.main_maintenance_panel);
        maintenanceDebugUnlocked = readMaintenanceDebugUnlocked();
        bindMaintenancePanelActions();

        maintenancePanelVisible = false;
        updateMaintenancePanelVisibility();

        BottomNavigationView bottomNavigationView = findViewById(R.id.main_bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> switchToTab(item.getItemId()));
        bottomNavigationView.setOnItemReselectedListener(item -> {
            // Top-level destinations: keep current state on reselection.
        });

        int initialTabId = R.id.nav_faces;
        if (savedInstanceState != null) {
            initialTabId = savedInstanceState.getInt(STATE_SELECTED_TAB_ID, R.id.nav_faces);
        }
        int requestedTabId = getIntent().getIntExtra(EXTRA_OPEN_TAB_ID, 0);
        if (requestedTabId != 0) {
            initialTabId = requestedTabId;
        }

        if (!switchToTab(initialTabId)) {
            initialTabId = R.id.nav_faces;
            switchToTab(initialTabId);
        }
        if (bottomNavigationView.getMenu().findItem(initialTabId) != null) {
            bottomNavigationView.getMenu().findItem(initialTabId).setChecked(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) {
            return;
        }
        int requestedTabId = intent.getIntExtra(EXTRA_OPEN_TAB_ID, 0);
        if (requestedTabId == 0) {
            return;
        }
        BottomNavigationView bottomNavigationView = findViewById(R.id.main_bottom_navigation);
        if (!switchToTab(requestedTabId)) {
            return;
        }
        if (bottomNavigationView != null && bottomNavigationView.getMenu().findItem(requestedTabId) != null) {
            bottomNavigationView.getMenu().findItem(requestedTabId).setChecked(true);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BottomNavigationView bottomNavigationView = findViewById(R.id.main_bottom_navigation);
        if (bottomNavigationView != null) {
            outState.putInt(STATE_SELECTED_TAB_ID, bottomNavigationView.getSelectedItemId());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
    }

    @Override
    public boolean isMaintenanceSessionActive() {
        return maintenanceDebugUnlocked;
    }

    @Override
    public void openMaintenancePanelForSession() {
        if (!maintenanceDebugUnlocked) {
            maintenanceDebugUnlocked = true;
            writeMaintenanceDebugUnlocked(true);
        }
        maintenancePanelVisible = true;
        updateMaintenancePanelVisibility();
    }

    @Override
    public void closeMaintenancePanelAndEndSession() {
        maintenancePanelVisible = false;
        updateMaintenancePanelVisibility();
    }

    private boolean switchToTab(int menuItemId) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment faces = fragmentManager.findFragmentByTag(TAG_FACES);
        Fragment battery = fragmentManager.findFragmentByTag(TAG_BATTERY);
        Fragment settings = fragmentManager.findFragmentByTag(TAG_SETTINGS);

        String targetTag;
        Fragment targetFragment;
        if (menuItemId == R.id.nav_faces) {
            targetTag = TAG_FACES;
            targetFragment = faces;
        } else if (menuItemId == R.id.nav_battery) {
            targetTag = TAG_BATTERY;
            targetFragment = battery;
        } else if (menuItemId == R.id.nav_settings) {
            targetTag = TAG_SETTINGS;
            targetFragment = settings;
        } else {
            return false;
        }

        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setReorderingAllowed(true);

        if (faces != null) transaction.hide(faces);
        if (battery != null) transaction.hide(battery);
        if (settings != null) transaction.hide(settings);

        if (targetFragment == null) {
            targetFragment = createTabFragment(targetTag);
            transaction.add(R.id.main_fragment_container, targetFragment, targetTag);
        } else {
            transaction.show(targetFragment);
        }

        transaction.commit();
        return true;
    }

    @NonNull
    private Fragment createTabFragment(@NonNull String tag) {
        switch (tag) {
            case TAG_BATTERY:
                return new BatteryFragment();
            case TAG_SETTINGS:
                return new SettingsFragment();
            case TAG_FACES:
            default:
                return new FacesFragment();
        }
    }

    private void bindMaintenancePanelActions() {
        View close = findViewById(R.id.main_maintenance_close);
        TextView updateCatalog = findViewById(R.id.main_maintenance_action_update_catalog);
        TextView clearCatalogCache = findViewById(R.id.main_maintenance_action_clear_catalog_cache);
        TextView clearImageCache = findViewById(R.id.main_maintenance_action_clear_image_cache);
        TextView reloadAppData = findViewById(R.id.main_maintenance_action_reload_app_data);

        close.setOnClickListener(v -> closeMaintenancePanelAndEndSession());
        updateCatalog.setOnClickListener(v -> onUpdateCatalogNow());
        clearCatalogCache.setOnClickListener(v -> onClearCatalogCache());
        clearImageCache.setOnClickListener(v -> onClearImageCache());
        reloadAppData.setOnClickListener(v -> onReloadAppData());
    }

    private void updateMaintenancePanelVisibility() {
        if (maintenancePanel == null) {
            return;
        }
        maintenancePanel.setVisibility(maintenancePanelVisible ? View.VISIBLE : View.GONE);
    }

    private void onUpdateCatalogNow() {
        if (!maintenanceDebugUnlocked || catalogRepository == null) {
            return;
        }
        String languageCode = resolveCurrentLanguageCode();
        catalogRepository.fetchRemoteCatalogAsync(languageCode, data -> {
            int messageRes = data != null
                    ? R.string.maintenance_result_catalog_updated
                    : R.string.maintenance_result_catalog_update_failed;
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        });
    }

    private void onClearCatalogCache() {
        if (!maintenanceDebugUnlocked || catalogRepository == null) {
            return;
        }
        boolean cleared = catalogRepository.clearCachedCatalog();
        int messageRes = cleared
                ? R.string.maintenance_result_catalog_cache_cleared
                : R.string.maintenance_result_catalog_cache_clear_failed;
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    private void onClearImageCache() {
        if (!maintenanceDebugUnlocked) {
            return;
        }
        if (imageCacheClearInProgress) {
            return;
        }
        imageCacheClearInProgress = true;
        Glide.get(getApplicationContext()).clearMemory();
        ExecutorService executor = ioExecutor;
        if (executor == null) {
            imageCacheClearInProgress = false;
            return;
        }
        executor.execute(() -> {
            Glide.get(getApplicationContext()).clearDiskCache();
            ImageCacheInvalidation.bumpVersion(getApplicationContext());
            runOnUiThread(() -> {
                Toast.makeText(
                        this,
                        R.string.maintenance_result_image_cache_cleared,
                        Toast.LENGTH_SHORT
                ).show();
                imageCacheClearInProgress = false;
                if (reloadPendingAfterImageCacheClear) {
                    reloadPendingAfterImageCacheClear = false;
                    recreate();
                }
            });
        });
    }

    private void onReloadAppData() {
        if (!maintenanceDebugUnlocked) {
            return;
        }
        Toast.makeText(this, R.string.maintenance_result_reload_started, Toast.LENGTH_SHORT).show();
        if (imageCacheClearInProgress) {
            reloadPendingAfterImageCacheClear = true;
            return;
        }
        recreate();
    }

    @NonNull
    private String resolveCurrentLanguageCode() {
        String language = AppLanguageStore.readLanguageTag(getApplicationContext());
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

    private boolean readMaintenanceDebugUnlocked() {
        return getSharedPreferences(PREFS_MAINTENANCE, MODE_PRIVATE)
                .getBoolean(KEY_DEBUG_UNLOCKED, false);
    }

    private void writeMaintenanceDebugUnlocked(boolean unlocked) {
        getSharedPreferences(PREFS_MAINTENANCE, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DEBUG_UNLOCKED, unlocked)
                .apply();
    }
}
