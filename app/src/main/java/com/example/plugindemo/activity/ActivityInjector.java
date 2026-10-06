/*
 * Copyright (C) 2005-2017 Qihoo 360 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.plugindemo.activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;

import com.example.plugindemo.PluginHostApplication;
import com.example.plugindemo.RePluginInternal;
import com.example.plugindemo.helper.LogDebug;
import com.example.plugindemo.loader.PmBase;
import com.example.plugindemo.model.Plugin;

import java.util.HashMap;

import static com.example.plugindemo.helper.LogDebug.LOG;
import static com.example.plugindemo.helper.LogDebug.PLUGIN_TAG;


/**
 * 根据需要来Inject一些Activity的特性，使其更像一个App中的Activity
 *
 * @author RePlugin Team
 */

public class ActivityInjector {

    public static final String TAG = "activity-injector";

    /**
     * 填充一些必要的东西到Activity中
     *
     * @param activity     Activity对象
     * @param plugin       插件名
     * @param realActivity 真实的（非坑位的）Activity名字
     * @return 是否Inject成功
     */
    public static boolean inject(Activity realActivity, String plugin) {

        // 优先取「插件自己声明的 ActivityInfo」：它的 label/icon 是插件包里的资源 id，
        // 正好能用插件 Activity 的 Resources 解析出来，这才是插件 Activity 该有的标题和图标。
        ActivityInfo ai = getPluginActivity(realActivity, plugin);
        if (ai == null) {
            // 兜底：拿不到插件信息时（例如插件还没加载）退回宿主自己的 ActivityInfo
            ai = getActivity(realActivity);
        }
        return ai != null && inject(realActivity, ai, getFrameworkVersion());

    }

    /**
     * 从插件自己的 ComponentList 里查这个 Activity 的 ActivityInfo
     */
    private static ActivityInfo getPluginActivity(Activity realActivity, String plugin) {
        PmBase pmBase = PluginHostApplication.sPmBase;
        if (pmBase == null || TextUtils.isEmpty(plugin)) {
            return null;
        }
        Plugin p = pmBase.getPlugin(plugin);
        if (p == null || p.mLoader == null || p.mLoader.mComponents == null) {
            return null;
        }
        return p.mLoader.mComponents.getActivity(realActivity.getClass().getName());
    }

    /**
     * 从宿主自己的 PackageInfo 里查 ActivityInfo。注意插件 Activity 不属于宿主包，查不到。
     */
    private static ActivityInfo getActivity(Activity realActivity) {
        PackageInfo pi = null;
        try {
            // 必须带 GET_ACTIVITIES，否则 PackageInfo.activities 是 null
            pi = realActivity.getApplicationContext().getPackageManager()
                    .getPackageInfo(realActivity.getApplicationContext().getPackageName(),
                            PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (pi == null || pi.activities == null || pi.activities.length == 0) {
            return null;
        }
        // NOTE 插件 Activity 的类不属于宿主包，这里是查不到的；能匹配上的只有宿主自己的 Activity，
        // 匹配不到时退回第一个，保证「最近任务」至少有个描述可用。
        String name = realActivity.getClass().getName();
        for (ActivityInfo ai : pi.activities) {
            if (TextUtils.equals(ai.name, name)) {
                return ai;
            }
        }
        return pi.activities[0];
    }


    private static int getFrameworkVersion() {
        return 18;
    }


    private static boolean inject(Activity activity, ActivityInfo ai, int frameworkVer) {
        // 可根据插件Activity的描述（android:label、android:icon）来设置Task在“最近应用程序”中的显示
        // 注意：框架版本需 >= 4，否则仍沿用Application的Label和Icon
        if (frameworkVer >= 4) {
            injectTaskDescription(activity, ai);
        }
        return true;
    }

    /**
     * 可根据插件Activity的描述（android:label、android:icon）来设置Task在“最近应用程序”中的显示 <p>
     * 注意：Android 4.x及以下暂不支持 <p>
     * Author: Jiongxuan Zhang
     */
    private static void injectTaskDescription(Activity activity, ActivityInfo ai) {
        // Android 4.x及以下暂不支持
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (activity == null || ai == null) {
            return;
        }

        if (LOG) {
            LogDebug.d(TAG, "activity = " + activity);
            LogDebug.d(TAG, "ai = " + ai);
        }

        // 获取 activity label
        String label = getLabel(activity, ai);
        // 如果获取 label 失败（可能性极小），则不修改 TaskDescription
        if (TextUtils.isEmpty(label)) {
            return;
        }

        // 获取 ICON
        Bitmap bitmap = getIcon(activity, ai);

        // FIXME color的透明度需要在Theme中的colorPrimary中获取，先不实现
        ActivityManager.TaskDescription td;
        if (bitmap != null) {
            td = new ActivityManager.TaskDescription(label, bitmap);
        } else {
            td = new ActivityManager.TaskDescription(label);
        }

        if (LOG) {
            LogDebug.d(TAG, "td = " + td);
        }

        activity.setTaskDescription(td);
    }

    /**
     * 获取 activity 的 label 属性
     */
    private static String getLabel(Activity activity, ActivityInfo ai) {
        String label;
        Resources res = activity.getResources();

        // 获取 Activity label（如有）
        label = getLabelById(res, ai.labelRes);

        // 获取插件 Application Label（如有）
        if (TextUtils.isEmpty(label)) {
            label = getLabelById(res, ai.applicationInfo.labelRes);
        }

        // 获取宿主 App label
        if (TextUtils.isEmpty(label)) {
            Context appContext = RePluginInternal.getAppContext();
            Resources appResource = appContext.getResources();
            ApplicationInfo appInfo = appContext.getApplicationInfo();
            label = getLabelById(appResource, appInfo.labelRes);
        }

        if (LOG) {
            LogDebug.d(TAG, "label = " + label);
        }
        return label;
    }

    private static String getLabelById(Resources res, int id) {
        if (id == 0) {
            return null;
        }
        try {
            return res.getString(id);
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取 activity 的 icon 属性
     */
    private static Bitmap getIcon(Activity activity, ActivityInfo ai) {
        Drawable iconDrawable;
        Resources res = activity.getResources();

        // 获取 Activity icon
        iconDrawable = getIconById(res, ai.icon);

        // 获取插件 Application Icon
        if (iconDrawable == null) {
            iconDrawable = getIconById(res, ai.applicationInfo.icon);
        }

        // 获取 App(Host) Icon
        if (iconDrawable == null) {
            Context appContext = RePluginInternal.getAppContext();
            Resources appResource = appContext.getResources();
            ApplicationInfo appInfo = appContext.getApplicationInfo();
            iconDrawable = getIconById(appResource, appInfo.icon);
        }

        Bitmap bitmap = null;
        if (iconDrawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) iconDrawable).getBitmap();
        }

        if (LOG) {
            LogDebug.d(TAG, "bitmap = " + bitmap);
        }
        return bitmap;
    }

    private static Drawable getIconById(Resources res, int id) {
        if (id == 0) {
            return null;
        }
        try {
            return res.getDrawable(id);
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Class类名 - Activity的Map表
     */
    final HashMap<String, ActivityInfo> mActivities = new HashMap<>();


}
