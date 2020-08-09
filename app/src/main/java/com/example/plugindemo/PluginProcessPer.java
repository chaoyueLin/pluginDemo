package com.example.plugindemo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;

import com.example.plugindemo.activity.ActivityInjector;
import com.example.plugindemo.helper.LogDebug;
import com.example.plugindemo.loader.PmBase;
import com.example.plugindemo.model.Plugin;

import java.util.HashSet;

import static com.example.plugindemo.helper.LogDebug.LOG;
import static com.example.plugindemo.helper.LogDebug.PLUGIN_TAG;

/**
 *  创建人：linchaoyue
 *  创建时间：2019-12-19 10:36
 * 描述：
 */
public class PluginProcessPer {


    private final static PmBase mPluginMgr=PluginHostApplication.sPmBase;

    /**
     * 加载插件；找到目标Activity；搜索匹配容器；加载目标Activity类；建立临时映射；返回容器
     *
     * @param plugin   插件名称
     * @param activity Activity 名称
     * @param intent   调用者传入的 Intent
     * @return 坑位
     */
    static String bindActivity(String plugin, String activity, Intent intent) {

        /* 获取插件对象 */
        Plugin p = mPluginMgr.loadAppPlugin(plugin);
        if (p == null) {
            if (LOG) {
                LogDebug.w(PLUGIN_TAG, "PACM: bindActivity: may be invalid plugin name or load plugin failed: plugin=" + plugin);
            }
            return null;
        }

        /* 获取 ActivityInfo */
        ActivityInfo ai = p.mLoader.mComponents.getActivity(activity);
        if (ai == null) {
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "PACM: bindActivity: activity not found: activity=" + activity);
            }
            return null;
        }

        if (ai.processName == null) {
            ai.processName = ai.applicationInfo.processName;
        }
        if (ai.processName == null) {
            ai.processName = ai.packageName;
        }

        /* 获取 Container 就是目标的activity*/
        String container=MainActivity.ACTIVITY_FROM;



        if (TextUtils.isEmpty(container)) {
            if (LOG) {
                LogDebug.w(PLUGIN_TAG, "PACM: bindActivity: activity container is empty");
            }
            return null;
        }

        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "PACM: bindActivity: lookup activity container: container=" + container);
        }

        /* 检查 activity 是否存在 */
        Class<?> c = null;
        try {
            c = p.mLoader.mClassLoader.loadClass(activity);
        } catch (Throwable e) {
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, e.getMessage(), e);
            }
        }
        if (c == null) {
            if (LOG) {
                LogDebug.w(PLUGIN_TAG, "PACM: bindActivity: plugin activity class not found: c=" + activity);
            }
            return null;
        }

        return container;
    }
    static final String INTENT_KEY_THEME_ID = "__themeId";

    public static ComponentName loadPluginActivity(Intent intent, String plugin, String activity) {

        ActivityInfo ai = null;
        String container = null;

        try {
            // 获取 ActivityInfo(可能是其它插件的 Activity，所以这里使用 pair 将 pluginName 也返回)
            ai = getActivityInfo(plugin, activity, intent);
            if (ai == null) {
                if (LOG) {
                    LogDebug.d(PLUGIN_TAG, "PACM: bindActivity: activity not found");
                }
                return null;
            }

            // 存储此 Activity 在插件 Manifest 中声明主题到 Intent
            intent.putExtra(INTENT_KEY_THEME_ID, ai.theme);
            if (LOG) {
                LogDebug.d("theme", String.format("intent.putExtra(%s, %s);", ai.name, ai.theme));
            }
            // 容器选择（启动目标进程）
            // 远程分配坑位
            container = bindActivity(plugin, ai.name, intent);
            if (LOG) {
                LogDebug.i(PLUGIN_TAG, "alloc success: container=" + container + " plugin=" + plugin + " activity=" + activity);
            }
        } catch (Throwable e) {
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, "l.p.a spp|aac: " + e.getMessage(), e);
            }
        }
        // 分配失败
        if (TextUtils.isEmpty(container)) {
            return null;
        }

        return new ComponentName(PluginHostApplication.mContext.getPackageName(), container);
    }

    /**
     * 根据条件，查找 ActivityInfo 对象
     *
     * @param plugin   插件名称
     * @param activity Activity 名称
     * @param intent   调用者传递过来的 Intent
     * @return 插件中 Activity 的 ActivityInfo
     */
    private static ActivityInfo getActivityInfo(String plugin, String activity, Intent intent) {
        // 获取插件对象
        Plugin p = mPluginMgr.loadAppPlugin(plugin);
        if (p == null) {
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "PACM: bindActivity: may be invalid plugin name or load plugin failed: plugin=" + p);
            }
            return null;
        }

        ActivityInfo ai = null;

        // activity 不为空时，从插件声明的 Activity 集合中查找
        if (!TextUtils.isEmpty(activity)) {
            ai = p.mLoader.mComponents.getActivity(activity);
        } else {
            // activity 为空时，根据 Intent 匹配

        }
        return ai;
    }

    // FIXME 建议去掉plugin和activity参数，直接用intent代替
    /**
     * @hide 内部方法，插件框架使用
     * 启动一个插件中的activity，如果插件不存在会触发下载界面
     * @param context 应用上下文或者Activity上下文
     * @param intent
     * @param plugin 插件名
     * @param activity 待启动的activity类名
     * @return 插件机制层是否成功，例如没有插件存在、没有合适的Activity坑
     */
    public static boolean startActivity(Context context, Intent intent, String plugin, String activity) {
        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "start activity: intent=" + intent + " plugin=" + plugin + " activity=" + activity  + " download=" );
        }

        ComponentName cn = loadPluginActivity(intent, plugin, activity);
        if (cn == null) {
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "plugin cn not found: intent=" + intent + " plugin=" + plugin + " activity=" + activity );
            }
            return false;
        }

        // 将Intent指向到“坑位”。这样：
        // from：插件原Intent
        // to：坑位Intent
        intent.setComponent(cn);

        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "start activity: real intent=" + intent);
        }
        context.startActivity(intent);
        return true;
    }

    fetchviewbyplugin
    public String fetchPluginName(ClassLoader cl) {
        Plugin p = mPluginMgr.lookupPlugin(cl);
        if (p == null) {
            // 没有拿到插件的
            return null;
        }
        return p.mInfo.getName();
    }


    /**
     * @hide 内部方法，插件框架使用
     * 插件的Activity创建成功后通过此方法获取其base context
     * @param activity
     * @param newBase
     * @return 为Activity构造一个base Context
     */
    public static Context createActivityContext(Activity activity, Context newBase) {
        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "createActivityContext=" + activity.getClass().getName());
        }
        Plugin plugin = mPluginMgr.lookupPlugin(activity.getClass().getClassLoader());
        if (plugin == null) {
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "PACM: createActivityContext: can't found plugin object for activity=" + activity.getClass().getName());
            }
            return null;
        }

        return plugin.mLoader.createBaseContext(newBase);
    }

    /**
     * @hide 内部方法，插件框架使用
     * 插件的Activity的onCreate调用前调用此方法
     * @param activity
     * @param savedInstanceState
     */
    public static void handleActivityCreateBefore(Activity activity, Bundle savedInstanceState) {
        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "activity create before: " + activity.getClass().getName() + " this=" + activity.hashCode() + " taskid=" + activity.getTaskId());
        }

        // 对FragmentActivity做特殊处理
        if (savedInstanceState != null) {
            //
            savedInstanceState.setClassLoader(activity.getClassLoader());
            //
            try {
                savedInstanceState.remove("android:support:fragments");
            } catch (Throwable e) {
                if (LOG) {
                    LogDebug.e(PLUGIN_TAG, "a.c.b1: " + e.getMessage(), e);
                }
            }
        }

        // 对FragmentActivity做特殊处理
        Intent intent = activity.getIntent();
        if (intent != null) {
            intent.setExtrasClassLoader(activity.getClassLoader());
//            activity.setTheme(getThemeId(activity, intent));
        }
    }

    public static void handleActivityCreate(Activity activity, Bundle savedInstanceState) {
        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "activity create: " + activity.getClass().getName() + " this=" + activity.hashCode() + " taskid=" + activity.getTaskId());
        }

        //
        if (savedInstanceState != null) {
            savedInstanceState.setClassLoader(activity.getClassLoader());
        }

        //
        Intent intent = activity.getIntent();
        if (intent != null) {
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "set activity intent cl=" + activity.getClassLoader());
            }
            intent.setExtrasClassLoader(activity.getClassLoader());
        }

        // 开始填充一些必要的属性给Activity对象
        // Added by Jiongxuan Zhang
        ActivityInjector.inject(activity, PmBase.pluginName);
    }

}
