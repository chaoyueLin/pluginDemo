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

import com.example.plugindemo.loader.PmBase;
import com.example.plugindemo.model.Plugin;
import com.example.plugindemo.pacakges.PluginManagerServer;

import java.io.File;

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
        final String path=this.getCacheDir().getPath()+ "/plugin01.apk";
        findViewById(R.id.install).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PluginManagerServer.installLocked(MainActivity.this,path);
            }
        });

        findViewById(R.id.start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PluginProcessPer.startActivity(MainActivity.this, new Intent(), PmBase.pluginName,ACTIVITY_TO);
            }
        });

    }
}
