package com.qxtx.idea.statusbar.view;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/9/17 19:13
 * <p><b>Description</b></p> 电池控件接口
 * @see BatteryView
 */
public interface IBattery {

    /**
     * 设置主题色
     * @param color the color
     */
    void setThemeColor(int color);

    /**
     * 设置充电时的颜色
     * @param color the color
     */
    void setChargingColor(int color);

    /**
     * 设置充电动画策略，如果任一参数不符合要求，则设置将不会成功。设置成功后，在下一次电池状态改变时生效。
     *
     * @param scale 充电动画的段数，范围为[1, 100]
     * @param speed 充电动画的执行速度，单位为 毫秒/次动画循环，范围为[1000, 1800000]
     */
    void setChargingAnim(int scale, int speed);

    /**
     * 更新电池控件
     *
     * @param newFraction 目标电量百分比，范围为[0f, 1f]
     * @param isCharging  目标充电状态，true为正在充电，false为未在充电
     */
    void update(float newFraction, boolean isCharging);
}
