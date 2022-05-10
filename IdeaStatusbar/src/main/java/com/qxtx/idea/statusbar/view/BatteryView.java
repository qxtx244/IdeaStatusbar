package com.qxtx.idea.statusbar.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.qxtx.idea.statusbar.R;
import com.qxtx.idea.statusbar.StatusBarLog;
import com.qxtx.idea.statusbar.StatusBarMgr;

import java.lang.reflect.Field;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/9/6 19:15
 * <p><b>Description</b></p> 电池图标，支持简单的充电动画（实心矩形电量表示）
 * 暂不支持{@link #setPadding(int, int, int, int)}属性
 */
public class BatteryView extends View implements IBattery {

    private final int DEF_COLOR_CHARGING = Color.GREEN;
    private final int DEF_COLOR_THEME = Color.WHITE;
    private final int DEF_CHARGING_ANIM_SPEED = 6000;
    private final int DEF_CHARGING_ANIM_SCALE = 10;

    private final Paint paint;

    /** 主题色，包括电池外框，电量填充色（未充电） */
    private int themeColor = DEF_COLOR_THEME;

    /** 充电时的电量填充色 */
    private int chargingColor = DEF_COLOR_CHARGING;

    /**
     * 当前图标电量百分比
     */
    protected float fraction = 0f;

    /**
     * 当前动画电量值，范围为[0, 100], 不代表当前真实电量。仅当{@link #isCharging}为true时，此数值有意义
     */
    protected int animLevel = 0;

    /**
     * 是否正在充电
     */
    protected boolean isCharging = false;

    /**
     * 充电动画级数
     */
    protected int chargingAnimScale = DEF_CHARGING_ANIM_SCALE;

    /**
     * 充电动画的速度，单位为 毫秒/次动画循环
     */
    protected int chargingAnimSpeed;

    private ValueAnimator animator = null;

    /**
     * Instantiates a new Battery view.
     *
     * @param context the context
     * @param attrs   the attrs
     */
    public BatteryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.BatteryView);
            chargingColor = typedArray.getColor(R.styleable.BatteryView_chargingColor, DEF_COLOR_CHARGING);
            themeColor = typedArray.getColor(R.styleable.BatteryView_batteryThemeColor, DEF_COLOR_THEME);
            fraction = typedArray.getFloat(R.styleable.BatteryView_batteryFraction, 0f);
            isCharging = typedArray.getBoolean(R.styleable.BatteryView_isCharging, false);
            chargingAnimSpeed = typedArray.getInt(R.styleable.BatteryView_chargingAnimSpeed, DEF_CHARGING_ANIM_SPEED);
            if (chargingAnimSpeed < 2000 || chargingAnimSpeed > 600000) {
                chargingAnimSpeed = DEF_CHARGING_ANIM_SPEED;
            }

            chargingAnimScale = typedArray.getInt(R.styleable.BatteryView_chargingAnimSpeed, DEF_CHARGING_ANIM_SCALE);
            if (chargingAnimScale < 1 || chargingAnimScale > 100) {
                chargingAnimScale = DEF_CHARGING_ANIM_SCALE;
            }

            typedArray.recycle();
        } else {
            chargingAnimSpeed = DEF_CHARGING_ANIM_SPEED;
        }

        paint = new Paint();

        invalidate();
    }

    /**
     * 重置运行时参数，主要是清除在绑定界面后获得的状态参数，但全局配置信息会被保留
     * 一般在该控件从界面解绑后执行，因为解绑之后，此控件中表示的状态已经无意义。
     */
    protected void reset() {
        fraction = 0f;
        animLevel = 0;
        isCharging = false;
        if (animator != null && animator.isStarted()) {
            //StatusBarLog.e("控件从window中解绑...无论如何，立即关闭控件中的动画");
            animator.cancel();
            animator = null;
        }
    }

    /**
     * 获得当前显示的电量百分比，范围为[0f, 1f]。如果控件从未被更新过，则返回默认值而不是实际值
     * @return the current fraction
     */
    public float getCurrentFraction() {
        return fraction;
    }

    /**
     * 获得当前空间的充电表示状态。如果控件从未被更新过，则返回默认值而不是实际值
     * @return the boolean
     */
    public boolean isCharging() {
        return isCharging;
    }

    @Override
    public void setThemeColor(int color) {
        themeColor = color;
        postInvalidate();
    }

    @Override
    public void setChargingColor(int color) {
        chargingColor = color;
    }

    @Override
    public void setChargingAnim(int scale, int speed) {
        if (speed < 2000 || speed > 600000 || scale < 1 || scale > 100) {
            StatusBarLog.e("设置动画策略错误！动画级数的范围应为[1, 100]， 设置值=" + scale
                    + "，动画速度范围应为[2000, 600000]，设置值=" + speed);
            return;
        }

        this.chargingAnimSpeed = speed;
        this.chargingAnimScale = scale;
    }

    @UiThread
    @Override
    public void update(float newFraction, boolean isCharging) {
        if (!StatusBarMgr.isUiThread()) {
            StatusBarLog.e("禁止在非UI线程更新状态栏电量！");
            return;
        }

        boolean isFractionChange = fraction != newFraction;

        //2021/11/9 9:37 发现有时候仅仅判断充电状态，仍然会产生判断问题，因此增加一个动画状态的判断
        boolean isChargeStateChange = this.isCharging != isCharging;
        if (!isChargeStateChange) {
            if (isCharging && (animator == null || !animator.isStarted())) {
                StatusBarLog.d("可能是对充电指示状态判断异常，主动修正...");
                isChargeStateChange = true;
            }
        }

        if (!isFractionChange && !isChargeStateChange) {
            //StatusBarLog.e("电池未发生变化，不更新。" + "fraction=" + fraction + ", isCharging? " + this.isCharging);
            return;
        } else {
            StatusBarLog.d("电池发生变化，更新..." +
                    "目标电量百分比=" + newFraction + "，当前电量百分比=" + fraction + ", 正在充电？" + isCharging);
        }

        fraction = Math.min(1f, newFraction);
        this.isCharging = isCharging;

        if (animator != null && animator.isStarted()) {
            animator.cancel();
        }
        if (isCharging) {
            animLevel = (int)(fraction * 100);
            if (fraction == 1f) {
                //StatusBarLog.e("已充满电，不做充电动画");
                postInvalidate();
                return;
            }

            //2021/10/27 12:11 如果用float，会导致动画数值更新事件回调得太频繁，用int就足够了。只是这样会降低动画效果
            int curLevel = animLevel;
            int duration = (int)((1 - (curLevel / 100f)) * chargingAnimSpeed);
            //最快不能超过2s
            animator = ValueAnimator.ofInt(curLevel, 100).setDuration(Math.max(2000, duration));
            animator.setInterpolator(new LinearInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.addUpdateListener(animation -> {
                Context context = getContext();
                if (context instanceof Activity) {
                    if (((Activity) context).isFinishing()) {
                        StatusBarLog.d("界面已经被销毁，强制取消充电动画...");
                        //StatusBarLog.e("Activity正在finish阶段，立即关闭充电动画");
                        animator.cancel();
                    }
                }

                //可以控制动画精度
                int value = (int)animation.getAnimatedValue();
                if (Math.abs(value - animLevel) >= chargingAnimScale || value == 0 || value == 100) {
                    //StatusBarLog.e("充电动画值：" + value + ", 当前值：" + animLevel);
                    animLevel = value;
                    postInvalidate();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    postInvalidate();
                    animator = null;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    postInvalidate();
                    animator = null;
                }
            });

            //fixup: 强制忽略系统的动画缩放，否则可能导致充电动画不可用（当系统关闭Animator时长缩放时）
            try {
                Field field = ValueAnimator.class.getDeclaredField("sDurationScale");
                field.setAccessible(true);
                field.set(null, 1.0f);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Log.e(getClass().getSimpleName(), "Error！ Failed to ignore system animator setting");
                e.printStackTrace();
            }

            animator.start();
        } else {
            postInvalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        //当从界面解绑，则一些状态要置为“未定义”，否则下一次绑定可能会错乱（解绑后，对象应不带任何外部信息）
        reset();

        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int curThemeColor = themeColor;
        int curChargingColor = chargingColor;

        //小椭圆：实心矩形
        //void addRoundRect(float left, float top, float right, float bottom, float rx, float ry, Path.Direction dir);
        float fixEdge = 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(curThemeColor);
        canvas.drawRoundRect(width / 4f, 0f, width * 0.75f, 2f,
                0.8f, 0.8f, paint);

        //大椭圆：空心矩形
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(curThemeColor);
        canvas.drawRoundRect(fixEdge, 1f + fixEdge, width - fixEdge, height - fixEdge,
                2f, 1f, paint);

        //绘制条形
        paint.setColor(isCharging ? curChargingColor : curThemeColor);
        paint.setStyle(Paint.Style.FILL);

        //如果正在显示充电动画，则使用动画电量值
        float curFraction = isCharging ? (animLevel / 100f) : fraction;
        canvas.drawRect(2f, Math.max(0f, 3f + (1f - curFraction) * (height - 5f)),
                width - 2f, height - 2f, paint);
    }
}
