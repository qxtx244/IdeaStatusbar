package com.qxtx.idea.statusbar.tools.network;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2021/2/3 10:20
 * <p><b>Description</b></p>
 * <pre>
 *     网络变化监听类的监听适配器
 * </pre>
 *
 * @see NetStateManager
 */
public class NetworkEventCallbackAdapter implements INetworkCallback {

    @Override
    public void onAvailable(Network network) {
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
    }

    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
    }

    @Override
    public void onLosing(Network network, int maxMsToLive) {
    }

    @Override
    public void onLost(Network network) {
    }

    @Override
    public void onUnavailable() {
    }
}
