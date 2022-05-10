package com.qxtx.idea.statusbar.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.qxtx.idea.statusbar.R;
import com.qxtx.idea.statusbar.StatusBarLog;
import com.qxtx.idea.statusbar.StatusBarMgr;
import com.qxtx.idea.statusbar.tools.ColorHelper;
import com.qxtx.idea.statusbar.tools.network.NetStateManager;

import java.util.HashMap;

/**
 * @author QXTX-WIN
 * <p><b>Create Date</b></p> 2021/7/14 17:12
 * <p><b>Description</b></p> 自定义的状态栏view，使用R.layout.def_status_bar布局，包含完整的UI实现。
 * <b>特别注意：UI请通过{@link #getContentView()}得到真实的状态栏内容控件，而不是直接通过状态栏对象进行属性的设置</b>
 * · 电池图标的UI方案
 * · sim信号图标的UI方案
 * · 状态栏高对比度主题色的支持
 * · 对于部分纯色背景或者显式设置了背景色的界面，支持沉浸式状态栏，默认启用此方案，可以通过{@link #setImmersive(boolean)}
 */
public class DefaultStatusBar extends BaseStatusBar {

    /** 仿照View.setTag(int, Object), 记录子控件的某些数据。在view被解绑后，立即清除这些数据 */
    private SparseArray<Object> keyedTags = new SparseArray<>(4);

    /** 是否使用沉浸式状态栏 */
    private boolean immersive = true;

    private @NetStateManager.NetType int netType = NetStateManager.NetType.TYPE_NONE;
    private float signalFraction = 0f;

    private int themeColor;

    private View networkTypeGray;

    /** 电量图形 */
    private BatteryView batteryIconView;
    /** 电量文字百分比 */
    private TextView batteryTextView;

    /**
     * Instantiates a new Default status bar.
     *
     * @param context the context
     */
    public DefaultStatusBar(Context context) {
        this(context, R.layout.def_status_bar);
    }

    /**
     * Instantiates a new Default status bar.
     *
     * @param context  the context
     * @param layoutId the layout id
     */
    public DefaultStatusBar(Context context, int layoutId) {
        super(context, layoutId);

        networkTypeGray = findViewById(R.id.networkTypeGray);

        batteryIconView = findViewById(R.id.batteryIcon);
        batteryTextView = findViewById(R.id.batteryText);

        Drawable drawable = contentView.getBackground();
        if (drawable instanceof ColorDrawable) {
            themeColor = ((ColorDrawable)drawable).getColor();
        } else {
            themeColor = Color.BLACK;
        }
    }

    /**
     * 设置是否使用沉浸式状态栏
     * @param immersive the immersive
     */
    public void setImmersive(boolean immersive) {
        if (this.immersive != immersive) {
            setThemeColor(themeColor);
        }
        this.immersive = immersive;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        //view被解绑后，移除全部tag
        keyedTags.clear();
    }

    @Override
    public void onAirplaneChanged(boolean enable) {
        if (airplaneModeView == null || !isUiThread() || isGone()) {
            return;
        }
        Object b = keyedTags.get(airplaneModeView.getId());
        if (b instanceof Boolean) {
            if ((Boolean) b == enable) {
                StatusBarLog.i("飞行模式未改变，不做处理");
                return;
            }
        }
        keyedTags.put(airplaneModeView.getId(), enable);

        super.onAirplaneChanged(enable);

        if (enable) {
            if (networkTypeGray != null) {
                networkTypeGray.setVisibility(GONE);
            }
        }

        if (immersive) {
            updateAirplaneThemeColor();
        }
    }

    @Override
    public void onHeadSetChanged(boolean exist) {
        if (headsetView == null || !isUiThread() || isGone()) {
            return;
        }
        Object b = keyedTags.get(headsetView.getId());
        if (b instanceof Boolean) {
            if ((Boolean) b == exist) {
                StatusBarLog.i("耳机状态未改变，不做处理");
                return;
            }
        }
        keyedTags.put(headsetView.getId(), exist);

        super.onHeadSetChanged(exist);

        if (immersive) {
            updateHeadsetThemeColor();
        }
    }

