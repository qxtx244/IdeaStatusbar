package com.qxtx.idea.statusbar;

/**
 * The interface Status bar mgr.
 *
 * @author QXTX-WIN
 * <p><b>Create Date</b></p> 2021/7/18 20:31
 * <p><b>Description</b></p> 状态栏管理类接口
 */
public interface IStatusBarMgr {

    /**
     * 当前是否为启用状态
     * @return the boolean
     */
    boolean isStatusBarEnable();

    /**
     * 是否启用状态栏
     * @param statusBarEnable the status bar enable
     */
    void setStatusBarEnable(boolean statusBarEnable);

    /**
     * 显示状态栏。受限于{@link #setStatusBarEnable(boolean)}，如果状态栏处于未启用状态，此方法无效。
     * 并且，此方法仅改变其可见性，并不会主动绑定状态栏，如果在{@link android.app.Activity}中调用，当前界面不一定能成功自动显示状态栏，
     * 因为状态栏只会响应在它的onResume()事件，即使此时手动调用此方法，也不会被显示，因为状态栏还未绑定到界面。
     */
    void show();

    /**
     * 隐藏状态栏。受限于{@link #setStatusBarEnable(boolean)}，如果状态栏处于未启用状态，此方法无效。
     * 并且，此方法仅改变其可见性，并不会主动解绑状态栏
     */
    void hide();
}
