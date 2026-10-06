package com.example.pluginother;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * 插件 Activity 基类 —— 宿主无侵入接入插件的那个「接缝」。
 * <p>
 * 坑位机制让系统在启动 {@code com.example.plugindemo.Activity01} 时，实际加载到的
 * 是本类的子类（{@code RePluginClassLoader → PmBase.loadClass} 把类换掉了）。
 * 系统仍旧用「坑位 Activity 的 ActivityInfo」去 attach，所以插件 Activity 拿到手的
 * baseContext / Resources / ClassLoader 都是宿主的，必须在这里换掉。
 * <p>
 * 换掉之后：{@code getResources()} 是插件的，{@code getClassLoader()} 是插件的
 * {@code PluginDexClassLoader}，{@code setContentView(R.layout.xxx)} 才能正确解析。
 */
public class PluginBaseActivity extends Activity {

    /**
     * 宿主 {@code PluginProcessPer.loadPluginActivity()} 把插件 Manifest 里声明的
     * {@code android:theme} 通过这个 key 放进 Intent
     */
    private static final String INTENT_KEY_THEME_ID = "__themeId";

    @Override
    protected void attachBaseContext(Context newBase) {
        // attachBaseContext 是在 Activity.attach() 内部、onCreate() 之前被系统调用的，
        // 换 base context 必须在这里做，晚一步资源就已经被解析掉了。
        super.attachBaseContext(PluginEnv.wrapActivityContext(this, newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. 先把主题定下来。必须在任何 setContentView 之前调用 ——
        //     decor 一旦安装（第一次 setContentView 时），再改主题就不生效了。
        applyPluginTheme();

        // 2. onCreate 之前：把 savedInstanceState / intent 里 extras 的 ClassLoader
        //    换成插件的，否则反序列化 Parcelable 时会 ClassNotFoundException
        PluginEnv.onActivityCreateBefore(this, savedInstanceState);

        super.onCreate(savedInstanceState);

        // 3. onCreate 之后：通知宿主，让它补上「最近任务」的标题和图标
        PluginEnv.onActivityCreate(this, savedInstanceState);
    }

    /**
     * 应用插件 Manifest 里声明的主题。
     * <p>
     * 宿主把 {@code android:theme} 的资源 id 通过 Intent extra 带了进来。此时 Activity 的
     * Resources 已经在 attachBaseContext 里换成插件的了，所以插件包里的主题 id 可以直接用
     * （取不到就沿用坑位 Activity 的主题）。
     * <p>
     * 注意：只能放在 onCreate 里，不能放在 attachBaseContext —— Activity.attach() 里
     * {@code attachBaseContext()} 是在 {@code mIntent = intent} <b>之前</b>执行的，
     * 那时候 getIntent() 还是 null。
     */
    private void applyPluginTheme() {
        if (getIntent() == null) {
            return;
        }
        int theme = getIntent().getIntExtra(INTENT_KEY_THEME_ID, 0);
        if (theme == 0) {
            return;
        }
        try {
            setTheme(theme);
        } catch (Throwable ignored) {
            // 主题 id 解析不了不影响功能，退回默认主题即可
        }
    }
}
