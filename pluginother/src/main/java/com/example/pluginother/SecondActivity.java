package com.example.pluginother;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * 插件的第二个页面，用来验证「插件内部跳转」：
 * 宿主只有一个坑位 Activity01，两个插件页面共用它，靠 PmBase 的映射表切换实际加载的类。
 */
public class SecondActivity extends PluginBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        TextView info = findViewById(R.id.second_info);
        info.setText(getString(R.string.second_page)
                + "\n\nActivity    : " + getClass().getName()
                + "\nResPackage  : " + getResources().getResourcePackageName(R.layout.activity_second));

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
