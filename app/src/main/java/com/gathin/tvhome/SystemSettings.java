package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;

/** The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 *  from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS). */
final class SystemSettings {
    private SystemSettings() {}

    static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS resolves the standard actions to a CTS compliance stub that opens nothing;
        // treat it as missing so the vendor fallback runs.
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard;

        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // An installed package alone does not establish that this exact exported activity exists.
            try {
                ActivityInfo activity = pm.getActivityInfo(component, 0);
                if (!activity.enabled || !activity.exported || !activity.applicationInfo.enabled) continue;
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            return new Intent().setComponent(component);
        }
        return standard;
    }

    /** A real OEM input picker, excluding our optional forwarding activity to avoid recursion. */
    static Intent inputPicker(Context context) {
        Intent standard = new Intent("com.android.tv.action.VIEW_INPUTS");
        for (ResolveInfo resolved : context.getPackageManager().queryIntentActivities(standard,
                PackageManager.MATCH_DEFAULT_ONLY)) {
            ActivityInfo activity = resolved.activityInfo;
            if (activity == null || !activity.enabled || !activity.exported
                    || !activity.applicationInfo.enabled
                    || context.getPackageName().equals(activity.packageName)) continue;
            return new Intent(standard).setComponent(new ComponentName(activity.packageName, activity.name));
        }
        return null;
    }
}
