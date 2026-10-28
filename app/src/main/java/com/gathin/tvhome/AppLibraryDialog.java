package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ChangedPackages;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Predicate;

/** On-demand installed-app picker. Uses the launcher's glass resources; no backdrop capture or blur. */
final class AppLibraryDialog {
    /**
     * The catalogue, the labels and the icons load here, off the UI thread: each label and icon opens the app's
     * resources, and on a TCL G09 that froze Home for 60-140 ms (2 to 4 janky frames) every time the library opened.
     */
    private static final ThreadPoolExecutor LOADER = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    /** An obsolete icon can never hold the current catalogue behind a serial artwork backlog. */
    private static final ThreadPoolExecutor ARTWORK = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    private Future<?> catalogueTask;
    private final List<Future<?>> artworkTasks = new ArrayList<>();
    /** Labels kept between openings (LOADER thread only), until the app is updated. */
    private static final Map<String, Known> KNOWN = new HashMap<>();
    /** Metadata only, confined to LOADER. Each opening still recalculates Home badges and ordering. */
    private static Catalogue cachedCatalogue;

    private static final class Catalogue {
        final int packageSequence;
        final String locale;
        final List<ResolveInfo> apps;
        final Set<String> tv;
        final Map<String, String> names;
        final Map<String, Long> installed;

        Catalogue(int packageSequence, String locale, List<ResolveInfo> apps, Set<String> tv,
                  Map<String, String> names, Map<String, Long> installed) {
            this.packageSequence = packageSequence;
            this.locale = locale;
            this.apps = apps;
            this.tv = tv;
            this.names = names;
            this.installed = installed;
        }
    }

    private static final class Known {
        final long updated;
        final String label;

