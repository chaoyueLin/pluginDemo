# 插件化

## 什么是插件化
插件化技术最初源于免安装运行apk的想法，这个免安装的apk可以理解为插件。支持插件化的app可以在运行时加载和运行插件，这样便可以将app中一些不常用的功能模块做成插件，一方面减小了安装包的大小，另一方面可以实现app功能的动态扩展。

宿主： 就是当前运行的APP插件： 相对于插件化技术来说，就是要加载运行的apk类文件

## Android插件化
* Class
* 四大组件
* Resource
* So
## [Replugin](/replugin.md)

## 工程结构

```
app/          宿主。核心是 RePluginClassLoader + PmBase（One Hook）、Loader、PluginContext、坑位 Activity01
pluginother/  插件。编译成独立 APK，零宿主编译依赖，运行时靠反射调回宿主
```

宿主与插件之间只靠两件事耦合：

1. **入口约定**：宿主按名字反射 `com.example.pluginother.Entry#create(Context, ClassLoader)`；
2. **坑位约定**：插件 Activity 借宿主 Manifest 里的 `Activity01` 启动，`PmBase` 在系统加载坑位类时把它换成插件的类。

插件在编译期看不到宿主的任何类，所有对宿主的调用都通过 `Entry.create` 第二个参数（宿主的 ClassLoader）反射完成，
见 `pluginother/.../PluginEnv.java`。

## 跑起来

```bash
./gradlew :app:assembleDebug      # 会自动先构建 :pluginother 并把插件 APK 拷进 app/src/main/assets/plugin01.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

启动后依次点：

1. **install** —— 把 assets 里的内置插件释放到 `cacheDir`，再"安装"到 `files/plugins_v3/plugin_test_installed.jar`；
2. **start** —— 启动插件页面，页面上会打印插件自己的资源包名和 ClassLoader，用来确认跑的确实是插件 dex + 插件资源；
3. 插件页里的「打开第二个插件页面」验证插件内部跳转（复用同一个坑位）。

没点 install 直接点 start 时，会退回显示宿主自己的坑位页面（纯色兜底页），这是预期行为。

> 构建链：AGP 8.13.2 / Gradle 9.3.1 / JDK 17+ / compileSdk 36 / minSdk 21。
> targetSdk 是 34，因此受 Android 14「动态加载的文件必须只读」限制，`PluginManagerServer` 安装完会把插件 APK 置为 0444。

## 点一次 start 都发生了什么

这是整个框架的核心链路，顺着看一遍就懂了 One Hook：

```
MainActivity「start」
 └─ PluginProcessPer.startActivity(ctx, intent, "plugin_test", "com.example.pluginother.OtherActivity")
     ├─ PmBase.loadAppPlugin()                     加载插件：PackageInfo → Resources → PluginDexClassLoader → PluginContext
     │   └─ Loader.loadEntryMethod2()              反射调用 Entry.create(pluginContext, 宿主 ClassLoader)
     │                                             插件在这里拿到「钥匙」，之后靠它反射回宿主
     ├─ ComponentList.getActivity(...)             从插件 APK 的 Manifest 里确认这个 Activity 存在
     ├─ PmBase.setActivityMapping(坑位, 插件Activity)  ★ 关键：登记「坑位 → 插件类」映射
     └─ intent.setComponent(ComponentName(宿主包名, "com.example.plugindemo.Activity01"))
        context.startActivity(intent)

        ↓ 系统拿着「坑位」去启动，AMS 校验的是宿主 Manifest 里的 Activity01

ActivityThread.performLaunchActivity
 ├─ LoadedApk.getClassLoader()                    = 被替换过的 RePluginClassLoader
 ├─ Instrumentation.newActivity(cl, "com.example.plugindemo.Activity01", intent)
 │   └─ RePluginClassLoader.loadClass()
 │       └─ PmBase.loadClass("...Activity01")     ★ Hook 点：查映射表，返回插件 OtherActivity 的 Class
 └─ 实例化出来的其实是 OtherActivity；Activity01 这个类从头到尾没被加载过

Activity.attach() → PluginBaseActivity.attachBaseContext()
 └─ 反射调用 PluginProcessPer.createActivityContext(this, newBase)
     └─ Loader.createBaseContext() → new PluginContext(...)   换成插件的 Resources / ClassLoader
        → onCreate() 里的 setContentView(R.layout.xxx) 才会用插件的资源表解析

Activity01 只在「插件没加载成功」时才真的被加载 —— 这就是兜底页。
```

## 实现范围

| 能力 | 状态 |
|---|---|
| Class（ClassLoader Hook） | ✅ `RePluginClassLoader` + `PluginDexClassLoader` |
| Resource | ✅ `PluginContext` + `Loader.loadDex` 里的 `getResourcesForApplication` |
| 四大组件 · Activity | ✅ 坑位 `Activity01` + 映射表，支持插件内部互相跳转 |
| 四大组件 · Service / Provider / Receiver | ❌ `PluginContext` 里是有意的空实现，见该类注释 |
| So | ⚠️ `PluginNativeLibsHelper` 已实现释放逻辑，但 demo 插件里没有 so，未验证 |

已知限制：

- 只有一个坑位，插件 Activity 的 `launchMode` / `windowSoftInputMode` / `getIntent().getComponent()` 拿到的都是坑位的配置；
- 进程被杀后从「最近任务」恢复，插件表是空的，会退化成宿主的兜底页；
- `PluginInfo.getPackageName()` 写死 `com.example.pluginother`，换插件包名要同步改宿主。