package com.example.plugindemo.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import com.example.plugindemo.app.PluginApplicationClient;
import com.example.plugindemo.helper.LogDebug;
import com.example.plugindemo.loader.Loader;

import java.io.Serializable;

import static com.example.plugindemo.helper.LogDebug.LOG;
import static com.example.plugindemo.helper.LogDebug.PLUGIN_TAG;


/**
 *  创建人：linchaoyue
 *  创建时间：2019-12-18 14:35
 * 描述：
 */
public class Plugin implements Serializable {
    // 只加载Service/Activity/ProviderInfo信息（包含ComponentList）
    public static final int LOAD_INFO = 0;

    // 加载插件信息和资源
    public static final int LOAD_RESOURCES = 1;

    // 加载插件信息、资源和Dex
    public static final int LOAD_DEX = 2;

    // 加载插件信息、资源、Dex，并运行Entry类
    public static final int LOAD_APP = 3;
    /**
     *
     */
    public PluginInfo mInfo;

    public Plugin(PluginInfo info) {
        mInfo = info;
    }
    /**
     *
     */
    Context mContext;

    /**
     *
     */
    ClassLoader mParent;

    public Loader mLoader;
    public static  Plugin build(PluginInfo info) {
        return new Plugin(info);
    }

    public static  Plugin cloneAndReattach(Context c, Plugin p, ClassLoader parent) {
        if (p == null) {
            return null;
        }
        p = build(p.mInfo);
        p.attach(c, parent);
        return p;
    }
    public void attach(Context context, ClassLoader parent) {
        mContext = context;
        mParent = parent;
    }

    /**
     *
     */
    public boolean load(int load, boolean useCache) {
        PluginInfo info = mInfo;
        Context context = mContext;
        ClassLoader parent = mParent;
        boolean rc = doLoad(context, parent, load);
        return rc;
    }

    private final boolean doLoad( Context context, ClassLoader parent, int load) {
        if (mLoader == null) {
            // 试图释放文件


            //
            mLoader = new Loader(context, mInfo.getName(), mInfo.getPath(), this);
            if (!mLoader.loadDex(parent, load)) {
                return false;
            }


            // 若需要加载Dex，则还同时需要初始化插件里的Entry对象
            if (load == LOAD_APP) {
                // NOTE Entry对象是可以在任何线程中被调用到
                if (!loadEntryLocked()) {
                    return false;
                }
            }
        }

        if (load == LOAD_INFO) {
            return mLoader.isPackageInfoLoaded();
        } else if (load == LOAD_RESOURCES) {
            return mLoader.isResourcesLoaded();
        } else if (load == LOAD_DEX) {
            return mLoader.isDexLoaded();
        } else {
            return true;
        }
    }

    /**
     * @return
     */
    public ClassLoader getClassLoader() {
        if (mLoader == null) {
            return null;
        }
        return mLoader.mClassLoader;
    }

    private boolean loadEntryLocked() {

            if (LOG) {
                LogDebug.d(PLUGIN_TAG, "Plugin.loadEntryLocked(): Load entry, info=" + mInfo);
            }
            if (!mLoader.loadEntryMethod2()) {
                return false;
            }
        return true;
    }
}