        Known(long updated, String label) {
            this.updated = updated;
            this.label = label;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    /** Icons loaded for this opening, dropped with it. */
    private final Map<String, Artwork> icons = new HashMap<>();
    private final Set<String> iconsLoading = new HashSet<>();
    private final Runnable dismissed;
    private Runnable presenting = () -> { };
    private volatile boolean closed;
    private boolean dismissDelivered;
    /** Only the latest load fills the catalogue. */
    private final LibraryLoads loads = new LibraryLoads();
    private final Activity activity;
    private final Predicate<String> onHome;
    private final Map<String, Boolean> membershipOverrides = new HashMap<>();
    private final Runnable changed;
    private final LinearLayout panel;
    private final View origin;
    private final boolean reducedMotion;
    private final boolean animations;
    private final int iconBackgroundOpacity;
    private final boolean hardwareArtworkEnabled;
    private final boolean rtl;
    private TextView instructions;
    private TextView countLabel;
    private int totalApps;
    final PopupWindow popup;
    private final float density;
    private List<ResolveInfo> apps = new ArrayList<>();
    private String selectedPackage;
    private int selectedPosition;
    private int selectedOffset;
    private int preferredColumn = -1;
    private int pendingFocusPosition = -1;
    private int focusRequest;
    private RecyclerView focusListenerOwner;
    private ViewTreeObserver.OnPreDrawListener focusListener;
    private final Map<String, Long> installTimes = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private long catalogTime;
    private PopupWindow actionPopup;
    private RecyclerView catalog;
    private FrameLayout shelf;
    private GlassView highlight;
    private boolean highlightShown;
    private SpringAnimation highlightX, highlightY;
    private float highlightScrollShift;
    private View pendingHighlightCard;
    private boolean pendingHighlightAnimate;
    private int lastFocusedPosition = RecyclerView.NO_POSITION;
    private ViewTreeObserver.OnPreDrawListener entryListener;
    private final Runnable updateHighlight = () -> {
        View card = pendingHighlightCard;
        boolean animate = pendingHighlightAnimate;
        pendingHighlightCard = null;
        if (!closed && catalog != null && card != null && card.hasFocus()) highlight(card, animate);
    };
    private final int catalogWidth;
    private final int catalogHeight;

    private static final class Artwork {
        final Drawable drawable;
        final boolean banner;

        Artwork(Drawable drawable, boolean banner) {
            this.drawable = drawable;
            this.banner = banner;
        }
    }

    private void stopHighlight() {
        ++focusRequest;
        pendingFocusPosition = -1;
        removeFocusListener();
        main.removeCallbacks(updateHighlight);
        pendingHighlightCard = null;
        cancelHighlightSprings();
        if (highlight != null) highlight.stopInteraction();
        if (catalog != null) catalog.stopScroll();
        highlightShown = false;
        lastFocusedPosition = RecyclerView.NO_POSITION;
    }

    private void cancelHighlightSprings() {
        if (highlightX != null) { highlightX.cancel(); highlightX.setStartVelocity(0f); }
        if (highlightY != null) { highlightY.cancel(); highlightY.setStartVelocity(0f); }
    }

    /** One pending focus update; repeated remote presses retarget the same pair of springs. */
    private void queueHighlight(View card) {
        main.removeCallbacks(updateHighlight);
        pendingHighlightCard = card;
        pendingHighlightAnimate = true;
        main.post(updateHighlight);
    }

    private void interactForFocus(int position) {
        if (highlight != null && lastFocusedPosition != RecyclerView.NO_POSITION && position != lastFocusedPosition) {
            int dx = position % LibraryGrid.COLUMNS - lastFocusedPosition % LibraryGrid.COLUMNS;
            int dy = position / LibraryGrid.COLUMNS - lastFocusedPosition / LibraryGrid.COLUMNS;
            highlight.interact(rtl ? -dx : dx, dy, animations && !reducedMotion);
        }
        lastFocusedPosition = position;
    }

    private void highlight(View card, boolean animate) {
        if (highlight == null || card.getParent() == null || card.getWidth() == 0 || card.getHeight() == 0) return;
        Rect rect = new Rect(0, 0, card.getWidth(), card.getHeight());
        shelf.offsetDescendantRectToMyCoords(card, rect);
        float scale = Motion.appFocusScale(animations, reducedMotion);
        ViewGroup.LayoutParams lp = highlight.getLayoutParams();
        int width = Math.round(rect.width() * scale) + dp(12);
        int height = Math.round(rect.height() * scale) + dp(12);
        if (lp.width != width || lp.height != height) {
            lp.width = width;
            lp.height = height;
            highlight.setLayoutParams(lp);
        }
        float x = rect.exactCenterX() - width / 2f;
        float y = rect.exactCenterY() - height / 2f
                - (animations && !reducedMotion ? Motion.FOCUS_LIFT_DP * density : 0f);
        highlight.setRadius(dp(14) * scale);
        if (!highlightShown || !animate || !animations || reducedMotion) {
            cancelHighlightSprings();
            highlight.setTranslationX(x);
            highlight.setTranslationY(y);
        } else {
            if (highlightX.isRunning() || Math.abs(highlight.getTranslationX() - x) > 0.5f)
                Motion.retarget(highlightX, x);
            if (highlightY.isRunning() || Math.abs(highlight.getTranslationY() - y) > 0.5f)
                Motion.retarget(highlightY, y - highlightScrollShift);
        }
        highlightShown = true;
        highlight.setAlpha(1f);
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        bg.setStroke(dp(1), 0x18FFFFFF);
        return bg;
    }

    private void resize(int width, int height) {
        popup.setWidth(width);
        popup.setHeight(height);
        if (popup.isShowing()) popup.update(width, height);
    }

    AppLibraryDialog(Activity activity, boolean animations, boolean reducedMotion, int iconBackgroundOpacity, Predicate<String> onHome, Runnable changed, Runnable dismissed) {
        this(activity, null, animations, reducedMotion, iconBackgroundOpacity, false, onHome, changed, dismissed);
    }

    AppLibraryDialog(Activity activity, View origin, boolean animations, boolean reducedMotion, int iconBackgroundOpacity, Predicate<String> onHome, Runnable changed, Runnable dismissed) {
        this(activity, origin, animations, reducedMotion, iconBackgroundOpacity, false, onHome, changed, dismissed);
    }

    AppLibraryDialog(Activity activity, View origin, boolean animations, boolean reducedMotion, int iconBackgroundOpacity,
                     boolean hardwareArtworkEnabled, Predicate<String> onHome, Runnable changed, Runnable dismissed) {
        this.activity = activity;
        this.origin = origin;
        this.animations = animations;
        this.iconBackgroundOpacity = iconBackgroundOpacity;
        this.hardwareArtworkEnabled = hardwareArtworkEnabled;
        this.rtl = activity.getResources().getBoolean(R.bool.rtl_app_grid);
        this.reducedMotion = reducedMotion;
        this.onHome = onHome;
        this.changed = changed;
        this.dismissed = dismissed;
        density = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackgroundResource(R.drawable.glass_panel);
        View decor = activity.getWindow().getDecorView();
        int screenWidth = decor.getWidth() > 0 ? decor.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = decor.getHeight() > 0 ? decor.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
        catalogWidth = Math.min(dp(840), Math.max(1, screenWidth - dp(32)));
        catalogHeight = Math.min(dp(450), Math.max(1, screenHeight - dp(32)));
        int width = catalogWidth;
        int height = catalogHeight;
        popup = new PopupWindow(panel, width, height, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setOnDismissListener(this::finishClose);
    }

    void close() {
        if (closed) return;
        // Invalidate worker replies and focus restoration before dismissing either popup.
        closed = true;
        dismissActions();
        if (popup.isShowing()) {
            popup.dismiss();
        } else {
            finishClose();  // still loading: it never shows
        }
    }

    private void finishClose() {
        if (dismissDelivered) return;
        closed = true;
        loads.cancel();
        dismissDelivered = true;
        dismissActions();
        stopHighlight();
        stopEntry();
        icons.clear();
        iconsLoading.clear();
        cancelWork();
        dismissed.run();
    }

    private void cancelWork() {
        if (catalogueTask != null) catalogueTask.cancel(false);
        for (Future<?> work : artworkTasks) work.cancel(false);
        artworkTasks.clear();
        LOADER.purge();
        ARTWORK.purge();
    }

    /** Release the activity-owned reusable hierarchy under memory pressure or configuration destruction. */
    void release() {
        close();
        cancelWork();
        if (catalog != null) {
            catalog.setAdapter(null);
            catalog.getRecycledViewPool().clear();
        }
        catalog = null;
        highlightX = highlightY = null;
        highlight = null;
        shelf = null;
        panel.removeAllViews();
        icons.clear();
    }

    void show() {
        closed = false;
        dismissDelivered = false;
        load(true);
    }

    void onPresenting(Runnable callback) { presenting = callback; }

    private void reveal() {
        if (closed || popup.isShowing()) return;
        if (activity.isFinishing() || activity.isDestroyed()) {
            close();
            return;
        }
        panel.setAlpha(animations ? 0f : 1f);
        float startScale = animations && !reducedMotion ? 0.96f : 1f;
        panel.setScaleX(startScale);
        panel.setScaleY(startScale);
        try {
        presenting.run();
        popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
        View container = panel.getRootView();
        if (container.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) container.getLayoutParams();
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.22f;
            activity.getWindowManager().updateViewLayout(container, params);
        }
        if (!animations) return;
        // Resolve the origin once after layout; the pivot never moves during the entry.
        entryListener = () -> {
            removeEntryListener();
            if (closed || !popup.isShowing()) return true;
            float pivotX = panel.getWidth(), pivotY = 0f;
            if (origin != null && origin.isAttachedToWindow()) {
                int[] anchorLocation = new int[2], panelLocation = new int[2];
                origin.getLocationOnScreen(anchorLocation);
                panel.getLocationOnScreen(panelLocation);
                pivotX = Math.max(0f, Math.min(panel.getWidth(), anchorLocation[0] + origin.getWidth() / 2f - panelLocation[0]));
                pivotY = Math.max(0f, Math.min(panel.getHeight(), anchorLocation[1] + origin.getHeight() / 2f - panelLocation[1]));
            }
            panel.setPivotX(pivotX);
            panel.setPivotY(pivotY);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(reducedMotion ? Motion.REDUCED_MS : 220)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            return true;
        };
        panel.getViewTreeObserver().addOnPreDrawListener(entryListener);
        } catch (RuntimeException failure) {
            android.util.Log.w("SwiftOSCatalogue", "Library presentation failed", failure);
            close(); // The common dismissal callback restores the Home foreground even after a partial show.
            Toast.makeText(activity, activity.getString(R.string.cant_open,
                    activity.getString(R.string.app_library)), Toast.LENGTH_SHORT).show();
        }
    }

    private void removeEntryListener() {
        if (entryListener != null && panel.getViewTreeObserver().isAlive())
            panel.getViewTreeObserver().removeOnPreDrawListener(entryListener);
        entryListener = null;
    }

    private void stopEntry() {
        removeEntryListener();
        panel.animate().cancel();
        panel.setAlpha(1f);
        panel.setScaleX(1f);
        panel.setScaleY(1f);
    }

    /** Called by the activity's existing package receiver; an uninstall cannot leave a stale action. */
    /** Packages with a leanback entry, from the last refresh. */
    private final java.util.Set<String> tvPackages = new java.util.HashSet<>();

    void refresh() {
        load(false);
    }

    private boolean isOnHome(String pkg) { return membershipOverrides.getOrDefault(pkg, onHome.test(pkg)); }

    void homeMembershipPublished() {
        if (closed) return;
        membershipOverrides.clear();
        fill(new ArrayList<>(apps));
    }

    /** Reads on LOADER; the latest completion retains an opening superseded by a package refresh. */
    private void load(boolean reveal) {
        dismissActions();
        int load = loads.start(reveal);
        cancelWork();
        icons.clear();
        iconsLoading.clear();
        PackageManager pm = activity.getPackageManager();
        String self = activity.getPackageName();
        String locale = activity.getResources().getConfiguration().getLocales().toLanguageTags();
        catalogueTask = LOADER.submit(() -> {
            try {
            if (closed || !loads.isLatest(load)) return;
            Catalogue cached = cachedCatalogue;
            if (cached != null && cached.locale.equals(locale)) {
                try {
                    ChangedPackages delta = pm.getChangedPackages(cached.packageSequence);
                    if (delta == null || delta.getPackageNames().isEmpty()) {
                        publishCatalogue(load, cached);
                        return;
                    }
                } catch (RuntimeException unavailable) { /* Rebuild when sequence lookup is unavailable. */ }
            }
            // Record the sequence before querying: an install during this load must invalidate the next opening.
            int sequence = -1;
            try {
                ChangedPackages all = pm.getChangedPackages(0);
                sequence = all == null ? 0 : all.getSequenceNumber();
            } catch (RuntimeException unavailable) { /* This load remains uncached. */ }
            Map<String, ResolveInfo> unique = new LinkedHashMap<>();
            Set<String> tv = new HashSet<>();
            // Prefer the TV entry when an app declares both categories.
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                if (closed || !loads.isLatest(load)) return;
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    if (info.activityInfo == null || !info.activityInfo.exported) continue;
                    String pkg = info.activityInfo.packageName;
                    if (Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category)) tv.add(pkg);
                    if (!pkg.equals(self) && !unique.containsKey(pkg)) unique.put(pkg, info);
                }
            }
            Map<String, String> names = new HashMap<>();
            Map<String, Long> installed = new HashMap<>();
            for (Map.Entry<String, ResolveInfo> entry : unique.entrySet()) {
                if (closed || !loads.isLatest(load)) return;
                String pkg = entry.getKey();
                long updated = 0;
                try {
                    PackageInfo info = pm.getPackageInfo(pkg, 0);
                    installed.put(pkg, info.firstInstallTime);
                    updated = info.lastUpdateTime;
                } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
                    installed.put(pkg, 0L);
                }
                Known known = KNOWN.get(pkg);
                if (known == null || known.updated != updated || cached != null && !cached.locale.equals(locale)) {
                    known = new Known(updated, loadLabel(pm, entry.getValue()));
                    KNOWN.put(pkg, known);
                }
                names.put(pkg, known.label);
            }
            if (closed || !loads.isLatest(load)) return;
            KNOWN.keySet().retainAll(unique.keySet());
            Catalogue result = new Catalogue(sequence, locale, new ArrayList<>(unique.values()), tv, names, installed);
            cachedCatalogue = sequence < 0 ? null : result;
            publishCatalogue(load, result);
            } catch (RuntimeException failure) {
                main.post(() -> {
                    if (closed || !loads.isLatest(load)) return;
                    android.util.Log.w("SwiftOSCatalogue", "Library catalogue failed", failure);
                    close(); // A failed refresh of an open Library must also restore its Home foreground.
                });
            }
        });
    }

    private void publishCatalogue(int load, Catalogue source) {
        main.post(() -> {
            if (closed || !loads.isLatest(load)) return;
            try {
            tvPackages.clear();
            tvPackages.addAll(source.tv);
            installTimes.clear();
            installTimes.putAll(source.installed);
            labels.clear();
            labels.putAll(source.names);
            fill(new ArrayList<>(source.apps));
            if (loads.takeReveal(load)) reveal();
            } catch (RuntimeException failure) {
                android.util.Log.w("SwiftOSCatalogue", "Library publication failed", failure);
                close();
            }
        });
    }

    private void fill(List<ResolveInfo> catalogue) {
        totalApps = catalogue.size();
        apps = catalogue;
        Map<String, Boolean> home = new HashMap<>();
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            home.put(pkg, isOnHome(pkg));
        }
        catalogTime = System.currentTimeMillis();
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> {
            String pa = a.activityInfo.packageName, pb = b.activityInfo.packageName;
            int priority = LibraryApps.comparePriority(home.get(pa), installTimes.get(pa),
                    home.get(pb), installTimes.get(pb), catalogTime);
            if (priority != 0) return priority;
            int byLabel = collator.compare(label(a), label(b));
            return byLabel != 0 ? byLabel : pa.compareTo(pb);
        });
        showCatalog();
    }

    private String label(ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        String cached = labels.get(pkg);
        if (cached != null) return cached;
        String value = loadLabel(activity.getPackageManager(), app);
        labels.put(pkg, value);
        return value;
    }

    private static String loadLabel(PackageManager pm, ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        try { return Cards.cleanLabel(String.valueOf(app.loadLabel(pm)), pkg); }
        catch (RuntimeException unavailable) { return pkg; }
    }

    /** Glass artwork and future-app fallbacks stay off the UI thread and are cached for this opening. */
    private void bindIcon(ImageView icon, ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        icon.setTag(pkg);
        Artwork cached = icons.get(pkg);
        if (cached == null) {
            Drawable shared = GlassArtwork.peek(activity, pkg, label(app), iconBackgroundOpacity, hardwareArtworkEnabled);
            if (shared != null) {
                cached = new Artwork(shared, true);
                icons.put(pkg, cached);
            }
        }
        showArtwork(icon, cached);
        if (cached != null || !iconsLoading.add(pkg)) return;
        PackageManager pm = activity.getPackageManager();
        String name = label(app);
        Context appContext = activity.getApplicationContext();
        int generation = loads.current();
        int cacheToken = GlassArtwork.cacheToken();
        artworkTasks.removeIf(Future::isDone);
        artworkTasks.add(ARTWORK.submit(() -> {
            if (closed || !loads.isLatest(generation)) return;
            Drawable loaded = GlassArtwork.loadApp(appContext, pkg, name, () -> app.loadIcon(pm),
                    cacheToken, iconBackgroundOpacity, hardwareArtworkEnabled);
            Artwork result = new Artwork(loaded, true);
            main.post(() -> {
                if (closed || !loads.isLatest(generation)) return;
                iconsLoading.remove(pkg);
                Artwork shown = result.drawable != null ? result
                        : new Artwork(activity.getDrawable(R.drawable.ic_apps), false);
                icons.put(pkg, shown);
                // The view may show another app by now; a view bound to it later takes it from the cache.
                if (pkg.equals(icon.getTag())) showArtwork(icon, shown);
                // A recycled holder may have rebound this package while the original request was pending.
                if (catalog != null) for (int i = 0; i < catalog.getChildCount(); i++) {
                    ImageView attached = catalog.getChildAt(i).findViewById(R.id.icon);
                    if (attached != null && pkg.equals(attached.getTag())) showArtwork(attached, shown);
                }
            });
        }));
    }

    private void showArtwork(ImageView icon, Artwork artwork) {
        boolean banner = artwork != null && artwork.banner;
        // Baked glass artwork already includes its rim and safe logo/name area.
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset = banner ? 0 : dp(8);
        icon.setPadding(inset, inset, inset, inset);
        icon.setImageDrawable(artwork != null ? artwork.drawable : null);
    }

    private void heading(String text) {
        TextView title = text(text, 18, 0xFFFFFFFF);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(12), dp(10), dp(12), dp(8));
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showCatalog() {
        stopHighlight();
        resize(catalogWidth, catalogHeight);
        if (catalog != null && !apps.isEmpty()) {
            countLabel.setText(activity.getResources().getQuantityString(R.plurals.library_app_count, totalApps, totalApps));
            catalog.getAdapter().notifyDataSetChanged();
            restoreSelection(catalog);
            return;
        }
        catalog = null;
        panel.removeAllViews();
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(8), 0);
        TextView title = text(activity.getString(R.string.app_library), 22, Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView count = text(activity.getResources().getQuantityString(
                R.plurals.library_app_count, totalApps, totalApps), 13, 0xA6FFFFFF);
        countLabel = count;
        count.setSingleLine(true);
        header.addView(count, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        instructions = text(activity.getString(R.string.library_hint_open), 12, 0xBBFFFFFF);
        instructions.setSingleLine(true);
        instructions.setEllipsize(TextUtils.TruncateAt.END);
        instructions.setPadding(dp(8), 0, dp(8), dp(6));
        panel.addView(instructions, new LinearLayout.LayoutParams(-1, dp(24)));
        panel.setFocusableInTouchMode(apps.isEmpty());
        if (apps.isEmpty()) {
            instructions.setVisibility(View.GONE);
            heading(activity.getString(R.string.library_empty));
            panel.setFocusableInTouchMode(true);
            panel.requestFocus();
            return;
        }
        shelf = new FrameLayout(activity);
        // The glass uses physical x coordinates; only the catalogue mirrors its item order.
        shelf.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        shelf.setClipChildren(true);
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        catalog = list;
        list.setLayoutManager(new GridLayoutManager(activity, LibraryGrid.COLUMNS, RecyclerView.VERTICAL, false) {
            @Override protected boolean isLayoutRTL() { return rtl; }
        });
        list.setItemAnimator(null);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        // Keep the enlarged card and its 6dp rim inside the clipped viewport at every edge.
        list.setPadding(dp(16), dp(16), dp(16), dp(16));
        list.setVerticalScrollBarEnabled(true);
        list.setScrollBarSize(dp(2));
        list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        list.setScrollbarFadingEnabled(true);
        list.setAdapter(new Catalog());
        shelf.addView(list, new FrameLayout.LayoutParams(-1, -1));
        highlight = new GlassView(activity, null);
        highlightScrollShift = 0f;
        highlight.setRadius(dp(14));
        highlight.setAlpha(0f);
        highlight.setFocusable(false);
        highlight.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shelf.addView(highlight, new FrameLayout.LayoutParams(dp(132), dp(116)));
        highlightX = new SpringAnimation(highlight, DynamicAnimation.TRANSLATION_X);
        highlightY = new SpringAnimation(highlight, new FloatPropertyCompat<GlassView>("libraryContentY") {
            @Override public float getValue(GlassView view) { return view.getTranslationY() - highlightScrollShift; }
            @Override public void setValue(GlassView view, float value) { view.setTranslationY(value + highlightScrollShift); }
        });
        for (SpringAnimation spring : new SpringAnimation[]{highlightX, highlightY}) {
            spring.setSpring(new SpringForce().setStiffness(520f).setDampingRatio(1f));
        }
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (closed || catalog != rv) return;
                // Keep the running spring in content coordinates while scrolling moves its viewport.
                highlightScrollShift -= dy;
                highlight.setTranslationY(highlight.getTranslationY() - dy);
                RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(selectedPosition);
                if (holder != null && holder.itemView.hasFocus()) queueHighlight(holder.itemView);
                else { cancelHighlightSprings(); highlight.setAlpha(0f); highlightShown = false; }
            }

            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (closed || catalog != rv) return;
                RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(selectedPosition);
                if (holder != null && holder.itemView.hasFocus()) queueHighlight(holder.itemView);
            }
        });
        panel.addView(shelf, new LinearLayout.LayoutParams(-1, 0, 1));
        restoreSelection(list);
    }

    private void restoreSelection(RecyclerView list) {
        int position = Math.min(selectedPosition, apps.size() - 1);
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).activityInfo.packageName.equals(selectedPackage)) { position = i; break; }
        }
        selectedPosition = position;
        preferredColumn = position % LibraryGrid.COLUMNS;
        ((GridLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, selectedOffset);
        final int target = position;
        list.post(() -> {
            if (catalog != list || !popup.isShowing()) return;
            focusCard(target);
        });
    }

    private void dismissActions() {
        PopupWindow menu = actionPopup;
        actionPopup = null;
        if (menu != null) menu.dismiss();
    }

    private void restoreCardFocus() {
        if (closed || catalog == null || !popup.isShowing()) return;
        focusCard(selectedPosition);
    }

    private void removeFocusListener() {
        if (focusListenerOwner != null && focusListener != null && focusListenerOwner.getViewTreeObserver().isAlive())
            focusListenerOwner.getViewTreeObserver().removeOnPreDrawListener(focusListener);
        focusListenerOwner = null;
        focusListener = null;
    }

    /** Explicit grid navigation avoids Android's rectangle search wrapping into the next row. */
    private void focusCard(int position) {
        RecyclerView list = catalog;
        if (closed || list == null || position < 0 || position >= apps.size()) return;
        removeFocusListener();
        int request = ++focusRequest;
        pendingFocusPosition = position;
        selectedPosition = position;
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        boolean alreadyFocused = holder != null && holder.itemView.hasFocus();
        if (holder != null && holder.itemView.requestFocus()) {
            // New focus queues the moving rim in its callback. Snapping here would cancel that motion.
            if (alreadyFocused) {
                ControlMotion.libraryFocus(holder.itemView, true, animations, reducedMotion);
                queueHighlight(holder.itemView);
            }
            return;
        }
        list.scrollToPosition(position);
        // A plain post may run before RecyclerView lays out the offscreen row. Pre-draw runs after layout.
        focusListenerOwner = list;
        focusListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                boolean current = !closed && request == focusRequest && catalog == list && popup.isShowing() && actionPopup == null;
                RecyclerView.ViewHolder attached = current ? list.findViewHolderForAdapterPosition(position) : null;
                if (!current || attached != null) {
                    removeFocusListener();
                    if (attached != null) attached.itemView.requestFocus();
                }
                return true;
            }
        };
        list.getViewTreeObserver().addOnPreDrawListener(focusListener);
    }

    private void changeHome(ResolveInfo app, boolean add) {
        if (!LibraryApps.save(activity, app.activityInfo.packageName, add)) {
            Toast.makeText(activity, R.string.library_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        selectedOffset = 0;
        if (catalog != null) {
            RecyclerView.ViewHolder holder = catalog.findViewHolderForAdapterPosition(selectedPosition);
            if (holder != null) {
                View card = holder.itemView;
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) card.getLayoutParams();
                selectedOffset = catalog.getLayoutManager().getDecoratedTop(card)
                        - params.topMargin - catalog.getPaddingTop();
            }
        }
        dismissActions();
        membershipOverrides.put(app.activityInfo.packageName, add);
        changed.run();
        // Shortcut membership changed, not the installed catalogue. Re-sort/rebind badges without PM queries.
        fill(new ArrayList<>(apps));
    }

    private void open(ResolveInfo app) {
        close();
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName(app.activityInfo.packageName, app.activityInfo.name));
        // TV apps keep the leanback launch intent, so an app already running resumes instead of restarting.
        if (tvPackages.contains(app.activityInfo.packageName)) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show();
        }
    }

    private void showActions(ResolveInfo app, View anchor) {
        dismissActions();
        selectedPackage = app.activityInfo.packageName;
        String pkg = selectedPackage;
        cancelHighlightSprings();
        View menuPanel = activity.getLayoutInflater().inflate(R.layout.popup_inputs, panel, false);
        LinearLayout rows = menuPanel.findViewById(R.id.rows);
        PopupWindow menu = new PopupWindow(menuPanel, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        actionPopup = menu;
        menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        menu.setOutsideTouchable(true);
        menu.setAnimationStyle(0);
        menu.setElevation(0f);
        menu.setOnDismissListener(() -> {
            menuPanel.animate().cancel();
            if (actionPopup == menu) actionPopup = null;
            restoreCardFocus();
        });
        addAction(rows, R.string.library_open, () -> open(app));
        boolean home = isOnHome(pkg);
        addAction(rows, home ? R.string.library_remove_shortcut : R.string.library_add_home,
                () -> changeHome(app, !home));
        addAction(rows, R.string.library_app_info, () -> {
            close();
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show();
            }
        });
        int menuWidth = dp(220);
        for (int i = 0; i < rows.getChildCount(); i++) {
            TextView title = rows.getChildAt(i).findViewById(R.id.title);
            menuWidth = Math.max(menuWidth, (int) Math.ceil(title.getPaint().measureText(title.getText().toString())) + dp(58));
            title.setEllipsize(TextUtils.TruncateAt.END);
        }
        menuWidth = Math.min(menuWidth, panel.getWidth() - dp(24));
        menu.setWidth(menuWidth);
        menuPanel.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int[] panelLocation = new int[2];
        panel.getLocationOnScreen(panelLocation);
        Rect screen = new Rect(panelLocation[0], panelLocation[1],
                panelLocation[0] + panel.getWidth(), panelLocation[1] + panel.getHeight());
        int x = Math.max(screen.left + dp(12), Math.min(location[0] + anchor.getWidth() - dp(12), screen.right - menuWidth - dp(12)));
        int y = Math.max(screen.top + dp(12), Math.min(location[1] + dp(8), screen.bottom - menuPanel.getMeasuredHeight() - dp(12)));
        menuPanel.setAlpha(animations ? 0f : 1f);
        menuPanel.setPivotY(0f);
        menuPanel.setScaleY(animations && !reducedMotion ? 0.92f : 1f);
        menuPanel.setTranslationY(animations && !reducedMotion ? -dp(8) : 0f);
        menu.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.START, x, y);
        if (animations) menuPanel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(reducedMotion ? Motion.REDUCED_MS : 220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addAction(LinearLayout rows, int label, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((TextView) row.findViewById(R.id.title)).setText(label);
        row.setFocusableInTouchMode(true);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.setOnClickListener(v -> action.run());
        rows.addView(row);
        row.setOnKeyListener((v, key, event) -> {
            ControlMotion.key(v, key, event, animations, reducedMotion, true);
            int index = rows.indexOfChild(v);
            return event.getAction() == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP && index == 0
                    || key == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.getChildCount() - 1);
        });
        row.setOnFocusChangeListener((v, focused) -> ControlMotion.focus(v, focused, animations, reducedMotion, true));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(activity.getResources().getFont(size >= 18 ? R.font.inter_semibold
                : size <= 13 && color != Color.WHITE ? R.font.outfit_regular : R.font.outfit_medium));
        view.setLetterSpacing(0f);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(int value) { return Math.round(value * density); }

    private final class Catalog extends RecyclerView.Adapter<Row> {
        @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout row = new FrameLayout(activity);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104));
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.setLayoutParams(params);
            row.setPadding(dp(6), dp(6), dp(6), dp(4));
            row.setFocusableInTouchMode(true);
            row.setClickable(true);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL);
            row.addView(content, new FrameLayout.LayoutParams(-1, -1));
            ImageView icon = new ImageView(activity);
            icon.setId(R.id.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setClipToOutline(false);
            content.addView(icon, new LinearLayout.LayoutParams(-1, dp(60)));
            TextView name = text("", 12, Color.WHITE);
            name.setId(R.id.title);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setShadowLayer(dp(1), 0, dp(1), 0x90000000);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, dp(28));
            nameParams.topMargin = dp(4);
            content.addView(name, nameParams);
            View check = new View(activity);
            check.setId(R.id.dot);
            FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP | Gravity.END);
            badge.setMargins(0, dp(2), dp(2), 0);
            row.addView(check, badge);
            return new Row(row, activity.getDrawable(R.drawable.library_check), surface(0xFF70D9A0, 8));
        }
        @Override public void onBindViewHolder(@NonNull Row holder, int position) {
            ResolveInfo app = apps.get(position);
            String pkg = app.activityInfo.packageName;
            bindIcon(holder.itemView.findViewById(R.id.icon), app);
            TextView name = holder.itemView.findViewById(R.id.title);
            name.setText(label(app));
            name.setEllipsize(TextUtils.TruncateAt.END);
            boolean home = isOnHome(pkg);
            boolean recent = LibraryApps.recent(installTimes.getOrDefault(pkg, 0L), catalogTime);
            holder.itemView.setContentDescription(label(app) + (home
                    ? ", " + activity.getString(R.string.library_on_home)
                    : recent ? ", " + activity.getString(R.string.library_recent) : ""));
            View check = holder.itemView.findViewById(R.id.dot);
            check.getLayoutParams().width = dp(home ? 14 : 6);
            check.getLayoutParams().height = dp(home ? 14 : 6);
            check.setBackground(home ? holder.homeBadge : holder.recentBadge);
            check.setVisibility(home || recent ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> open(app));
            holder.itemView.setOnLongClickListener(v -> { showActions(app, v); return true; });
            holder.itemView.setOnFocusChangeListener((v, focused) -> {
                ControlMotion.libraryFocus(v, focused, animations, reducedMotion);
                if (focused) {
                    selectedPackage = pkg;
                    if (!TextUtils.equals(instructions.getText(), activity.getText(R.string.library_hint_open)))
                        instructions.setText(R.string.library_hint_open);
                    int index = holder.getBindingAdapterPosition();
                    if (index != RecyclerView.NO_POSITION) {
                        interactForFocus(index);
                        selectedPosition = index;
                        if (pendingFocusPosition != index) preferredColumn = index % LibraryGrid.COLUMNS;
                        pendingFocusPosition = -1;
                    }
                    queueHighlight(v);
                }
            });
            holder.itemView.setOnKeyListener((v, key, event) -> {
                ControlMotion.libraryKey(v, key, event, animations, reducedMotion);
                int direction = key == KeyEvent.KEYCODE_DPAD_LEFT ? LibraryGrid.LEFT
                        : key == KeyEvent.KEYCODE_DPAD_RIGHT ? LibraryGrid.RIGHT
                        : key == KeyEvent.KEYCODE_DPAD_UP ? LibraryGrid.UP
                        : key == KeyEvent.KEYCODE_DPAD_DOWN ? LibraryGrid.DOWN : 0;
                if (direction == 0) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int index = pendingFocusPosition >= 0 ? pendingFocusPosition : holder.getBindingAdapterPosition();
                    if (index != RecyclerView.NO_POSITION) {
                        int next = LibraryGrid.next(index, apps.size(), direction, rtl, preferredColumn);
                        if (direction == LibraryGrid.LEFT || direction == LibraryGrid.RIGHT)
                            preferredColumn = next % LibraryGrid.COLUMNS;
                        if (next != index) focusCard(next);
                    }
                }
                return true;
            });
        }
        @Override public int getItemCount() { return apps.size(); }
        @Override public void onViewRecycled(@NonNull Row holder) {
            ControlMotion.reset(holder.itemView);
            super.onViewRecycled(holder);
        }
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final Drawable homeBadge;
        final Drawable recentBadge;

        Row(View view, Drawable homeBadge, Drawable recentBadge) {
            super(view);
            this.homeBadge = homeBadge;
            this.recentBadge = recentBadge;
        }
    }
}
