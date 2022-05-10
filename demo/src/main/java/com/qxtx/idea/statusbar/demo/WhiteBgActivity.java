package com.qxtx.idea.statusbar.demo;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

public class WhiteBgActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_white_bg);

        Toast.makeText(this, "2秒后跳转到黑色背景界面...", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, BlackBgActivity.class));
            finish();
        }, 2000);
    }
}