    @Override
    public void onNetworkTypeChanged(int type, float signalFraction, int transferState) {
        if (networkTypeView == null || !isUiThread() || isGone()) {
            return;
        }
        Object obj = keyedTags.get(networkTypeView.getId());
        if (obj instanceof Pair) {
            Pair<Integer, Object[]> pair = (Pair<Integer, Object[]>)obj;
            boolean isSameType = pair.first != null && pair.first == type;
            Object curSignalFraction = pair.second[0];
            Object curTransferState = pair.second[1];
            boolean isSameSignal = (curSignalFraction instanceof Float) && (Float)curSignalFraction == signalFraction;
            boolean isSameTransferState = (curTransferState instanceof Integer) && (Integer)curTransferState == transferState;
            if (isSameType && isSameTransferState && isSameSignal) {
                StatusBarLog.i("网络状态未改变，不做处理");
                return;
            }
        }
        keyedTags.put(networkTypeView.getId(), new Pair<>(type, new Object[] {signalFraction, transferState}));

        if (networkTypeGray != null) {
            networkTypeGray.setVisibility(GONE);
        }
        super.onNetworkTypeChanged(type, signalFraction, transferState);

        this.netType = type;
        this.signalFraction = signalFraction;

        boolean isWifi = type == NetStateManager.NetType.TYPE_WIFI;
        if (!isWifi) {
            if (networkTypeGray != null) {
                networkTypeGray.setVisibility(GONE);
            }
        }

        if (immersive) {
            updateNetworkTypeThemeColor(isWifi, signalFraction);
        }
    }

    @Override
    public void onBatteryChanged(float fraction, boolean isCharging) {
        if (batteryView == null || !isUiThread() || isGone()) {
            return ;
        }
        if (fraction > 1f) {
            StatusBarLog.e("Error! Invalid fraction of battery level.");
            return ;
        }

        Object obj = keyedTags.get(batteryView.getId());
        if (obj instanceof Pair) {
            Pair<Float, Boolean> pair = (Pair<Float, Boolean>)obj;
            boolean isSameFraction = pair.first != null && pair.first == fraction;
            boolean isSameCharging = pair.second != null && pair.second == isCharging;
            if (isSameFraction && isSameCharging) {
                StatusBarLog.i("电池状态未改变，不做处理");
                return;
            }
        }
        keyedTags.put(batteryView.getId(), new Pair<>(fraction, isCharging));

        update(batteryView, () -> {
            if (batteryTextView != null) {
                String targetText = (int)(fraction * 100 + 0.5f) + "%";
                if (!batteryTextView.getText().toString().equals(targetText)) {
                    batteryTextView.setText(targetText);
                    //StatusBarLog.e("更新电量文字：" + targetText);
                }

                batteryTextView.setTextColor(isCharging ? Color.GREEN : Color.WHITE);
                batteryTextView.setVisibility(VISIBLE);
            }

            batteryView.setVisibility(VISIBLE);

            if (batteryIconView != null) {
                batteryIconView.update(fraction, isCharging);
            }

            if (immersive) {
                updateBatteryThemeColor();
            }
        });
    }

