package com.example.plugindemo;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.view.View;
import android.widget.Toast;

import com.example.plugindemo.loader.PmBase;
import com.example.plugindemo.model.PluginInfo;
import com.example.plugindemo.pacakges.PluginManagerServer;

public class MainActivity extends AppCompatActivity {
    private static String TAG="MainActivity";

    public static String ACTIVITY_FROM="com.example.plugindemo.Activity01";
    public static String ACTIVITY_TO="com.example.pluginother.OtherActivity";


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            String[] p=new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE};
            requestPermissions(p, 11);

        }
        findViewById(R.id.install).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 插件 APK 是内置在宿主 assets 里的，安装前先释放到 cacheDir
                String path = PluginManagerServer.ensurePluginApk(MainActivity.this);
                if (path == null) {
                    Toast.makeText(MainActivity.this,
                            "assets 里没有内置插件 plugin01.apk，先执行 ./gradlew :app:assembleDebug",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                PluginInfo info = PluginManagerServer.installLocked(MainActivity.this, path);
                Toast.makeText(MainActivity.this,
                        info == null ? "安装失败，看 logcat" : "安装成功: " + info.getName(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ok = PluginProcessPer.startActivity(MainActivity.this, new Intent(), PmBase.pluginName, ACTIVITY_TO);
                if (!ok) {
                    // 插件没安装/没加载成功：退回到宿主自己的坑位页面（纯色兜底页）。
                    // 此时 PmBase.loadClass 换不出插件类，会正常加载宿主自己的 Activity01。
                    Toast.makeText(MainActivity.this,
                            "插件未加载，显示宿主兜底页（先点 install）", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MainActivity.this, Activity01.class));
                }
            }
        });

    }
}
