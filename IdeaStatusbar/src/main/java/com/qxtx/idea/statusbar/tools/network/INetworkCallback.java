package com.qxtx.idea.statusbar.tools.network;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * @author QXTX-WORK
 * <p>Create Date 2020/11/16 11:13
 * <p><b>Description</b>
 * <pre>
 *     外部网络事件接口。
 * </pre>
 *
 * @see NetStateManager
 */
public interface INetworkCallback {

    /**
     * 网络已可用（但不代表能联网）
     *
     * @param network the network
     */
    void onAvailable(Network network);

    /**
     * 网络能力发生了某些变化时回调，并且附带一些信息。
     * 显然，这很可能多次被回调，也并不代表每次回调都是可用和不可用之间的切换标志
     *
     * @param network             the network
     * @param networkCapabilities the network capabilities
     */
    void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities);

    /**
     * （已连接的）网络属性发生了改变时回调。从实际运行来看，每次可用网络变化只回调一次
     *
     * @param network        the network
     * @param linkProperties 包含一些路由信息
     */
    void onLinkPropertiesChanged(Network network, LinkProperties linkProperties);

    /**
     * 网络正常的情况下，有数据丢失。有可能并不会回调
     *
     * @param network     the network
     * @param maxMsToLive 尝试保持网络连接的最大时长
     */
    void onLosing(Network network, int maxMsToLive);

    /**
     * 网络变得不可用时回调
     *
     * @param network the network
     */
    void onLost(Network network);

    /**
     * 网络已打开，但超时时间内没能连接成功时回调
     */
    void onUnavailable();
}
