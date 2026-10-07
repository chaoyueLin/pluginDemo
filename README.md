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

## 几种方案对比

插件化要绕过的无非是「类加载 / 四大组件 / 资源 / so」四道管控，按绕法可以分成几派：

| 方案 | 核心手段 | Hook 面 | 组件 / 资源 / so | 现状（2026-10） |
|---|---|---|---|---|
| **RePlugin**（360） | 只换 ClassLoader（One Hook）+ 宿主 manifest 预埋坑位 Activity | 1 个 | 四大组件含静态 Receiver；独立资源；官方称 v3.0 起支持 64 位 so | 未归档但基本停更。v3.1.0（2024-09）只做到「适配 target34」，官方适配声明止于 Android 9，issue 区 Android 14 初始化报错至今 open |
| **VirtualAPK**（滴滴） | Hook Instrumentation + AMS/PMS 系 Binder + 占坑 | 大 | 四大组件全支持且无需手工注册；插件内不支持自定义布局的 Notification | **已归档**，最后提交 2018-12。插件必须在编译期与宿主对齐（`targetHost`、`packageId`） |
| **Atlas**（阿里） | 编译期重改造：自研 aapt、分包、资源分区，运行时按需装 Bundle（OSGi 风格） | 编译期改造 | 以 Bundle 为单位做类与资源隔离 | 2020-09 后停更，官方支持矩阵止于 Android 9。工程改造代价最高 |
| **Shadow**（腾讯） | 零反射零 Hook：编译期把插件 Activity 字节码改写成继承 `ShadowActivity`，宿主真实注册壳子组件代理生命周期 | 无 | 四大组件、资源 ID 分区、so、DataBinding 均支持 | 唯一仍在活跃维护的主流开源方案（master 2026-03 仍在提交）。但最新 release 还是 2022 年的 2.3.0，维护者明确表示接入方要自行打补丁 |
| **DroidPlugin / VirtualApp** | 容器：前者 Hook 近 20 个系统服务 + Stub 组件，后者虚拟化整套 Framework | 极大 | 可免安装运行整个 APK，插件是完整应用 | DroidPlugin 2019-12 停更，只适配到 Android 8.0；VirtualApp 开源版 2017-12 冻结，商用需授权 |
| **App Bundle**（官方） | 上传 AAB，商店按 ABI / 密度 / 语言 / 模块下发 split APK | 无 | 系统原生；`dynamic-feature` 模块可带自己的组件 | 唯一官方路线，但只在 Google Play 生效——它本身就是被推荐用来替代动态代码加载的；国内商店主要仍按 APK 分发 |

选型上没有普适答案：能接受 fork 维护 + 要动态下发 → Shadow；只在 Google Play 上架 → AAB / Play Feature Delivery；国内且要免安装、双开 → 容器类；只想修线上 Bug 走热修复而不是插件化（Tinker 这类补丁包**不能改 manifest、不能新增组件**）；想搞懂原理 → RePlugin 的 One Hook 链路最短，本项目仿的就是它。

### 平台侧的变化

这几年的系统收紧直接决定了上面的格局：

| 版本 | 变化 | 影响 |
|---|---|---|
| Android 9（API 28） | 非 SDK 接口限制生效 | 分水岭，全量 Hook 派开始退潮 |
| Android 11（API 30） | 公开 `ResourcesLoader` / `ResourcesProvider` | 动态加载资源终于有官方途径，但不向下兼容 API 30 以下 |
| Android 14（API 34） | 动态加载的 dex / jar / apk **必须只读** | 所有「下载下来再加载」的方案强制改造。本 demo 里 `PluginManagerServer` 装完把插件 APK 置 0444，就是在满足这条 |
| Android 15+ | 16KB 页对齐 | 插件里预编译的 so 要重新对齐，否则新设备上加载失败 |
| Android 17（API 37） | 只读要求扩展到 `System.load()` 的 native 库 | 插件 so「解压到私有目录再 load」的套路同样要补 `setReadOnly()` |

