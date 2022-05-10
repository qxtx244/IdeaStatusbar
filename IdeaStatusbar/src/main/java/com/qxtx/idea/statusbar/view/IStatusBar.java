package com.qxtx.idea.statusbar.view;

import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.qxtx.idea.statusbar.StatusBarMgr;
import com.qxtx.idea.statusbar.tools.network.NetStateManager;

import java.util.HashMap;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/7/19 10:51
 * <p><b>Description</b></p> 状态栏接口
 */
public interface IStatusBar {

    /**
     * Gets content view.
     *
     * @return the content view
     */
    View getContentView();

    /**
     * 获取wifi信号最大级数
     * @return the wifi max level
     */
    int getWifiMaxLevel();

    /**
     * sim卡的改变
     *
     * @param simInfoMap   sim卡信息集
     * @param primarySubId 主卡subId
     */
    void onSimChanged(HashMap<Integer, StatusBarMgr.SimInfo> simInfoMap, int primarySubId);

    /**
     * 电量状态改变
     *
     * @param fraction   电量百分比，范围为[0f,1f]
     * @param isCharging 是否正在充电
     */
    void onBatteryChanged(float fraction, boolean isCharging);

    /**
     * 飞行模式开关状态改变
     *
     * @param enable 是否启用
     */
    void onAirplaneChanged(boolean enable);

    /**
     * 网络状态改变
     *
     * @param type           目标网络类型等效值
     * @param signalFraction 信号强度百分比，范围为[0f,1f]
     * @param transferState  数据传输状态（上行、下行、双工状态），取值见{@link StatusBarMgr.TransferState}
     */
    void onNetworkTypeChanged(@NetStateManager.NetType int type, float signalFraction, int transferState);

    /**
     * 耳机状态改变
     *
     * @param exist 是否存在耳机
     */
    void onHeadSetChanged(boolean exist);

    /**
     * 是否处于UI线程
     * @return the boolean
     */
    default boolean isUiThread() {
        boolean ret = Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper();
        if (!ret) {
            Log.i(getClass().getSimpleName(), "There not the main thread!");
        }
        return ret;
    }
}
