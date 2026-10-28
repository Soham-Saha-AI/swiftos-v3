package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** A staged app-order editor: the caller owns persistence, and Cancel never calls it. */
final class NumberedAppOrderDialog {
    static final class Item {
        final String key;
        final String label;

        Item(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private NumberedAppOrderDialog() { }

    /** Returns the shown editor (or null for a closing Activity), so its owner can dismiss it on leaving Home. */
    static Dialog show(Activity activity, List<Item> items, Consumer<List<String>> onSave) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(onSave, "onSave");
        if (activity.isFinishing() || activity.isDestroyed()) return null;

        // Never mutate the caller's list. A repeated component is one tile, even if labels differ.
        List<Item> staged = snapshot(items);
        if (staged.isEmpty()) {
            AlertDialog empty = new AlertDialog.Builder(activity)
                    .setTitle(R.string.numbered_order_title)
                    .setMessage(R.string.numbered_order_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
            empty.show();
            styleWindow(activity, empty);
            return empty;
        }

        AlertDialog[] child = new AlertDialog[1];
        ArrayAdapter<String> rows = rows(activity, labels(activity, staged));
        AlertDialog editor = new AlertDialog.Builder(activity)
                .setCustomTitle(header(activity, activity.getString(R.string.numbered_order_title),
                        activity.getString(R.string.numbered_order_hint, MainActivity.COLUMNS)))
                .setAdapter(rows, null)
                .setPositiveButton(R.string.numbered_order_save, (dialog, which) -> {
                    List<String> ordered = new ArrayList<>(staged.size());
                    for (Item item : staged) ordered.add(item.key);
                    onSave.accept(ordered);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        editor.setOnDismissListener(dialog -> {
            AlertDialog destination = child[0];
            child[0] = null;
            if (destination != null) destination.dismiss();
        });
        editor.show();
        styleWindow(activity, editor);
        ListView list = editor.getListView();
        styleList(activity, list);
        // Replace AlertDialog's auto-dismiss item handler: the editor stays open while sorting.
        list.setOnItemClickListener((parent, view, position, id) ->
                choosePosition(activity, editor, list, rows, staged, position, child));
        list.setSelection(0);
        list.requestFocus();
        return editor;
    }

    private static void choosePosition(Activity activity, AlertDialog editor, ListView editorList,
                                       ArrayAdapter<String> editorRows, List<Item> staged, int from,
                                       AlertDialog[] child) {
        if (activity.isFinishing() || activity.isDestroyed() || !editor.isShowing()
                || from < 0 || from >= staged.size()) return;
        if (child[0] != null && child[0].isShowing()) return;
        Item moving = staged.get(from);
        AlertDialog destination = new AlertDialog.Builder(activity)
                .setCustomTitle(header(activity,
                        activity.getString(R.string.numbered_order_move_title, moving.label),
                        activity.getString(R.string.numbered_order_destination_hint, staged.size())))
                .setSingleChoiceItems(labels(activity, staged).toArray(new String[0]), from,
                        (dialog, to) -> {
                            if (moveTo(staged, from, to)) {
                                editorRows.setNotifyOnChange(false);
                                editorRows.clear();
                                editorRows.addAll(labels(activity, staged));
                                editorRows.notifyDataSetChanged();
                            }
                            dialog.dismiss();
                            if (editor.isShowing()) {
                                editorList.setSelection(to);
                                editorList.requestFocus();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        child[0] = destination;
        destination.setOnDismissListener(dialog -> {
            if (child[0] == destination) child[0] = null;
        });
        destination.show();
        styleWindow(activity, destination);
        styleList(activity, destination.getListView());
    }

    /** Final, zero-based destination; removing the item shifts its neighbours without swapping them. */
    static <T> boolean moveTo(List<T> values, int from, int to) {
        if (values == null || from < 0 || to < 0 || from >= values.size() || to >= values.size()
                || from == to) return false;
        values.add(to, values.remove(from));
        return true;
    }

    static List<Item> snapshot(List<Item> items) {
        List<Item> copy = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (items == null) return copy;
        for (Item item : items) {
            if (item == null || item.key == null || item.key.trim().isEmpty() || !seen.add(item.key)) continue;
            String label = item.label == null || item.label.trim().isEmpty() ? item.key : item.label;
            copy.add(new Item(item.key, label));
        }
        return copy;
    }

    private static List<String> labels(Activity activity, List<Item> items) {
        List<String> labels = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            labels.add(activity.getString(R.string.numbered_order_row, i + 1, items.get(i).label));
        }
        return labels;
    }

    private static ArrayAdapter<String> rows(Activity activity, List<String> values) {
        return new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setTextColor(Color.WHITE);
                row.setTextSize(20);
                row.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
                row.setSingleLine(true);
                row.setEllipsize(TextUtils.TruncateAt.END);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(dp(activity, 52));
                row.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8));
                return row;
            }
        };
    }

    private static View header(Activity activity, String title, String hint) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 12));
        TextView name = new TextView(activity);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(26);
        name.setTypeface(activity.getResources().getFont(R.font.inter_semibold));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(name);
        TextView help = new TextView(activity);
        help.setText(hint);
        help.setTextColor(0xFFCFD5DE);
        help.setTextSize(15);
        help.setTypeface(activity.getResources().getFont(R.font.outfit_regular));
        help.setPadding(0, dp(activity, 8), 0, 0);
        header.addView(help);
        return header;
    }

    private static void styleList(Activity activity, ListView list) {
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(Math.max(list.getPaddingLeft(), dp(activity, 8)),
                Math.max(list.getPaddingTop(), dp(activity, 4)),
                Math.max(list.getPaddingRight(), dp(activity, 8)),
                Math.max(list.getPaddingBottom(), dp(activity, 4)));
        list.setSelector(activity.getDrawable(R.drawable.input_row_bg));
        list.setDivider(new ColorDrawable(0x14FFFFFF));
        list.setDividerHeight(dp(activity, 1));
        Config cfg = Config.load(activity);
        final View[] selected = {null};
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (selected[0] != null && selected[0] != view) ControlMotion.focus(selected[0], false, cfg.animations, cfg.reducedMotion, false);
                selected[0] = view;
                if (view != null) ControlMotion.focus(view, list.hasFocus(), cfg.animations, cfg.reducedMotion, false);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                if (selected[0] != null) ControlMotion.reset(selected[0]);
                selected[0] = null;
            }
        });
        list.setOnKeyListener((view, key, event) -> {
            View row = list.getSelectedView();
            if (row != null) ControlMotion.key(row, key, event, cfg.animations, cfg.reducedMotion, false);
            return false;
        });
        list.setOnFocusChangeListener((view, focused) -> {
            View row = list.getSelectedView();
            if (row != null) ControlMotion.focus(row, focused, cfg.animations, cfg.reducedMotion, false);
        });
    }

    private static void styleWindow(Activity activity, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(activity.getDrawable(R.drawable.glass_panel));
        int width = Math.min(dp(activity, 640), Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.86f));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        Config cfg = Config.load(activity);
        for (int id : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            TextView button = dialog.getButton(id);
            if (button == null) continue;
            button.setTextColor(Color.WHITE);
            button.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
            button.setBackground(activity.getDrawable(R.drawable.pill_bg));
            ControlMotion.bind(button, cfg.animations, cfg.reducedMotion);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
