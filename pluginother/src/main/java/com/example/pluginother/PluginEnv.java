package com.example.pluginother;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 插件侧的「宿主环境」持有者，是插件与宿主之间唯一的桥。
 * <p>
 * 插件在<b>编译期零依赖宿主</b>，所以所有对宿主的调用都是反射。
 * 反射用的 ClassLoader 是宿主在 {@link Entry#create} 里递进来的那个，
 * 因此这里不依赖「插件 ClassLoader 的 parent 能不能看见宿主类」——
 * 即便在 release 下插件 ClassLoader 的 parent 是 BootClassLoader，也照样能找到宿主类。
 */
public final class PluginEnv {

    private static final String TAG = "PluginEnv";

    /**
     * 宿主里负责「插件 Activity 与系统生命周期对接」的类
     */
    private static final String HOST_BRIDGE_CLASS = "com.example.plugindemo.PluginProcessPer";

    /**
     * 宿主塞进来的 PluginContext，携带插件的 Resources 与 PluginDexClassLoader
     */
    private static Context sPluginContext;

    private static ClassLoader sHostClassLoader;

    /**
     * PluginProcessPer.createActivityContext(Activity, Context)
     */
    private static Method sCreateActivityContext;

    /**
     * PluginProcessPer.handleActivityCreateBefore(Activity, Bundle)
     */
    private static Method sHandleCreateBefore;

    /**
     * PluginProcessPer.handleActivityCreate(Activity, Bundle)
     */
    private static Method sHandleCreate;

    private static boolean sResolved;

    private PluginEnv() {
    }

    static synchronized void init(Context pluginContext, ClassLoader hostClassLoader) {
        sPluginContext = pluginContext;
        sHostClassLoader = hostClassLoader;
        resolveHostMethods();
    }

    /**
     * 宿主构造的插件 Context。注意它绑定的是 Application 级 base context，
     * 不要直接拿来当 Activity 的 base context 用。
     */
    public static Context getPluginContext() {
        return sPluginContext;
    }

    private static void resolveHostMethods() {
        if (sResolved) {
            return;
        }
        sResolved = true;
        try {
            Class<?> bridge = Class.forName(HOST_BRIDGE_CLASS, true, sHostClassLoader);
            sCreateActivityContext = bridge.getMethod("createActivityContext", Activity.class, Context.class);
            sHandleCreateBefore = bridge.getMethod("handleActivityCreateBefore", Activity.class, Bundle.class);
            sHandleCreate = bridge.getMethod("handleActivityCreate", Activity.class, Bundle.class);
        } catch (Throwable e) {
            // 宿主版本对不上：降级为「不注入」。Activity 仍能起来，只是会用到宿主的资源。
            Log.w(TAG, "cannot resolve host bridge " + HOST_BRIDGE_CLASS, e);
        }
    }

    /**
     * 把 Activity 的 baseContext 换成宿主为插件构造的 PluginContext。
     * <p>
     * 这一步是插件化能不能用自己资源的关键：坑位机制让系统用「宿主的 ActivityInfo」
     * 实例化插件 Activity，拿到的 baseContext 是宿主的，Resources / ClassLoader 全是宿主的，
     * 这样 {@code setContentView(R.layout.xxx)} 会拿宿主的资源表去解析插件的资源 id，必然出错。
     *
     * @return 换失败时原样返回 newBase，保证 Activity 至少能起来
     */
    public static Context wrapActivityContext(Activity activity, Context newBase) {
        if (sCreateActivityContext == null) {
            return newBase;
        }
        try {
            Context wrapped = (Context) sCreateActivityContext.invoke(null, activity, newBase);
            return wrapped != null ? wrapped : newBase;
        } catch (Throwable e) {
            Log.w(TAG, "createActivityContext failed", e);
            return newBase;
        }
    }

    /**
     * 在 super.onCreate() 之前调用，让宿主修好 savedInstanceState 和 intent extras 的 ClassLoader
     */
    public static void onActivityCreateBefore(Activity activity, Bundle savedInstanceState) {
        invokeQuietly(sHandleCreateBefore, activity, savedInstanceState);
    }

    /**
     * 在 super.onCreate() 之后调用，让宿主补齐 TaskDescription 等「像原生 Activity」的属性
     */
    public static void onActivityCreate(Activity activity, Bundle savedInstanceState) {
        invokeQuietly(sHandleCreate, activity, savedInstanceState);
    }

    private static void invokeQuietly(Method m, Activity activity, Bundle savedInstanceState) {
        if (m == null) {
            return;
        }
        try {
            m.invoke(null, activity, savedInstanceState);
        } catch (Throwable e) {
            Log.w(TAG, "invoke " + m.getName() + " failed", e);
        }
    }
}
