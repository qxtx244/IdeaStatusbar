package com.qxtx.idea.statusbar.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.View;

import com.qxtx.idea.statusbar.R;
import com.qxtx.idea.statusbar.StatusBarLog;
import com.qxtx.idea.statusbar.StatusBarMgr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/9/13 19:01
 * <p><b>Description</b></p> sim自定义控件，支持双卡。4格信号表示，初始状态为sim均不可用状态。
 * 注意，只要主卡不可用，不管副卡是否可用，均视为全部sim不可用状态 · 暂不支持{@link #setPadding(int, int, int, int)} 和  {@link #setPaddingRelative(int, int, int, int)}
 */
public class SimSignalView extends View implements ISimSignal {

    private final Context appContext;

    private final Drawable noSimDrawable;

    private final Paint paint;

    /**
     * The constant DEF_CORNER_RADIUS_MAX.
     */
    public static final float DEF_CORNER_RADIUS_MAX = 0.5f;
    /**
     * The constant LEVEL_NO_SIGNAL.
     */
    public static final int LEVEL_NO_SIGNAL = -1;

    private final int DEF_FORE_COLOR = Color.WHITE;
    private final float DEF_CORNER_RADIUS_X = 0.2f;
    private final float DEF_CORNER_RADIUS_Y = 0.2f;
    private final int DEF_BG_COLOR = Color.parseColor("#676767");

    /** 主sim信号等级，有效等级范围为[0, 4]，如果为{@link #LEVEL_NO_SIGNAL}则说明无信号 */
    private int primaryLevel;

    /** 副sim信号等级，有效等级范围为[0, 4]，如果为{@link #LEVEL_NO_SIGNAL}则说明无信号 */
    private int subLevel;

    /** 主题色，包括有效信号的颜色，以及sim不可用图标的颜色 */
    private int themeColor;
    /** 无效信号单元的颜色 */
    private int signalBgColor;

    /** 矩形圆角半径，元素0为椭圆x半径占矩形x百分值，元素1为椭圆y半径占矩形y百分值。范围均为(0, 0.5f]，默认均为0.2f */
    private final float[] cornerRadius;

    /**
     * Instantiates a new Sim signal view.
     *
     * @param context the context
     * @param attrs   the attrs
     */
    public SimSignalView( Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        appContext = context.getApplicationContext();

        noSimDrawable = getResources().getDrawable(R.drawable.sb_signal_disabled_ic);

        paint = new Paint();

        float cornerRadiusX = DEF_CORNER_RADIUS_X;
        float cornerRadiusY = DEF_CORNER_RADIUS_Y;
        int bgColor = DEF_BG_COLOR;
        int themeColor = DEF_FORE_COLOR;
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.SimSignalView);
            int primarySimLevel = typedArray.getInt(R.styleable.SimSignalView_primaryLevel, LEVEL_NO_SIGNAL);
            primaryLevel = primarySimLevel < 0 ? LEVEL_NO_SIGNAL : primarySimLevel;

            int subSimLevel = typedArray.getInt(R.styleable.SimSignalView_subLevel, LEVEL_NO_SIGNAL);
            subLevel = subSimLevel < 0 ? LEVEL_NO_SIGNAL : subSimLevel;

            cornerRadiusX = typedArray.getFloat(R.styleable.SimSignalView_roundRectRadiusX, cornerRadiusX);
            cornerRadiusY = typedArray.getFloat(R.styleable.SimSignalView_roundRectRadiusY, cornerRadiusY);

            bgColor = typedArray.getColor(R.styleable.SimSignalView_signalBgColor, bgColor);
            themeColor = typedArray.getColor(R.styleable.SimSignalView_signalThemeColor, themeColor);

            typedArray.recycle();
        }

        signalBgColor = bgColor;
        this.themeColor = themeColor;
        cornerRadius = new float[] {
                Math.min(DEF_CORNER_RADIUS_MAX, cornerRadiusX),
                Math.min(DEF_CORNER_RADIUS_MAX, cornerRadiusY)};

        invalidate();
    }

    @Override
    public void setThemeColor(int color) {
        this.themeColor = color;
        postInvalidate();
    }

    @Override
    public void setSignalBgColor(int color) {
        this.signalBgColor = color;
    }

    @Override
    public void update(int primaryLevel, int subLevel) {
        primaryLevel = Math.min(4, primaryLevel);
        primaryLevel = primaryLevel < 0 ? LEVEL_NO_SIGNAL : primaryLevel;
        subLevel = Math.min(4, subLevel);
        subLevel = subLevel < 0 ? LEVEL_NO_SIGNAL : subLevel;

        if (this.primaryLevel != primaryLevel || this.subLevel != subLevel) {
            this.primaryLevel = primaryLevel;
            this.subLevel = subLevel;
            postInvalidate();
        } else {
            StatusBarLog.d("状态变化，不更新");
        }
    }

    /**
     * 更新控件
     *
     * @param simInfoMap   sim信息集
     * @param primarySubId 主数据卡id
     */
    @UiThread
    public void update(HashMap<Integer, StatusBarMgr.SimInfo> simInfoMap, int primarySubId) {
        if (!StatusBarMgr.isUiThread()) {
            StatusBarLog.e("禁止在非UI线程更新状态栏sim信号！");
            return;
        }

        StatusBarLog.d("即将更新状态栏sim图标... 主卡subId=" + primarySubId + ", sim信息集=" + (simInfoMap == null ? null : simInfoMap.toString()));

        boolean isSimExist = simInfoMap != null && !simInfoMap.isEmpty();

        if (StatusBarMgr.isAirplaneMode(appContext)) {
            StatusBarLog.d("飞行模式下不显示sim卡信号");
            //飞行模式，隐藏状态栏中所有的sim信号图标
            if (primaryLevel != LEVEL_NO_SIGNAL || subLevel != LEVEL_NO_SIGNAL) {
                primaryLevel = LEVEL_NO_SIGNAL;
                subLevel = LEVEL_NO_SIGNAL;
                invalidate();
            }
            setVisibility(GONE);
            return;
        }

        List<Integer> slotIdList = null;
        boolean isSimValid = false;
        if (simInfoMap != null) {
            for (StatusBarMgr.SimInfo info : simInfoMap.values()) {
                if (info != null
                        //LYX_TAG 2021/9/6 11:24 需求变动：不再检查sim卡的数据业务是否可用，只要有卡在，就显示信号（假信号）
                        //  原代码：&& StatusBarMgr.SimInfo.isSimReady(info.getSimState()) && StatusBarMgr.SimInfo.isSimDataReg(info.getServiceState())) {
                        && StatusBarMgr.SimInfo.isSimReady(info.getSimState())) {

                    isSimValid = true;

                    if (slotIdList == null) {
                        slotIdList = new ArrayList<>();
                    }
                    slotIdList.add(info.getSlotId());
                }
            }
        }

        //未处于飞行模式，无论如何均显示sim占位
        setVisibility(VISIBLE);

        //计算出当前主卡/副卡的信号等级
        int newPrimaryLevel = Integer.MIN_VALUE;
        int newSubLevel = Integer.MIN_VALUE;
        if (!isSimExist || !isSimValid) {
            StatusBarLog.d("无sim卡或无不可用sim卡");
            newPrimaryLevel = LEVEL_NO_SIGNAL;
            newSubLevel = LEVEL_NO_SIGNAL;
        } else {
            for (Integer slotId : slotIdList) {
                StatusBarMgr.SimInfo info = simInfoMap.get(slotId);
                if (info == null) {
                    continue;
                }
                int level = info.getSignalLevel();
                if (info.getSubId() == primarySubId) {
                    newPrimaryLevel = level;
                } else {
                    newSubLevel = level;
                }
            }
        }

        if (newPrimaryLevel == primaryLevel && newSubLevel == subLevel) {
            return;
        }

        primaryLevel = newPrimaryLevel;
        subLevel = newSubLevel;
        invalidate();
    }

    /**
     * 获取当前的主信号等级。当控件从未被更新时，将返回默认值而不是实际值
     *
     * @return the primary signal
     */
    protected int getPrimarySignal() {
        return primaryLevel;
    }

    /**
     * 获取当前的副信号等级。当控件从未被更新时，将返回默认值而不是实际值
     *
     * @return the sub signal
     */
    protected int getSubSignal() {
        return subLevel;
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        //禁止使用偏移
        StatusBarLog.w("");
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        //禁止使用偏移
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        byte signalCount = calculateSimNum();
        switch (signalCount) {
            case 0:
                //无卡
                drawSimDisabled(canvas);
                break;
            case 1:
                //单卡
                drawSingleSim(canvas);
                break;
            case 2:
                //双卡
                drawDualSim(canvas);
                break;
        }
    }

    /**
     * 绘制双卡信号
     * 信号单位宽度：3/16控件宽度
     * 信号单位之间的间距：1/12控件宽度
     * 信号矩形圆角x轴半径：1/5信号单位宽度，y轴半径：1/5信号单位高度
     *  每个信号单元占据0.19控件高度
     *
     * 主信号占据0.72控件高度
     * 副信号占据0.18控件高度
     * 两信号上下间隔0.1控件高度
     */
    private void drawDualSim(Canvas canvas) {
        int viewH = getHeight();
        int viewW = getWidth();
        float simGap = viewH * 0.1f;
        float unitW = viewW * 3 / 16f;
        float unitH = viewH * 0.18f;
        float gapW = viewW / 12f;
        float rrx = unitW * cornerRadius[0];
        float rry = unitH * cornerRadius[1];

        float l, t;
        int color;
        float primaryBottom = viewH - (unitH + simGap);
        for (int i = 0; i < 4; i++) {
            l = i * (unitW + gapW);

            //主信号
            t = viewH - simGap - unitH - (i + 1) * unitH;
            color = i < primaryLevel ? themeColor : signalBgColor;
            drawRoundRect(canvas, l, t, l + unitW, primaryBottom, rrx, rry, color);

            //副信号
            t = viewH - unitH;
            color = i < subLevel ? themeColor : signalBgColor;
            drawRoundRect(canvas, l, t, l + unitW, viewH, rrx, rry, color);
        }
    }

    /**
     * 绘制单卡信号，铺满控件
     * 信号单位宽度：3/16控件宽度
     * 信号单位高度：1/4控件高度
     * 信号单位之间的间距：1/12控件宽度
     * 信号矩形圆角x轴半径：1/5信号单位宽度，y轴半径：1/5信号单位高度
     */
    private void drawSingleSim(Canvas canvas) {
        int viewW = getWidth();
        int viewH = getHeight();
        float unitW = viewW * 3 / 16f;
        float unitH = viewH / 4f;
        float gapW = viewW / 12f;
        float rrx = unitW * cornerRadius[0];
        float rry = unitH * cornerRadius[1];
        float startL, startT;
        int color;
        for (int i = 0; i < 4; i++) {
            startL = i * (unitW + gapW);
            startT = viewH - (i + 1) * unitH;
            color = i < primaryLevel ? themeColor : signalBgColor;
            drawRoundRect(canvas, startL, startT, startL + unitW, viewH, rrx, rry, color);
        }
    }

    /**
     * 绘制无卡图标，尽可能地铺满控件
     * 由于内置的svg资源的比例问题，因此这里强制使用比例尺寸缩放图形，以防止变形
     */
    private void drawSimDisabled(Canvas canvas) {
        if (noSimDrawable != null) {
            int w = getWidth();
            int h = getHeight();
            float l = 0, t = 0, r = w, b = h;
            float fixLen;
            if (w > (h * 4 / 5f)) {
                fixLen = h * 4 / 5f;
                l = (w - fixLen) / 2f;
                r = l + fixLen;
            } else {
                fixLen = w * 5 / 4f;
                t = (h - fixLen) / 2f;
                b = t + fixLen;
            }

            noSimDrawable.setBounds(
                    (int)(l + 0.5f), (int)(t + 0.5f),
                    (int)(r + 0.5f), (int)(b + 0.5f));
            noSimDrawable.setTint(themeColor);
            //DrawableCompat.setTint(noSimDrawable, themeColor);
            noSimDrawable.draw(canvas);
        }
    }

    /** 绘制信号单元 */
    private void drawRoundRect(Canvas canvas, float l, float t, float r, float b, float rx, float ry, int color) {
        paint.reset();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(l, t, r, b, rx, ry, paint);
    }

    private byte calculateSimNum() {
        byte result = 0;
        if (primaryLevel >= 0) {
            result = 1;
            if (subLevel >= 0) {
                result = 2;
            }
        }
        return result;
    }
}
