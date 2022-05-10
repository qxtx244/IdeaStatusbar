package com.qxtx.idea.statusbar.demo;

import android.app.Application;
import android.content.Context;

import com.qxtx.idea.statusbar.demo.tools.Reflection;

/**
 * @author QXTX-WIN
 * Create Date 2022/5/10 23:06
 * Description
 */
public class DemoApplication extends Application  {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        Reflection.unseal(base);
    }
}
