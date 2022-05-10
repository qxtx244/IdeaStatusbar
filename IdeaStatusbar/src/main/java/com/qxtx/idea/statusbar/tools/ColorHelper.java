package com.qxtx.idea.statusbar.tools;

import android.graphics.Color;
import android.os.Build;

/**
 * @author QXTX-WIN
 * <p><b>Create Date</b></p> 2021/9/18 1:18
 * <p><b>Description</b></p> rgb颜色助手类
 */
public class ColorHelper {

    private static final float[] hsv = new float[3];

    /**
     * 评测rgb颜色的亮度值，范围为[0f, 1f]  @param color the color
     *
     * @return the float
     */
    public static float evaluateColorBrightness(int color) {
        float ret = 1f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ret = Color.luminance(color);
        } else {
            Color.colorToHSV(color, hsv);
            ret = hsv[2];
        }
        return ret;
    }
}