另外两条容易忽略的约束：Google Play 的 Device and Network Abuse 政策禁止从商店之外下载可执行代码，所以「从自己服务器下发 dex / so」的插件化和热修复在 Play 上都有下架风险，技术合规不等于商店合规；国内工信部信管函〔2023〕26 号也对热更新改动 App 主要功能做了限制，商店侧已有对应的审核要求。

上面各框架的支持范围与维护状态取自各仓库 README / release / 提交记录，框架本身迭代很快，落地前建议重新核对官方声明。

## 资源（Resource）怎么隔离

class 隔离靠 ClassLoader，资源隔离的抓手则是 **Resources 对象**：一个 `Resources` 背后挂着一个 `AssetManager`，能往里塞几个 APK；而资源 id 是 aapt 在编译期写死的 32 位整数（`0xPPTTEEEE`，高 8 位是 packageId），系统资源固定 `0x01`，应用默认 `0x7f`。于是只有两条路：要么给插件单独造一个 `Resources`，要么并进宿主的 `AssetManager` 并把前缀岔开。

| 方案 | 做法 | 隔离效果 | 代表 |
|---|---|---|---|
| **独立 Resources** | `pm.getResourcesForApplication(插件的 ApplicationInfo)` 直接拿，或反射 `AssetManager.addAssetPath(插件APK)` 自己拼一个只装插件资源的 `Resources` | 插件与宿主资源互不可见，要互访得手动把对方的 `Resources` 递过去 | RePlugin、DroidPlugin、**本 demo** |
| **合并 + 资源分区** | 插件 APK 也 `addAssetPath` 进宿主同一个 `AssetManager`，但用 `--package-id` 把插件前缀改成 `0x6f`、`0x80` 之类 | 不隔离，靠 id 前缀区分，插件可直接引用宿主资源 | VirtualAPK（`0x6f`）、Shadow（`0x80` 起） |
| **官方 ResourcesLoader**（API 30+） | `ResourcesLoader` / `ResourcesProvider` 挂载资源包，不需要反射 | 可挂可卸 | Android 11 起的官方 API |
| **编译期合并**（不算插件化） | 插件作为模块跟宿主一起编译打包，运行时没有资源加载 | 无 | Small、AAB 的 install-time feature |

分区的坑在于 id 空间很窄：`0x80` 及以上在 Android 8.0 之前会被当作负数（无效 id），低于 `0x7f` 的 `0x02–0x7e` 又是保留区，aapt2 要 `--allow-reserved-package-id` 才肯放行；而且 AGP 并不直接暴露 `--package-id`，得定制 aapt 或改打包流程 —— 这正是 VirtualAPK、Atlas 要自带一套打包工具链的原因。

拿到插件的 `Resources` 之后，还要让插件代码真的用上它：

- **换 Context（本 demo 的做法）**：写一个 `ContextWrapper` / `ContextThemeWrapper` 子类，override `getResources()` 和 `getAssets()`（`loader/PluginContext.java`），插件 Activity 在 `attachBaseContext` 里套上它。`Resources` 从 `Loader` 里通过 `getResourcesForApplication` 拿到（`loader/Loader.java`）。注意 `LayoutInflater` 得跟着 `cloneInContext` 并设 `Factory`，否则布局里的自定义 View 会拿宿主的 ClassLoader 去找类。
- **反射替换**：直接改 Activity / ResourcesManager 里的 `mResources` 字段。更彻底，但要吃 Android 9 起的非 SDK 接口限制。

几个反复踩的坑：`AssetManager.addAssetPath` 至今是 hidden API，能走 `getResourcesForApplication` 或 `ResourcesLoader` 就别反射；Theme 是重灾区 —— 插件 Activity 套宿主主题时，主题里若引用了插件资源 id 就会解析失败；WebView、Notification 自定义布局这类"从系统侧取资源"的入口拿不到插件的独立 `Resources`，所以 VirtualAPK 直接写明不支持自定义布局的 Notification；还有 AGP 8 默认 `android.nonFinalResIds=true`，R 字段不再是编译期常量，靠内联 id 做优化的方案要显式关掉。

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