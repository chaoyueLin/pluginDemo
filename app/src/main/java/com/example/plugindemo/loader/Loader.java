package com.example.plugindemo.loader;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.example.plugindemo.ComponentList;
import com.example.plugindemo.PluginDexClassLoader;
import com.example.plugindemo.helper.LogDebug;
import com.example.plugindemo.model.Plugin;
import com.example.plugindemo.model.PluginInfo;
import java.io.File;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.example.plugindemo.helper.LogDebug.LOADER_TAG;
import static com.example.plugindemo.helper.LogDebug.LOG;
import static com.example.plugindemo.helper.LogDebug.PLUGIN_TAG;

/**
 *  创建人：linchaoyue
 *  创建时间：2019-12-18 16:55
 * 描述：
 */
public class Loader {
    private final Context mContext;

    private final String mPluginName;

    final String mPath;

    final Plugin mPluginObj;

    PackageInfo mPackageInfo;

    Resources mPkgResources;

    public Context mPkgContext;

    public ClassLoader mClassLoader;
    /**
     * 记录所有缓存的Component列表
     */
    public ComponentList mComponents;

    /**
     * layout缓存：构造器表
     */
    HashMap<String, Constructor<?>> mConstructors = new HashMap<String, Constructor<?>>();

    /**
     * 初始化Loader对象
     *
     * @param p Plugin类的对象
     *          为何会反向依赖plugin对象？因为plugin.mInfo对象会发生变化，
     *          缓存plugin可以实时拿到最新的mInfo对象，防止出现问题
     *          FIXME 有优化空间，但改动量会很大，暂缓
     */
    public Loader(Context context, String name, String path, Plugin p) {
        mContext = context;
        mPluginName = name;
        mPath = path;
        mPluginObj = p;
    }

    public boolean isPackageInfoLoaded() {
        return mPackageInfo != null;
    }

    public boolean isResourcesLoaded() {
        return isPackageInfoLoaded() && mPkgResources != null;
    }

    public boolean isDexLoaded() {
        return isResourcesLoaded() && mClassLoader != null;
    }


