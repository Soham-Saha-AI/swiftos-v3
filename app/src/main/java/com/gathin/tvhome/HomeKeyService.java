package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sends physical HOME to this launcher, or to its App Library when held. Remotes that emit
 * KEYCODE_ALL_APPS open the App Library directly. Android TV 9 otherwise routes ALL_APPS only to the
 * system-image launcher, so once the stock launcher is disabled the button does nothing.
 *
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here, for firmware that turns
 * that launcher on again after it was disabled (XGIMI's xrmservice does, on its own remote keys).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    private static final String TAG = "TVHomeRemote";
    private static final String ACTION_OPEN_LIBRARY = "com.gathin.tvhome.action.OPEN_LIBRARY";
    private final Handler homeHandler = new Handler(Looper.getMainLooper());
    private boolean homePressed;
    private boolean homeLongPressLaunched;
    private long homeDownTime;
    private final Runnable homeLongPress = this::openLibraryForHomeHold;

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private BroadcastReceiver unlockReceiver;
    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private final Set<String> stockHome = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        cancelHomePress();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // The service is directBootAware: Android 11 skips a service that is not while the user is still
        // locked at boot, writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the
        // unlock. Config.load reads credential-protected storage, so wait for the unlock before reading it.
        UserManager users = getSystemService(UserManager.class);
        if (users.isUserUnlocked()) {
            loadConfig();
            return;
        }
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopWaitingForUnlock();
                loadConfig();
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked()) {
            stopWaitingForUnlock();
            loadConfig();
        }
    }

    private void stopWaitingForUnlock() {
        if (unlockReceiver == null) return;
        unregisterReceiver(unlockReceiver);
        unlockReceiver = null;
    }

    @Override
    public void onDestroy() {
        cancelHomePress();
        stopWaitingForUnlock();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cancelHomePress();
        return super.onUnbind(intent);
    }

    private void loadConfig() {
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        stockHome.clear();
        if (!config.stockHomeRedirect.isEmpty()) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
        }
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Consume every HOME phase before any configured mapping or Android's native HOME policy.
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) return handleHomeKey(event);
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) return true;
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS);
                try {
                    startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (RemoteKeys.handle(this, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            openLauncher(true);
        }
        return true;
    }

    private boolean handleHomeKey(KeyEvent event) {
        long heldMs = Math.max(0L, event.getEventTime() - event.getDownTime());
        Log.d(TAG, "HOME action=" + event.getAction() + " repeat=" + event.getRepeatCount()
                + " durationMs=" + heldMs);
        if (event.isCanceled()) {
            cancelHomePress();
            return true;
        }
        int timeoutMs = ViewConfiguration.getLongPressTimeout();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!homePressed || homeDownTime != event.getDownTime()) {
                cancelHomePress();
                homePressed = true;
                homeDownTime = event.getDownTime();
                homeHandler.postDelayed(homeLongPress, Math.max(0L, timeoutMs - heldMs));
            }
            // Repeats are useful on remotes that flag a long press, but the timer also works when
            // the remote sends only one DOWN followed much later by UP.
            if (event.isLongPress() || heldMs >= timeoutMs) openLibraryForHomeHold();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean trackedPress = homePressed && homeDownTime == event.getDownTime();
            // If the main thread was busy until release, still classify the press by its duration.
            if (trackedPress && heldMs >= timeoutMs) openLibraryForHomeHold();
            boolean shortPress = trackedPress && !homeLongPressLaunched;
            cancelHomePress();
            if (shortPress) openLauncher(false);
        }
        return true;
    }

    private void openLibraryForHomeHold() {
        if (!homePressed || homeLongPressLaunched) return;
        homeLongPressLaunched = true;
        homeHandler.removeCallbacks(homeLongPress);
        openLauncher(true);
    }

    private void cancelHomePress() {
        homeHandler.removeCallbacks(homeLongPress);
        homePressed = false;
        homeLongPressLaunched = false;
        homeDownTime = 0L;
    }

    private void openLauncher(boolean library) {
        Intent open = new Intent(this, MainActivity.class)
                .setAction(library ? ACTION_OPEN_LIBRARY : Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!library) open.addCategory(Intent.CATEGORY_HOME);
        startActivity(open);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // stock_home_redirect: the firmware turned the stock launcher back on and its home screen came up.
        if (stockHome.isEmpty() || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (cls != null && stockHome.contains(cls.toString())) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }
    }

    @Override
    public void onInterrupt() {
        cancelHomePress();
    }
}
