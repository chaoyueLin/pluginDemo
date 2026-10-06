package com.example.pluginother;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * 插件页面。类名写死在宿主 {@code MainActivity.ACTIVITY_TO} 里，改这里要同步改宿主。
 * <p>
 * 页面上把插件自己的资源包名和 ClassLoader 打出来，用来肉眼确认
 * 「跑的确实是插件 dex + 插件资源」，而不是宿主的。
 */
public class OtherActivity extends PluginBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        TextView info = findViewById(R.id.info);
        info.setText(getString(R.string.hello_from_plugin)
                + "\n\nActivity    : " + getClass().getName()
                + "\nPackage     : " + getPackageName()
                // getResourcePackageName 返回的是「解析这个资源 id 的资源表」所属的包名，
                // 是插件自己 = 独立资源表生效
                + "\nResPackage  : " + getResources().getResourcePackageName(R.layout.activity_other)
                + "\nClassLoader : " + getClassLoader());

        findViewById(R.id.open_second).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 插件内部跳转：宿主坑位 Activity01 会被复用，
                // 由 PmBase 里动态维护的「坑位 → 插件 Activity」映射表决定加载哪个类
                startActivity(new Intent(OtherActivity.this, SecondActivity.class));
            }
        });

        findViewById(R.id.finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
