/*
 * Copyright (C) 2005-2017 Qihoo 360 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.plugindemo.pacakges;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;


import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.plugindemo.PluginHostApplication;
import com.example.plugindemo.helper.LogDebug;
import com.example.plugindemo.loader.PluginNativeLibsHelper;
import com.example.plugindemo.loader.PmBase;
import com.example.plugindemo.model.Plugin;
import com.example.plugindemo.model.PluginInfo;
import com.example.plugindemo.util.FilePermissionUtils;
import com.example.plugindemo.util.FileUtils;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;


/**
 * 插件管理器。用来控制插件的安装、卸载、获取等。运行在常驻进程中 <p>
 * 补充：涉及到插件交互、运行机制有关的管理器，在IPluginHost中 <p>
 * TODO 待p-n型插件逐渐变少后，将涉及到存储等逻辑，从PmHostSvc中重构后移到这里 <p>
 * <p>
 * 注意：插件框架内部使用，外界请不要调用。
 *
 * @author RePlugin Team
 */
public class PluginManagerServer {

    private static final String TAG = "PluginManagerServer";

    /**
     * 内置在宿主 assets 里的插件 APK 文件名
     */
    private static final String PLUGIN_ASSET_NAME = "plugin01.apk";

    /**
     * 把 assets 里内置的插件 APK 释放到 {@code cacheDir/plugin01.apk}，返回释放后的路径。
     * <p>
     * 每次安装前都要调用：{@link #installLocked} 会把 cache 里的 APK <b>移动</b>到
     * plugins_v3 目录，所以第二次点「install」时 cache 里已经没有文件了。
     *
     * @return 释放后的 APK 绝对路径；assets 里没有内置插件时返回 null
     */
    public static String ensurePluginApk(Context context) {
        File target = new File(context.getCacheDir(), PLUGIN_ASSET_NAME);
        InputStream in;
        try {
            in = context.getAssets().open(PLUGIN_ASSET_NAME);
        } catch (IOException e) {
            if (LogDebug.LOG) {
                LogDebug.e(TAG, "ensurePluginApk: assets 里没有内置插件 " + PLUGIN_ASSET_NAME, e);
            }
            return null;
        }
        try {
            // copyInputStreamToFile 内部会关闭输入流
            FileUtils.copyInputStreamToFile(in, target);
        } catch (IOException e) {
            if (LogDebug.LOG) {
                LogDebug.e(TAG, "ensurePluginApk: 释放插件失败", e);
            }
            return null;
        }
        if (LogDebug.LOG) {
            LogDebug.i(TAG, "ensurePluginApk: " + target.getAbsolutePath() + " (" + target.length() + " bytes)");
        }
        return target.getAbsolutePath();
    }

    public static PluginInfo installLocked(Context mContext,String path) {
        final boolean verifySignEnable = false;
        final int flags = verifySignEnable ? PackageManager.GET_META_DATA | PackageManager.GET_SIGNATURES : PackageManager.GET_META_DATA;

        // 1. 读取APK内容
        PackageInfo pi = mContext.getPackageManager().getPackageArchiveInfo(path, flags);
        if (pi == null) {
            if (LogDebug.LOG) {
                LogDebug.e(TAG, "installLocked: Not a valid apk. path=" + path);
            }

            return null;
        }

        // 3. 解析出名字和三元组
        PluginInfo instPli = PluginInfo.parseFromPackageInfo(PmBase.pluginName, path);
        if (LogDebug.LOG) {
            LogDebug.i(TAG, "installLocked: Info=" + instPli);
        }

        // 4. 将合法的APK改名后，移动（或复制，见RePluginConfig.isMoveFileWhenInstalling）到新位置
        // 注意：不能和p-n的最终释放位置相同，因为管理方式不一样
        if (!copyOrMoveApk(path, instPli)) {
            return null;
        }

        // 5. 从插件中释放 So 文件
        PluginNativeLibsHelper.install(instPli.getPath(), instPli.getNativeLibsDir());
        Intent intent=new Intent();
        intent.setAction("action_plugin_test");
        Bundle bundle=new Bundle();
        bundle.putSerializable("plugin",Plugin.build(instPli));
        bundle.putSerializable("pluginInfo",instPli);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        return instPli;
    }



    private static boolean copyOrMoveApk(String path, PluginInfo instPli) {
        File srcFile = new File(path);
        File newFile = instPli.getApkFile();

        // 插件已被释放过一次？通常“同版本覆盖安装”时，覆盖次数超过2次的会出现此问题
        // 此时，直接删除安装路径下的文件即可，这样就可以直接Move/Copy了
        if (newFile.exists()) {
            FileUtils.deleteQuietly(newFile);
        }

        // 将源APK文件移动/复制到安装路径下
        try {

                FileUtils.moveFile(srcFile, newFile);

        } catch (IOException e) {
            if (LogDebug.LOG) {
                LogDebug.e(TAG, "copyOrMoveApk: Copy/Move Failed! src=" + srcFile + "; dest=" + newFile, e);
            }
            return false;
        }

        instPli.setPath(newFile.getAbsolutePath());

        // Android 14 (API 34) 起，动态加载的 dex/jar 必须是「只读」的，否则 DexClassLoader
        // 会直接抛异常。插件 APK 落在 getDir("plugins_v3") 里默认是可写的，这里显式置为 r--r--r--。
        // 注意：odex 的释放目录是旁边的 oat/ 子目录，不在这个文件上，不受影响。
        FilePermissionUtils.setPermissions(newFile.getPath(),
                FilePermissionUtils.S_IRUSR | FilePermissionUtils.S_IRGRP | FilePermissionUtils.S_IROTH, -1, -1);

        return true;
    }


}
