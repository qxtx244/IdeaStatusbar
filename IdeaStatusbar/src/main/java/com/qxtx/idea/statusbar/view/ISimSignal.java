package com.qxtx.idea.statusbar.view;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/9/17 19:08
 * <p><b>Description</b></p> sim信号控件的接口
 * @see SimSignalView
 */
public interface ISimSignal {

    /**
     * 更新控件
     *
     * @param primaryLevel the primary level
     * @param subLevel     the sub level
     */
    void update(int primaryLevel, int subLevel);

    /**
     * 设置主题色
     * @param color the color
     */
    void setThemeColor(int color);

    /**
     * 设置信号的背景色
     * @param color the color
     */
    void setSignalBgColor(int color);
}
