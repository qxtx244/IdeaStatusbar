package com.qxtx.idea.statusbar.demo;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class DrawableLightBgActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawable_light_bg);

        Toast.makeText(this, "3秒后返回主界面...", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> {
            finish();
        }, 3000);
    }
}