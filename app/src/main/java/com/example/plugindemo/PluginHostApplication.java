package com.example.plugindemo;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.plugindemo.loader.PmBase;
import com.example.plugindemo.model.Plugin;
import com.example.plugindemo.model.PluginInfo;

/**
 *  创建人：linchaoyue
 *  创建时间：2019-12-19 15:23
 * 描述：
 */
public class PluginHostApplication extends Application {
    public static PmBase sPmBase;
    public static Context mContext;
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sPmBase=new PmBase(base);
        PatchClassLoaderUtils.patch(this,sPmBase);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext=this.getApplicationContext();
        RePluginInternal.init(this);
        LocalBroadcastReceiver localReceiver = new LocalBroadcastReceiver();

        LocalBroadcastManager localBroadcastManager= LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter =new IntentFilter();
        intentFilter.addAction("action_plugin_test");
        localBroadcastManager.registerReceiver(localReceiver, intentFilter);

    }

    private void addData(Plugin plugin, PluginInfo pluginInfo){
        sPmBase.putPluginObject(pluginInfo,plugin);
    }

    public class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override

        public void onReceive(Context context, Intent intent) {
            Bundle bundle=intent.getExtras();
            Plugin plugin= (Plugin) bundle.getSerializable("plugin");
            PluginInfo pluginInfo= (PluginInfo) bundle.getSerializable("pluginInfo");
            addData(plugin,pluginInfo);
        }

    }



}