    public  boolean loadDex(ClassLoader parent, int load) {
        try {
            PackageManager pm = mContext.getPackageManager();


            if (mPackageInfo == null) {
                // PackageInfo
                mPackageInfo = pm.getPackageArchiveInfo(mPath,
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS | PackageManager.GET_META_DATA);
                if (mPackageInfo == null || mPackageInfo.applicationInfo == null) {
                    if (LOG) {
                        LogDebug.d(PLUGIN_TAG, "get package archive info null");
                    }
                    mPackageInfo = null;
                    return false;
                }
                if (LOG) {
                    LogDebug.d(PLUGIN_TAG, "get package archive info, pi=" + mPackageInfo);
                }
                mPackageInfo.applicationInfo.sourceDir = mPath;
                mPackageInfo.applicationInfo.publicSourceDir = mPath;

                if (TextUtils.isEmpty(mPackageInfo.applicationInfo.processName)) {
                    mPackageInfo.applicationInfo.processName = mPackageInfo.applicationInfo.packageName;
                }

                // 添加针对SO库的加载
                // 此属性最终用于ApplicationLoaders.getClassLoader，在创建PathClassLoader时成为其参数
                // 这样findLibrary可不用覆写，即可直接实现SO的加载
                // Added by Jiongxuan Zhang
                PluginInfo pi = mPluginObj.mInfo;
                File ld = pi.getNativeLibsDir();
                mPackageInfo.applicationInfo.nativeLibraryDir = ld.getAbsolutePath();

            }


            // 创建或获取ComponentList表
            // Added by Jiongxuan Zhang

            if (mComponents == null) {
                // ComponentList
                mComponents = new ComponentList(mPackageInfo, mPath, mPluginObj.mInfo);

                // 动态注册插件中声明的 receiver
//                regReceivers();


                /* 只调整一次 */
                // 调整插件中组件的进程名称
                adjustPluginProcess(mPackageInfo.applicationInfo);

                // 调整插件中 Activity 的 TaskAffinity
                adjustPluginTaskAffinity(mPluginName, mPackageInfo.applicationInfo);
            }

            if (load == Plugin.LOAD_INFO) {
                return isPackageInfoLoaded();
            }


            // LOAD_RESOURCES和LOAD_ALL都会获取资源，但LOAD_INFO不可以（只允许获取PackageInfo）
            if (mPkgResources == null) {
                // Resources
                try {
                    if (LOG) {
                        // 如果是Debug模式的话，防止与Instant Run冲突，资源重新New一个
                        Resources r = pm.getResourcesForApplication(mPackageInfo.applicationInfo);
                        mPkgResources = new Resources(r.getAssets(), r.getDisplayMetrics(), r.getConfiguration());
                    } else {
                        mPkgResources = pm.getResourcesForApplication(mPackageInfo.applicationInfo);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    if (LOG) {
                        LogDebug.d(PLUGIN_TAG, e.getMessage(), e);
                    }
                    return false;
                }
                if (mPkgResources == null) {
                    if (LOG) {
                        LogDebug.d(PLUGIN_TAG, "get resources null");
                    }
                    return false;
                }
                if (LOG) {
                    LogDebug.d(PLUGIN_TAG, "get resources for app, r=" + mPkgResources);
                }

            }
            if (load == Plugin.LOAD_RESOURCES) {
                return isResourcesLoaded();
            }


            if (mClassLoader == null) {
                // ClassLoader
                String out = mPluginObj.mInfo.getDexParentDir().getPath();
                //changeDexMode(out);

                //
                Log.i("dex", "load " + mPath + " ...");
                if (LOG) {
                    // 因为Instant Run会替换parent为IncrementalClassLoader，所以在DEBUG环境里
                    // 需要替换为BootClassLoader才行
                    // Added by yangchao-xy & Jiongxuan Zhang
                    parent = ClassLoader.getSystemClassLoader();
                } else {
                    // 线上环境保持不变
                    parent = getClass().getClassLoader().getParent(); // TODO: 这里直接用父类加载器
                }
                String soDir = mPackageInfo.applicationInfo.nativeLibraryDir;

                long begin = 0;
                boolean isDexExist = false;

                if (LOG) {
                    begin = System.currentTimeMillis();
                    File dexFile = mPluginObj.mInfo.getDexFile();
                    if (dexFile.exists() && dexFile.length() > 0) {
                        isDexExist = true;
                    }
                }

                mClassLoader = new PluginDexClassLoader(mPluginObj.mInfo.getName(), mPath, out, soDir, parent);
                Log.i("dex", "load " + mPath + " = " + mClassLoader);

                if (mClassLoader == null) {
                    if (LOG) {
                        LogDebug.d(PLUGIN_TAG, "get dex null");
                    }
                    return false;
                }

                if (LOG) {
                    if (!isDexExist) {
                        Log.d(LOADER_TAG, " --释放DEX, " + "(plugin=" + mPluginName + ", version="
//                                + mPluginObj.mInfo.getVersion() + ")"
                                + ", use:" + (System.currentTimeMillis() - begin)
//                                + ", process:" + IPC.getCurrentProcessName()
                        +"");
                    } else {
                        Log.d(LOADER_TAG, " --无需释放DEX, " + "(plugin=" + mPluginName + ", version="
//                                + mPluginObj.mInfo.getVersion() + ")"
                                + ", use:" + (System.currentTimeMillis() - begin)
//                                + ", process:" + IPC.getCurrentProcessName()
                        +"");
                    }
                }

            }
            if (load == Plugin.LOAD_DEX) {
                return isDexLoaded();
            }

            // Context
            mPkgContext = new PluginContext(mContext, android.R.style.Theme, mClassLoader, mPkgResources, mPluginName, this);
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "pkg context=" + mPkgContext);
            }

        } catch (Throwable e) {
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, "p=" + mPath + " m=" + e.getMessage(), e);
            }
            return false;
        }

        return true;
    }

    /**
     * 调整插件中组件的进程名称，用宿主中的进程坑位来接收插件中的自定义进程
     *
     * 注：
     * 如果插件中没有配置静态的 “meta-data：process_map” 进行静态的进程映射，则自动为插件中组件分配进程
     *
     * @param appInfo
     */
    private void adjustPluginProcess(ApplicationInfo appInfo) {
        HashMap<String, String> processMap = getConfigProcessMap(appInfo);
        if (LOG) {
            Log.d(PLUGIN_TAG, "--- 调整插件中组件的进程 BEGIN ---");
            for (Map.Entry<String, String> entry : processMap.entrySet()) {
                Log.d(PLUGIN_TAG, entry.getKey() + " -> " + entry.getValue());
            }
        }

        doAdjust(processMap, mComponents.getActivityMap());
        doAdjust(processMap, mComponents.getServiceMap());
        doAdjust(processMap, mComponents.getReceiverMap());
        doAdjust(processMap, mComponents.getProviderMap());

        if (LOG) {
//            Log.d(PLUGIN_TAG, "--- 调整插件中组件的进程 END --- " + IPC.getCurrentProcessName());
        }
    }

    private HashMap<String, String> getConfigProcessMap(ApplicationInfo appInfo) {
        HashMap<String, String> processMap = new HashMap<>();
        Bundle bdl = appInfo.metaData;
        if (bdl == null || TextUtils.isEmpty(bdl.getString("process_map"))) {
            return processMap;
        }

        return processMap;
    }

    private void doAdjust(HashMap<String, String> processMap, HashMap<String, ? extends ComponentInfo> infos) {

        if (processMap == null || processMap.isEmpty()) {
            return;
        }

        for (HashMap.Entry<String, ? extends ComponentInfo> entry : infos.entrySet()) {
            ComponentInfo info = entry.getValue();
            if (info != null) {
                String targetProcess = processMap.get(info.processName);

                if (!TextUtils.isEmpty(targetProcess)) {
                    if (LOG) {
                        Log.d(PLUGIN_TAG, String.format("--- 调整组件 %s, %s -> %s", info.name, info.processName, targetProcess));
                    }

                    info.processName = targetProcess;
                }
            }
        }
    }

    /**
     * 调整插件中 Activity 的默认 TaskAffinity
     *
     * @param plugin 插件名称
     */
    private void adjustPluginTaskAffinity(String plugin, ApplicationInfo appInfo) {
        if (appInfo == null) {
            return;
        }

        Bundle bdl = appInfo.metaData;
        if (bdl != null) {
            boolean useDefault = bdl.getBoolean("use_default_task_affinity", true);
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "useDefault = " + useDefault);
            }

            if (!useDefault) {
                if (LOG) {
                    LogDebug.d(PLUGIN_TAG, String.format("替换插件 %s 中默认的 TaskAffinity", plugin));
                }

                String defaultPluginTaskAffinity = appInfo.packageName;
                for (HashMap.Entry<String, ActivityInfo> entry : mComponents.getActivityMap().entrySet()) {
                    ActivityInfo info = entry.getValue();
                    if (LOG) {
                        if (info != null) {
                            LogDebug.d(PLUGIN_TAG, String.format("%s.taskAffinity = %s ", info.name, info.taskAffinity));
                        }
                    }

                    // 如果是默认 TaskAffinity
                    if (info != null && info.taskAffinity.equals(defaultPluginTaskAffinity)) {
                        info.taskAffinity = info.taskAffinity + "." + plugin;
                        if (LOG) {
                            LogDebug.d(PLUGIN_TAG, String.format("修改 %s 的 TaskAffinity 为 %s", info.name, info.taskAffinity));
                        }
                    }
                }
            }
        }
    }

    public Context createBaseContext(Context newBase) {
        return new PluginContext(newBase, android.R.style.Theme, mClassLoader, mPkgResources, mPluginName, this);
    }
    Method mCreateMethod2;
    public boolean loadEntryMethod2() {
        //
        try {
            String className =   "com.example.pluginother.Entry";
            Class<?> c = mClassLoader.loadClass(className);
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "found entry: className=" + className + ", loader=" + c.getClassLoader());
            }
            mCreateMethod2 = c.getDeclaredMethod("create", PLUGIN_ENTRY_EXPORT_METHOD2_PARAMS);
            mCreateMethod2.invoke(null, mPkgContext, getClass().getClassLoader());
        } catch (Throwable e) {
            // 老版本的插件才会用到这个方法，因后面还有新版本的load方式，这里不打log
            if (LOG) {
                LogDebug.d(PLUGIN_TAG, e.getMessage(), e);
            }
        }
        return mCreateMethod2 != null;
    }

    public static final Class<?> PLUGIN_ENTRY_EXPORT_METHOD2_PARAMS[] = {
            Context.class, ClassLoader.class
    };
    public boolean invoke2() {
        try {
            mCreateMethod2.invoke(null, mPkgContext, getClass().getClassLoader());
        } catch (Throwable e) {
            if (LOG) {
                LogDebug.e(PLUGIN_TAG, e.getMessage(), e);
            }
            return false;
        }
        return true;
    }
}
