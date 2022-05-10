package com.qxtx.idea.statusbar.demo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class DrawableDarkBgActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawable_dark_bg);

        Toast.makeText(this, "3秒后跳转到亮色图片界面...", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, DrawableLightBgActivity.class));
            finish();
        }, 3000);
    }
}