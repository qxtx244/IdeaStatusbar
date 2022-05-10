package com.qxtx.idea.statusbar.tools.network;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2020/3/18 14:11
 * <p><b>Description</b></p>
 * <pre>
 *     网络事件回调的适配类，可以按需重写回调事件
 * </pre>
 */
public class NetworkCallbackAdapter implements INetworkCallback {

    @Override
    public void onAvailable(Network network) { }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) { }

    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) { }

    @Override
    public void onLosing(Network network, int maxMsToLive) { }

    @Override
    public void onLost(Network network) { }

    @Override
    public void onUnavailable() { }
}
