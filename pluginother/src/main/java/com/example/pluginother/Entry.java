package com.example.pluginother;

import android.content.Context;

/**
 * 插件入口类。
 * <p>
 * 宿主 {@code Loader.loadEntryMethod2()} 是按下面这样硬编码反射调用它的：
 * <pre>
 *   Class&gt;?&lt; c = mClassLoader.loadClass("com.example.pluginother.Entry");
 *   mCreateMethod2 = c.getDeclaredMethod("create", Context.class, ClassLoader.class);
 *   mCreateMethod2.invoke(null, mPkgContext, getClass().getClassLoader());
 * </pre>
 * 所以：<b>类名、方法名、参数类型、static 修饰都不能改</b>。改了这个方法就找不到了，
 * {@code Loader.loadEntryMethod2()} 返回 false，整个插件加载直接失败。
 * <p>
 * 参数里的 {@code ClassLoader} 是宿主递给插件的「钥匙」：插件在编译期完全不依赖宿主，
 * 后面所有对宿主 API 的调用都要靠它去 {@code Class.forName} 找到宿主类。
 */
public class Entry {

    /**
     * 插件被加载时由宿主调用。
     *
     * @param pluginContext   宿主构造的 PluginContext，携带插件的 Resources 和 ClassLoader
     * @param hostClassLoader 宿主的 ClassLoader，用于反射访问宿主的类
     */
    public static void create(Context pluginContext, ClassLoader hostClassLoader) {
        PluginEnv.init(pluginContext, hostClassLoader);
    }
}
