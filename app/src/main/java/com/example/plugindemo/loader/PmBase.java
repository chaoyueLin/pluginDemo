package com.example.plugindemo.loader;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.example.plugindemo.MainActivity;
import com.example.plugindemo.helper.LogDebug;
import com.example.plugindemo.model.Plugin;
import com.example.plugindemo.model.PluginInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.plugindemo.helper.LogDebug.LOG;
import static com.example.plugindemo.helper.LogDebug.PLUGIN_TAG;

/**
 *  创建人：linchaoyue
 *  创建时间：2019-12-18 17:59
 * 描述：
 */
public class PmBase {
    public static  String pluginName="plugin_test";
    private Context mContext;
    /**
     *
     */
    private ClassLoader mClassLoader;

    private Map<String,String> activityMap=new HashMap<>();

    public PmBase(Context context) {
        //
        mContext = context;
        activityMap.put(MainActivity.ACTIVITY_FROM,MainActivity.ACTIVITY_TO);
    }

    /**
     * 所有插件
     */
    private final Map<String, Plugin> mPlugins = new ConcurrentHashMap<>();

    public Class<?> loadClass(String className) {
        //
        if (activityMap.containsKey(className)) {
            Class<?> c = resolveActivityClass(activityMap.get(className),pluginName);
            if (c != null) {
                return c;
            }

        }
        return null;
    }

    public Plugin lookupPlugin(ClassLoader loader) {
        for (Plugin p : mPlugins.values()) {
            if (p != null && p.getClassLoader() == loader) {
                return p;
            }
        }
        return null;
    }

    /**
     *
     */

    public Plugin getPlugin(String plugin) {
        return mPlugins.get(plugin);
    }

    final Plugin loadPackageInfoPlugin(String plugin) {
        Plugin p = Plugin.cloneAndReattach(mContext, mPlugins.get(plugin), mClassLoader);
        return loadPlugin(p, Plugin.LOAD_INFO, true);
    }

    final Plugin loadResourcePlugin(String plugin) {
        Plugin p = Plugin.cloneAndReattach(mContext, mPlugins.get(plugin), mClassLoader);
        return loadPlugin(p, Plugin.LOAD_RESOURCES, true);
    }

    final Plugin loadDexPlugin(String plugin) {
        Plugin p = Plugin.cloneAndReattach(mContext, mPlugins.get(plugin), mClassLoader);
        return loadPlugin(p, Plugin.LOAD_DEX, true);
    }
    public Plugin loadAppPlugin(String plugin) {
        callAttach();
        return loadPlugin(mPlugins.get(plugin), Plugin.LOAD_APP, true);
    }
    // 底层接口
    final Plugin loadPlugin(Plugin p, int loadType, boolean useCache) {
        if (p == null) {
            return null;
        }
        if (!p.load(loadType, useCache)) {
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, "pmb.lp: f to l. lt=" + loadType + "; i=" + p.mInfo);
            }
            return null;
        }
        return p;
    }

    public void callAttach() {
        mClassLoader = PmBase.class.getClassLoader();
        // 挂载
        for (Plugin p : mPlugins.values()) {
            p.attach(mContext, mClassLoader);
        }
    }

    public void putPluginObject(PluginInfo info, Plugin plugin) {
        if (mPlugins.containsKey(info.getName())) {
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "当前内置插件列表中已经有" + info.getName() + "，需要看看谁的版本号大。");
            }

            // 找到已经存在的
            Plugin existedPlugin = mPlugins.get(info.getPackageName());

        } else {
            // 同时加入PackageName和Alias（如有）
            pluginName=info.getName();
            mPlugins.put(info.getName(), plugin);
        }
    }

    public Class<?> resolveActivityClass(String container,String plugin) {
        String activity = container;
        Plugin p = loadAppPlugin(plugin);
        if (p == null) {
            // PACM: loadActivityClass, not found plugin
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, "load fail: c=" + container + " p=" + plugin + " t=" + activity);
            }
            return null;
        }

        ClassLoader cl = p.getClassLoader();
        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "PACM: loadActivityClass, plugin activity loader: in=" + container + " activity=" + activity);
        }
        Class<?> c = null;
        try {
            c = cl.loadClass(activity);
        } catch (Throwable e) {
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, e.getMessage(), e);
            }
        }
        if (LOG) {
            LogDebug.d(PLUGIN_TAG, "PACM: loadActivityClass, plugin activity loader: c=" + c + ", loader=" + cl);
        }

        return c;
    }

}
