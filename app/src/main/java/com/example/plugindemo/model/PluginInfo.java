package com.example.plugindemo.model;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.example.plugindemo.RePluginInternal;
import com.example.plugindemo.loader.VMRuntimeCompat;

import java.io.File;
import java.io.Serializable;

/**
 *  创建人：linchaoyue
 *  创建时间：2019-12-18 15:09
 * 描述：
 */
public class PluginInfo implements Serializable {
    private String path;
    private String name;

    PluginInfo(String name,String path){
        this.name=name;
        this.path=path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 通过插件APK的MetaData来初始化PluginInfo <p>
     * 注意：框架内部接口，外界请不要直接使用
     */
    public static PluginInfo parseFromPackageInfo(String name, String path) {
        PluginInfo pli = new PluginInfo(name,path);

        return pli;
    }

    /**
     * 获取APK存放的文件信息 <p>
     * 若为"纯APK"插件，则会位于app_p_a中；若为"p-n"插件，则会位于"app_plugins_v3"中 <p>
     * 注意：若支持同版本覆盖安装的话，则会位于app_p_c中； <p>
     *
     * @return Apk所在的File对象
     */
    public File getApkFile() {
        return new File(getApkDir(), makeInstalledFileName() + ".jar");
    }

    /**
     * 获取APK存放目录
     *
     * @return
     */
    public String getApkDir() {
        // 必须使用宿主的Context对象，防止出现“目录定位到插件内”的问题
        Context context = RePluginInternal.getAppContext();
        File dir;

            dir = context.getDir(Constant.LOCAL_PLUGIN_SUB_DIR, 0);


        return dir.getAbsolutePath();
    }
    /**
     * 生成用于放入app_plugin_v3（app_p_n）等目录下的插件的文件名，其中：<p>
     * 1、“纯APK”方案：得到混淆后的文件名（规则见代码内容） <p>
     * 2、“旧p-n”和“内置插件”（暂定）方案：得到类似 shakeoff_10_10_103 这样的比较规范的文件名 <p>
     * 3、只获取文件名，其目录和扩展名仍需在外面定义
     *
     * @return 文件名（不含扩展名）
     */
    public String makeInstalledFileName() {
        return getName()+"_installed";
    }

    /**
     * 根据类型来获取SO释放的路径 <p>
     * 若为"纯APK"插件，则会位于app_p_n中；若为"p-n"插件，则会位于"app_plugins_v3_libs"中 <p>
     * 若支持同版本覆盖安装的话，则会位于app_p_c中； <p>
     * 注意：仅供框架内部使用
     *
     * @return SO释放路径所在的File对象
     */
    public File getNativeLibsDir() {
        // 必须使用宿主的Context对象，防止出现“目录定位到插件内”的问题
        Context context = RePluginInternal.getAppContext();
        File dir;

            dir = context.getDir(Constant.LOCAL_PLUGIN_DATA_LIB_DIR, 0);

        return new File(dir, makeInstalledFileName());
    }

    /**
     * 获取插件包名
     */
    public String getPackageName() {
        return "com.example.pluginother";
    }


    /**
     * 获取Extra Dex（优化前）生成时所在的目录 <p>
     * 若为"纯APK"插件，则会位于app_p_od/xx_ed中；若为"p-n"插件，则会位于"app_plugins_v3_odex/xx_ed"中 <p>
     * 若支持同版本覆盖安装的话，则会位于app_p_c/xx_ed中； <p>
     * 注意：仅供框架内部使用;仅适用于Android 4.4.x及以下
     *
     * @return 优化前Extra Dex所在目录的File对象
     */
    public File getExtraDexDir() {
        return getDexDir(getDexParentDir(), Constant.LOCAL_PLUGIN_INDEPENDENT_EXTRA_DEX_SUB_DIR);
    }

    /**
     * 获取Extra Dex（优化后）生成时所在的目录 <p>
     * 若为"纯APK"插件，则会位于app_p_od/xx_eod中；若为"p-n"插件，则会位于"app_plugins_v3_odex/xx_eod"中 <p>
     * 若支持同版本覆盖安装的话，则会位于app_p_c/xx_eod中； <p>
     * 注意：仅供框架内部使用;仅适用于Android 4.4.x及以下
     *
     * @return 优化后Extra Dex所在目录的File对象
     */
    public File getExtraOdexDir() {
        return getDexDir(getDexParentDir(), Constant.LOCAL_PLUGIN_INDEPENDENT_EXTRA_ODEX_SUB_DIR);
    }

    /**
     * 获取或创建（如果需要）某个插件的Dex目录，用于放置dex文件
     * 注意：仅供框架内部使用;仅适用于Android 4.4.x及以下
     *
     * @param dirSuffix 目录后缀
     * @return 插件的Dex所在目录的File对象
     */
    @NonNull
    private File getDexDir(File dexDir, String dirSuffix) {

        File dir = new File(dexDir, makeInstalledFileName() + dirSuffix);

        if (!dir.exists()) {
            dir.mkdir();
        }
        return dir;
    }

    /**
     * 获取Dex（优化后）生成时所在的目录 <p>
     *
     * Android O之前：
     * 若为"纯APK"插件，则会位于app_p_od中；若为"p-n"插件，则会位于"app_plugins_v3_odex"中 <p>
     * 若支持同版本覆盖安装的话，则会位于app_p_c中； <p>
     *
     * Android O：
     * APK存放目录/oat/{cpuType}
     *
     * 注意：仅供框架内部使用
     * @return 优化后Dex所在目录的File对象
     */
    public File getDexParentDir() {

        // 必须使用宿主的Context对象，防止出现“目录定位到插件内”的问题
        Context context = RePluginInternal.getAppContext();

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            return new File(getApkDir() + File.separator + "oat" + File.separator + VMRuntimeCompat.getArtOatCpuType());
        } else {

                return context.getDir(Constant.LOCAL_PLUGIN_ODEX_SUB_DIR, 0);

        }
    }

    /**
     * 获取Dex（优化后）所在的文件信息 <p>
     *
     * Android O 之前：
     * 若为"纯APK"插件，则会位于app_p_od中；若为"p-n"插件，则会位于"app_plugins_v3_odex"中 <p>
     *
     * Android O：
     * APK存放目录/oat/{cpuType}/XXX.odex
     *
     * 注意：仅供框架内部使用
     *
     * @return 优化后Dex所在文件的File对象
     */
    public File getDexFile() {

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            File dir = getDexParentDir();
            return new File(dir, makeInstalledFileName() + ".odex");
        } else {
            File dir = getDexParentDir();
            return new File(dir, makeInstalledFileName() + ".dex");
        }
    }

}