    /**
     * 注意飞行模式下不应显示sim信号图标
     * @param simInfoMap sim卡信息集
     * @param primarySubId 网络卡id
     */
    @Override
    public void onSimChanged(HashMap<Integer, StatusBarMgr.SimInfo> simInfoMap, int primarySubId) {
        if (simView == null || !isUiThread() || isGone()) {
            return;
        }

        ((SimSignalView) simView).update(simInfoMap, primarySubId);

        if (immersive) {
            updateSimThemeColor();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (immersive) {
            //long durationMs = System.currentTimeMillis();
            ViewGroup parent = (ViewGroup) getParent();
            ViewGroup viewContent = parent.findViewById(android.R.id.content);
            if (viewContent.getChildCount() > 0) {
                View userView = viewContent.getChildAt(0);
                Drawable drawable = userView.getBackground();

                int color = Color.BLACK;
                float brightnessSum = 0f;
                if (drawable instanceof ColorDrawable) {
                    color = ((ColorDrawable) drawable).getColor();
                    brightnessSum = ColorHelper.evaluateColorBrightness(color);
                } else if (drawable != null) {
                    int w = drawable.getIntrinsicWidth();
                    int h = drawable.getIntrinsicHeight();
                    if (w > 0 && h > 0) {
                        Bitmap bitmap;
                        if (drawable instanceof BitmapDrawable) {
                            bitmap = ((BitmapDrawable) drawable).getBitmap();
                            w = bitmap.getWidth();
                        } else {
                            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(bitmap);
                            drawable.draw(canvas);
                        }

                        int[] pixels = new int[w * 2];
                        bitmap.getPixels(pixels, 0, w, 0, 0, w, 2);
                        for (int i : pixels) {
                            brightnessSum += ColorHelper.evaluateColorBrightness(i);
                        }
                        brightnessSum /= pixels.length;
                        color = brightnessSum < 0.5f ? Color.BLACK : Color.WHITE;
                    }
                } else {
                    StatusBarLog.w("无法获取界面过度色值，使用预置颜色：" + Integer.toHexString(color));
                }

//        StatusBarLog.e("界面状态栏过渡背景色平均亮度：" + brightnessSum
//                + ", 计算对比度主题色耗时：" + (System.currentTimeMillis() - durationMs) + "ms."
//                + ", 使用背景色：" + Integer.toHexString(color));

                final int bgColor = color;
                int themeColor = brightnessSum < 0.5f ? Color.WHITE : Color.BLACK;
                post(() -> {
                    //沉浸式
                    setThemeColor(themeColor, bgColor);
                });
            }
        }
    }

    /**
     * On sim changed.
     *
     * @param primaryLevel the primary level
     * @param subLevel     the sub level
     */
    public void onSimChanged(int primaryLevel, int subLevel) {
        if (simView != null) {
            ((SimSignalView) simView).update(primaryLevel, subLevel);
        }

        updateSimThemeColor();
    }

    private void updateSimThemeColor() {
        if (simView == null || simView.getVisibility() != VISIBLE
                || themeColor == Color.TRANSPARENT) {
            return;
        }
        post(() -> {
            ((SimSignalView)simView).setThemeColor(themeColor);
            //sim背景色不变
        });
    }

    private void updateBatteryThemeColor() {
        if (batteryView == null || batteryView.getVisibility() != VISIBLE
                || themeColor == Color.TRANSPARENT) {
            return;
        }

        if (batteryIconView == null) {
            return;
        }

        post(() -> {
            batteryIconView.setThemeColor(themeColor);
            if (batteryTextView != null && !batteryIconView.isCharging) {
                batteryTextView.setTextColor(themeColor);
            }
        });
    }

    private void updateHeadsetThemeColor() {
        if (headsetView == null || headsetView.getVisibility() != VISIBLE || themeColor == Color.TRANSPARENT) {
            return;
        }
        post(() -> {
            Drawable drawable = headsetView.getBackground();
            if (drawable == null) {
                StatusBarLog.e("找不到耳机图标资源！");
                return;
            }
            drawable.setTint(themeColor);
            headsetView.setBackground(drawable);
        });
    }

    private void updateNetworkTypeThemeColor(boolean isWifi, float signalFraction) {
        if (networkTypeView == null || networkTypeView.getVisibility() != VISIBLE
                || themeColor == Color.TRANSPARENT) {
            return;
        }
        post(() -> {
            Drawable drawable = networkTypeView.getBackground();
            if (drawable == null) {
                StatusBarLog.i("找不到网络图标资源！");
                return;
            }
            drawable.setTint(themeColor);
            networkTypeView.setBackground(drawable);

            if (networkTypeGray == null) {
                return;
            }

            int resId;
            if (isWifi) {
                if (signalFraction > 0.85f) {
                    resId = NO_ID;
                } else if (signalFraction > 0.65f) {
                    resId = R.drawable.sb_network_wifi_3_ic_gray;
                } else if (signalFraction > 0.45f) {
                    resId = R.drawable.sb_network_wifi_2_ic_gray;
                } else if (signalFraction > 0.2f) {
                    resId = R.drawable.sb_network_wifi_1_ic_gray;
                } else {
                    resId = R.drawable.sb_network_wifi_0_ic_gray;
                }

                if (resId != NO_ID) {
                    networkTypeGray.setBackgroundResource(resId);
                    networkTypeGray.setVisibility(VISIBLE);
                } else {
                    networkTypeGray.setVisibility(GONE);
                }
            }
        });
    }

    private void updateAirplaneThemeColor() {
        if (airplaneModeView == null || airplaneModeView.getVisibility() != VISIBLE
                || themeColor == Color.TRANSPARENT) {
            return;
        }
        post(() -> {
            Drawable drawable = airplaneModeView.getBackground();
            if (drawable == null) {
                StatusBarLog.e("找不到飞行模式图标资源！");
                return;
            }
            drawable.setTint(themeColor);
            airplaneModeView.setBackground(drawable);
        });
    }

    /**
     * 设置主题色，并以此为状态栏图标颜色，然后设置背景色为强对比色（黑白）
     *
     * @param color 颜色值
     */
    public void setThemeColor(int color) {
        themeColor = color;

        color &= 0xffffff;
        float brightness;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            brightness = Color.luminance(color);
        } else {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            brightness = hsv[2];
        }
        //StatusBarLog.e("亮度值：" + brightness);
        super.getContentView().setBackgroundColor(brightness > 0.5f ? Color.BLACK : Color.WHITE);

        updateAirplaneThemeColor();
        updateHeadsetThemeColor();
        updateNetworkTypeThemeColor(netType == NetStateManager.NetType.TYPE_WIFI, signalFraction);
        updateBatteryThemeColor();
        updateSimThemeColor();
    }

    /**
     * 设置主题色、背景色
     *
     * @param themeColor 主题色
     * @param bgColor    背景色
     */
    public void setThemeColor(int themeColor, int bgColor) {
        this.themeColor = themeColor;

        super.getContentView().setBackgroundColor(bgColor);

        updateAirplaneThemeColor();
        updateHeadsetThemeColor();
        updateNetworkTypeThemeColor(netType == NetStateManager.NetType.TYPE_WIFI, signalFraction);
        updateBatteryThemeColor();
        updateSimThemeColor();
    }

    /** 不允许获得子控件 */
    @Override
    public View getContentView() {
        return null;
    }
}
