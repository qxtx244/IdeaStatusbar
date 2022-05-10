package com.qxtx.idea.statusbar.demo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class BlackBgActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_black_bg);

        Toast.makeText(this, "2秒后跳转到灰色界面...", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, GrayBgActivity.class));
            finish();
        }, 2000);
    }
}