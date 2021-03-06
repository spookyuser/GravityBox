/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.q.gravitybox;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.ceco.q.gravitybox.adapters.*;
import com.ceco.q.gravitybox.quicksettings.OOSThemeColorUtils;

public class ModPowerMenu {
    private static final String TAG = "GB:ModPowerMenu";
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String CLASS_GLOBAL_ACTIONS = "com.android.systemui.globalactions.GlobalActionsDialog";
    public static final String CLASS_ACTION = CLASS_GLOBAL_ACTIONS + ".Action";
    private static final boolean DEBUG = false;
    private static final String ITEM_LAYOUT_PACKAGE = PACKAGE_NAME;

    private static String mRebootStr;
    private static String mRebootSoftStr;
    private static String mRecoveryStr;
    private static String mBootloaderStr;
    private static Drawable mRebootIcon;
    private static Drawable mRebootSoftIcon;
    private static Drawable mRecoveryIcon;
    private static Drawable mBootloaderIcon;
    private static Drawable mExpandedDesktopIcon;
    private static List<IIconListAdapterItem> mRebootItemList;
    private static String mRebootConfirmStr;
    private static String mRebootConfirmRecoveryStr;
    private static String mRebootConfirmBootloaderStr;
    private static String mExpandedDesktopStr;
    private static String mExpandedDesktopOnStr;
    private static String mExpandedDesktopOffStr;
    private static Unhook mRebootActionHook;
    private static Unhook mRebootActionShowHook;
    private static Object mRebootActionItem;
    private static boolean mRebootActionItemStockExists;
    private static Object mExpandedDesktopAction;
    private static boolean mRebootConfirmRequired;
    private static boolean mRebootAllowOnLockscreen;
    private static boolean mAllowSoftReboot;
    private static Object mGlobalActionsDialog;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {

        try {
            final Class<?> globalActionsClass = XposedHelpers.findClass(CLASS_GLOBAL_ACTIONS, classLoader);
            final Class<?> actionClass = XposedHelpers.findClass(CLASS_ACTION, classLoader);

            XposedBridge.hookAllConstructors(globalActionsClass, new XC_MethodHook() {
               @Override
               protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                   Context ctx = (Context) param.args[0];
                   mGlobalActionsDialog = param.thisObject;
                   Resources res = ctx.getResources();
                   Context gbContext = Utils.getGbContext(ctx, res.getConfiguration());

                   int rebootStrId = res.getIdentifier("factorytest_reboot", "string", PACKAGE_NAME);
                   mRebootStr  = (rebootStrId == 0) ? gbContext.getString(R.string.reboot) : res.getString(rebootStrId);
                   mRebootSoftStr = gbContext.getString(R.string.reboot_soft);
                   mRecoveryStr = gbContext.getString(R.string.poweroff_recovery);
                   mBootloaderStr = gbContext.getString(R.string.poweroff_bootloader);
                   mExpandedDesktopStr = gbContext.getString(R.string.action_expanded_desktop_title);
                   mExpandedDesktopOnStr = gbContext.getString(R.string.action_expanded_desktop_on);
                   mExpandedDesktopOffStr = gbContext.getString(R.string.action_expanded_desktop_off);

                   mRebootIcon = gbContext.getDrawable(R.drawable.ic_lock_reboot);
                   mRebootSoftIcon = gbContext.getDrawable(R.drawable.ic_lock_reboot_soft);
                   mRecoveryIcon = gbContext.getDrawable(R.drawable.ic_lock_recovery);
                   mBootloaderIcon = gbContext.getDrawable(R.drawable.ic_lock_bootloader);
                   mExpandedDesktopIcon = gbContext.getDrawable(R.drawable.ic_lock_expanded_desktop);

                   mRebootItemList = new ArrayList<>();
                   mRebootItemList.add(new BasicIconListItem(mRebootStr, null, mRebootIcon, null));
                   mRebootItemList.add(new BasicIconListItem(mRebootSoftStr, null, mRebootSoftIcon, null));
                   mRebootItemList.add(new BasicIconListItem(mRecoveryStr, null, mRecoveryIcon, null));
                   mRebootItemList.add(new BasicIconListItem(mBootloaderStr, null, mBootloaderIcon, null));

                   mRebootConfirmStr = gbContext.getString(R.string.reboot_confirm);
                   mRebootConfirmRecoveryStr = gbContext.getString(R.string.reboot_confirm_recovery);
                   mRebootConfirmBootloaderStr = gbContext.getString(R.string.reboot_confirm_bootloader);

                   if (DEBUG) log("GlobalActions constructed, resources set.");
               }
            });

            XposedHelpers.findAndHookMethod(globalActionsClass, "createDialog", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    if (mRebootActionHook != null) {
                        if (DEBUG) log("Unhooking previous hook of reboot action item");
                        mRebootActionHook.unhook();
                        mRebootActionHook = null;
                    }
                    if (mRebootActionShowHook != null) {
                        mRebootActionShowHook.unhook();
                        mRebootActionShowHook = null;
                    }
                }

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final Context ctx = ((Dialog) param.getResult()).getContext();

                    mRebootConfirmRequired = prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_REBOOT_CONFIRM_REQUIRED, true);
                    mRebootAllowOnLockscreen = prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_REBOOT_ALLOW_ON_LOCKSCREEN, false);
                    mAllowSoftReboot = prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_REBOOT_ALLOW_SOFTREBOOT, false);

                    @SuppressWarnings("unchecked")
                    List<Object> mItems = (List<Object>) XposedHelpers.getObjectField(param.thisObject, "mItems");
                    BaseAdapter mAdapter = (BaseAdapter) XposedHelpers.getObjectField(param.thisObject, "mAdapter");
                    int index = 1;

                    // try to find out if reboot action item already exists in the list of GlobalActions items
                    // strategy:
                    // 1) check if Action has mIconResId field or mMessageResId field
                    // 2) check if the name of the corresponding resource contains "reboot" or "restart" substring
                    if (mRebootActionItem == null) {
                        if (DEBUG) log("Searching for existing reboot action item...");
                        Resources res = ctx.getResources();
                        for (Object o : mItems) {
                            // search for drawable
                            try {
                                Field f = XposedHelpers.findField(o.getClass(), "mIconResId");
                                String resName = res.getResourceEntryName((Integer) f.get(o)).toLowerCase(Locale.US);
                                if (DEBUG) log("Drawable resName = " + resName);
                                if (resName.contains("reboot") || resName.contains("restart")) {
                                    mRebootActionItem = o;
                                    break;
                                }
                            } catch (NoSuchFieldError | Resources.NotFoundException |
                                     IllegalArgumentException nfe) {
                                // continue
                            }

                            if (mRebootActionItem == null) {
                                // search for text
                                try {
                                    Field f = XposedHelpers.findField(o.getClass(), "mMessageResId");
                                    String resName = res.getResourceEntryName((Integer) f.get(o)).toLowerCase(Locale.US);
                                    if (DEBUG) log("Text resName = " + resName);
                                    if (resName.contains("reboot") || resName.contains("restart")) {
                                        mRebootActionItem = o;
                                        break;
                                    }
                                } catch (NoSuchFieldError | Resources.NotFoundException |
                                         IllegalArgumentException nfe) {
                                    // continue
                                }
                            }
                        }

                        if (mRebootActionItem == null) {
                            if (DEBUG) log("Existing Reboot action item NOT found! Creating new RebootAction item");
                            mRebootActionItemStockExists = false;
                            mRebootActionItem = Proxy.newProxyInstance(classLoader, new Class<?>[] { actionClass }, 
                                    new RebootAction());
                        } else {
                            if (DEBUG) log("Existing Reboot action item found!");
                            mRebootActionItemStockExists = true;
                        }
                    }

                    // Add/hook reboot action if enabled
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_POWEROFF_ADVANCED, false)) {
                        if (mRebootActionItemStockExists) {
                            mRebootActionHook = XposedHelpers.findAndHookMethod(mRebootActionItem.getClass(), 
                                    "onPress", new XC_MethodReplacement () {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam param) {
                                    RebootAction.showRebootDialog(ctx);
                                    return null;
                                }
                            });
                            mRebootActionShowHook = XposedHelpers.findAndHookMethod(mRebootActionItem.getClass(),
                                    "showDuringKeyguard", new XC_MethodReplacement() {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam param) {
                                    return mRebootAllowOnLockscreen;
                                }
                            });
                        } else {
                            // add to the second position
                            mItems.add(index, mRebootActionItem);
                        }
                        index++;
                    } else if (mRebootActionItemStockExists) {
                        index++;
                    }

                    // Add Expanded Desktop action if enabled
                    if (ExpandedDesktopAction.isExpandedDesktopEnabled(prefs) &&
                            prefs.getBoolean(GravityBoxSettings.PREF_KEY_POWERMENU_EXPANDED_DESKTOP, true)) {
                        if (mExpandedDesktopAction == null) {
                            mExpandedDesktopAction = Proxy.newProxyInstance(classLoader, 
                                    new Class<?>[] { actionClass },
                                        new ExpandedDesktopAction());
                            if (DEBUG) log("mExpandedDesktopAction created");
                        }
                        mItems.add(index++, mExpandedDesktopAction);
                    }

                    mAdapter.notifyDataSetChanged();
                }
            });

            XC_MethodHook showDialogHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) {
                    prefs.reload();
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_POWERMENU_DISABLE_ON_LOCKSCREEN, false)) {
                        boolean locked = (Boolean) param.args[0];
                        if (!locked) {
                            // double-check using keyguard manager
                            try {
                                Context context = (Context) XposedHelpers.getObjectField(
                                        param.thisObject, "mContext");
                                KeyguardManager km = (KeyguardManager) context.getSystemService(
                                        Context.KEYGUARD_SERVICE);
                                locked = km.isKeyguardLocked();
                            } catch (Throwable ignored) { }
                        }

                        if (locked) {
                            Object wmf = XposedHelpers.getObjectField(param.thisObject, "mWindowManagerFuncs");
                            XposedHelpers.callMethod(wmf, "onGlobalActionsShown");
                            createDialogIfNull();
                            param.setResult(null);
                        }
                    }
                }
            };
            XposedBridge.hookAllMethods(globalActionsClass, "showDialog", showDialogHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static void createDialogIfNull() {
        try {
            if (XposedHelpers.getObjectField(mGlobalActionsDialog, "mDialog") == null) {
                XposedHelpers.setObjectField(mGlobalActionsDialog, "mDialog",
                        XposedHelpers.callMethod(mGlobalActionsDialog, "createDialog"));
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, "Error in createDialogIfNull:", t);
        }
    }

    private static void reboot() {
        try {
            Object wmf = XposedHelpers.getObjectField(mGlobalActionsDialog, "mWindowManagerFuncs");
            Method m = XposedHelpers.findMethodExactIfExists(wmf.getClass(), "reboot",
                    boolean.class, String.class);
            if (m != null) {
                m.invoke(wmf, false, "reboot");
            } else {
                XposedHelpers.callMethod(wmf, "reboot", false);
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private static ContextThemeWrapper getThemedContext(Context ctx, int defaultThemeResId) {
        int resId = ctx.getResources().getIdentifier("qs_theme", "style", ctx.getPackageName());
        if (resId == 0) {
            resId = defaultThemeResId;
        }
        return new ContextThemeWrapper(ctx, resId);
    }

    private static class RebootAction implements InvocationHandler {
        private Context mContext;

        public RebootAction() {
        }

        public static void showRebootDialog(final Context context) {
            if (context == null) {
                if (DEBUG) log("Context is null - aborting");
                return;
            }

            try {
                if (DEBUG) log("about to build reboot dialog");

                List<IIconListAdapterItem> list =
                        new ArrayList<>(mRebootItemList);
                if (!mAllowSoftReboot) {
                    list.remove(1);
                }
                final ContextThemeWrapper ctw = getThemedContext(context, android.R.style.Theme_Material_Dialog_Alert);
                int color = Utils.isOxygenOsRom() ?
                        OOSThemeColorUtils.getColorAccent(context) :
                        ColorUtils.getColorFromStyleAttr(ctw, android.R.attr.colorControlNormal);
                mRebootIcon.setTint(color);
                mRebootSoftIcon.setTint(color);
                mRecoveryIcon.setTint(color);
                mBootloaderIcon.setTint(color);
                AlertDialog.Builder builder = new AlertDialog.Builder(ctw)
                    .setTitle(mRebootStr)
                    .setAdapter(new IconListAdapter(ctw, list), (dialog, which) -> {
                        dialog.dismiss();
                        if (DEBUG) log("onClick() item = " + which);
                        handleReboot(ctw, mRebootStr,
                                (which > 0 && !mAllowSoftReboot) ? (which+1) : which);
                    })
                    .setNegativeButton(android.R.string.no,
                            (dialog, which) -> dialog.dismiss());
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                dialog.show();
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }

        private static void doReboot(Context context, int mode) {
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (mode == 0) {
                reboot();
            } else if (mode == 1) {
                Utils.performSoftReboot();
            } else if (mode == 2) {
                pm.reboot("recovery");
            } else if (mode == 3) {
                pm.reboot("bootloader");
            }
        }

        private static void handleReboot(final Context context, String caption, final int mode) {
            try {
                if (!mRebootConfirmRequired) {
                    doReboot(context, mode);
                } else {
                    String message = mRebootConfirmStr;
                    if (mode == 2) {
                        message = mRebootConfirmRecoveryStr;
                    } else if (mode == 3) {
                        message = mRebootConfirmBootloaderStr;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(getThemedContext(
                            context,android.R.style.Theme_Material_Dialog_Alert))
                        .setTitle(caption)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            dialog.dismiss();
                            doReboot(context, mode);
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());
                    AlertDialog dialog = builder.create();
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                    dialog.show();
                }
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();

            if (methodName.equals("create")) {
                mContext = (Context) args[0];
                Resources res = mContext.getResources();
                LayoutInflater li = (LayoutInflater) args[3];
                int layoutId = res.getIdentifier(
                        "global_actions_grid_item", "layout", ITEM_LAYOUT_PACKAGE);
                View v = li.inflate(layoutId, (ViewGroup) args[2], false);

                ImageView icon = v.findViewById(res.getIdentifier(
                        "icon", "id", "android"));
                icon.setImageDrawable(mRebootIcon);

                TextView messageView = v.findViewById(res.getIdentifier(
                        "message", "id", "android"));
                messageView.setText(mRebootStr);

                TextView statusView = v.findViewById(res.getIdentifier(
                        "status", "id", "android"));
                statusView.setVisibility(View.GONE);

                return v;
            } else if (methodName.equals("onPress")) {
                showRebootDialog(mContext);
                return null;
            } else if (methodName.equals("onLongPress")) {
                handleReboot(mContext, mRebootStr, 0);
                return true;
            } else if (methodName.equals("showDuringKeyguard")) {
                return mRebootAllowOnLockscreen;
            } else if (methodName.equals("showBeforeProvisioning")) {
                return true;
            } else if (methodName.equals("isEnabled")) {
                return true;
            } else if (methodName.equals("showConditional")) {
                return true;
            } else if (methodName.equals("getLabelForAccessibility")) {
                return null;
            } else if (methodName.equals("shouldBeSeparated")) {
                return false;
            } else {
                GravityBox.log(TAG, "RebootAction: Unhandled invocation method: " + methodName);
                return null;
            }
        }
    }

    private static class ExpandedDesktopAction implements InvocationHandler {
        private Context mContext;
        private TextView mStatus;
        private Handler mHandler;

        public ExpandedDesktopAction() {
        }

        public static boolean isExpandedDesktopEnabled(XSharedPreferences prefs) {
            int edMode = GravityBoxSettings.ED_DISABLED;
            try {
                edMode = Integer.valueOf(prefs.getString(GravityBoxSettings.PREF_KEY_EXPANDED_DESKTOP, "0"));
            } catch(NumberFormatException nfe) {
                GravityBox.log(TAG, "Invalid value for PREF_KEY_EXPANDED_DESKTOP preference");
            }
            return (edMode != GravityBoxSettings.ED_DISABLED);
        }

        public static boolean isExpandedDesktopOn(Context context) {
            return (Settings.Global.getInt(context.getContentResolver(),
                    ModExpandedDesktop.SETTING_EXPANDED_DESKTOP_STATE, 0) == 1);
        }

        private void updateStatus() {
            mStatus.setText(isExpandedDesktopOn(mContext) ? 
                    mExpandedDesktopOnStr : mExpandedDesktopOffStr);
        }

        private void toggleStatus() {
            try {
                mHandler.postDelayed(() -> {
                    Settings.Global.putInt(mContext.getContentResolver(),
                            ModExpandedDesktop.SETTING_EXPANDED_DESKTOP_STATE,
                            isExpandedDesktopOn(mContext) ? 0 : 1);
                    updateStatus();
                }, 200);
            } catch (Throwable t) {
                GravityBox.log(TAG, t);
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();

            if (methodName.equals("create")) {
                mContext = (Context) args[0];
                mHandler = new Handler();

                Resources res = mContext.getResources();
                LayoutInflater li = (LayoutInflater) args[3];
                int layoutId = res.getIdentifier(
                        "global_actions_grid_item", "layout", ITEM_LAYOUT_PACKAGE);
                View v = li.inflate(layoutId, (ViewGroup) args[2], false);

                ImageView icon = v.findViewById(res.getIdentifier(
                        "icon", "id", "android"));
                icon.setImageDrawable(mExpandedDesktopIcon);

                TextView messageView = v.findViewById(res.getIdentifier(
                        "message", "id", "android"));
                messageView.setText(mExpandedDesktopStr);

                mStatus = v.findViewById(res.getIdentifier(
                        "status", "id", "android"));
                mStatus.setVisibility(View.GONE);
                updateStatus();

                return v;
            } else if (methodName.equals("onPress")) {
                toggleStatus();
                return null;
            } else if (methodName.equals("onLongPress")) {
                return false;
            } else if (methodName.equals("showDuringKeyguard")) {
                return true;
            } else if (methodName.equals("showBeforeProvisioning")) {
                return true;
            } else if (methodName.equals("isEnabled")) {
                return true;
            } else if (methodName.equals("showConditional")) {
                return true;
            } else if (methodName.equals("getLabelForAccessibility")) {
                return null;
            } else if (methodName.equals("shouldBeSeparated")) {
                return false;
            } else {
                GravityBox.log(TAG, "ExpandedDesktopAction: Unhandled invocation method: " + methodName);
                return null;
            }
        }
    }
}
