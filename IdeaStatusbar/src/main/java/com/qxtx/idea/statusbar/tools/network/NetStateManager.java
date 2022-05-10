package com.qxtx.idea.statusbar.tools.network;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author QXTX-WORK
 * <p><b>Create Date</b></p> 2020/3/18 15:36
 * <p><b>Description</b></p>
 * <pre>
 *   网络状态管理器。
 *   通过向此类注册/反注册网络状态监听器{@link INetworkCallback}，实现网络状态变化事件的回调/取消回调。
 *   注册的回调类之间相互独立。
 *   在api23以上，必须动态注册广播接收器，才能够接收到android.net.conn.CONNECTIVITY_CHANGE消息。
 *   改为使用ConnectivityManager完成网络的监听
 *   需求：
 *    1、每次网络改变，通知所有已注册的监听器
 *  </pre>
 */
public class NetStateManager {
    private static final String TAG = NetStateManager.class.getSimpleName();

    private static volatile NetStateManager instance;

    /**
     * The interface Net type.
     */
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetType {
        /**
         * The constant TYPE_UNKNOWN.
         */
        int TYPE_UNKNOWN = Integer.MIN_VALUE;
        /**
         * The constant TYPE_NONE.
         */
        int TYPE_NONE = 0;
        /**
         * The constant TYPE_WIFI.
         */
        int TYPE_WIFI = 1;
        /**
         * The constant TYPE_2G.
         */
        int TYPE_2G = 2;
        /**
         * The constant TYPE_3G.
         */
        int TYPE_3G = 3;
        /**
         * The constant TYPE_4G.
         */
        int TYPE_4G = 4;
        /**
         * The constant TYPE_5G.
         */
        int TYPE_5G = 5;
    }

    /**
     * The interface Network name.
     */
    @Retention(RetentionPolicy.SOURCE)
     public @interface NetworkName {
        /**
         * The constant NET_UNKNOWN.
         */
        String NET_UNKNOWN = "UNKNOWN";
        /**
         * The constant NET_NONE.
         */
//规避外部遗漏判空处理引起空指针异常的问题
        String NET_NONE = "NULL";
        /**
         * The constant NET_WIFI.
         */
        String NET_WIFI = "WIFI";
        /**
         * The constant NET_2G.
         */
        String NET_2G = "2G";
        /**
         * The constant NET_3G.
         */
        String NET_3G = "3G";
        /**
         * The constant NET_4G.
         */
        String NET_4G = "4G";
        /**
         * The constant NET_5G.
         */
        String NET_5G = "5G";
        /**
         * The constant NET_ETHERNET.
         */
        String NET_ETHERNET = "ETHERNET";
    }

    private final Context mContext;

    private ConnectivityManager.OnNetworkActiveListener networkActiveListener;
    private final List<ConnectivityManager.OnNetworkActiveListener> mDefNetworkCallbackActiveList;

    private NetworkCallback mNetworkCallback;
    private final List<INetworkCallback> mCallbackList;

    private NetworkCallback mDefaultNetworkCallback;
    private final List<INetworkCallback> mDefNetworkCallbackList;

    private NetStateManager(Context context) {

        mContext = context == null ? getContextMyself() : context.getApplicationContext();

        mCallbackList = Collections.synchronizedList(new ArrayList<>());
        mDefNetworkCallbackList = Collections.synchronizedList(new ArrayList<>());
        mDefNetworkCallbackActiveList = Collections.synchronizedList(new ArrayList<>());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mContext.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                LOG.E("异常！无法获得必要的权限：" + Manifest.permission.ACCESS_WIFI_STATE);
                return;
            }
        }

        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                mNetworkCallback = new OnNetworkCallback(mCallbackList);
                NetworkRequest.Builder builder = new NetworkRequest.Builder()
//                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
                }
                cm.registerNetworkCallback(builder.build(), mNetworkCallback);

                networkActiveListener = () -> {
                    for (int i = 0; i < mDefNetworkCallbackActiveList.size(); i++) {
                        ConnectivityManager.OnNetworkActiveListener listener = mDefNetworkCallbackActiveList.get(i);
                        if (listener != null) {
                            listener.onNetworkActive();
                        }
                    }
                };
                cm.addDefaultNetworkActiveListener(networkActiveListener);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mDefaultNetworkCallback = new OnNetworkCallback(mDefNetworkCallbackList);
                    cm.registerDefaultNetworkCallback(mDefaultNetworkCallback);
                }
            }
        } catch (Exception e) {
            LOG.E("网络监听异常! " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets instance.
     *
     * @param context the context
     * @return the instance
     */
    public static NetStateManager getInstance(Context context) {
        if (instance ==  null) {
            synchronized (NetStateManager.class) {
                if (instance == null) {
                    instance = new NetStateManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static NetStateManager getInstance() {
        if (instance ==  null) {
            synchronized (NetStateManager.class) {
                if (instance == null) {
                    instance = new NetStateManager(null);
                }
            }
        }
        return instance;
    }

    /**
     * 添加网络变更事件回调，以处理受网络变更影响的业务。
     *
     * @param callback the callback
     * @see #removeNetworkCallback(INetworkCallback) #removeNetworkCallback(INetworkCallback)
     * @see #addDefNetworkCallback(INetworkCallback) #addDefNetworkCallback(INetworkCallback)
     */
    public void addNetworkCallback(INetworkCallback callback) {
        if (callback == null) {
            mCallbackList.remove(null);
            return;
        }
        if (mCallbackList.contains(callback)) {
            return ;
        }
        boolean ret = mCallbackList.add(callback);
        if (!ret) {
            LOG.E("添加网络状态监听器失败了");
        } else {
            //laiyx 2022/1/5 14:51 总是触发一次网络事件，作为初始事件
            if (isNetworkAvailable()) {
                ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                Network network = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    network = cm.getActiveNetwork();
                }
                callback.onAvailable(network);
                callback.onCapabilitiesChanged(network, cm.getNetworkCapabilities(network));
            } else {
                callback.onUnavailable();
            }
        }
    }

    /**
     * 移除某个网络变更事件回调监听
     *
     * @param callback the callback
     * @see #addNetworkCallback(INetworkCallback) #addNetworkCallback(INetworkCallback)
     */
    public void removeNetworkCallback(INetworkCallback callback) {
        mCallbackList.remove(callback);
    }

    /**
     * 添加一个对默认网络的状态改变事件回调，以处理受网络变更影响的业务。
     *
     * @param callback 回调对象
     * @see #addNetworkCallback(INetworkCallback) #addNetworkCallback(INetworkCallback)
     */
    public void addDefNetworkCallback(INetworkCallback callback) {
        if (callback == null) {
            mDefNetworkCallbackList.remove(null);
            return;
        }
        if (mDefNetworkCallbackList.contains(callback)) {
            return;
        }
        mDefNetworkCallbackList.add(callback);

        //laiyx 2022/1/5 14:51 总是触发一次网络事件，作为初始事件
        if (isNetworkAvailable()) {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                network = cm.getActiveNetwork();
            }
            callback.onAvailable(network);
            callback.onCapabilitiesChanged(network, cm.getNetworkCapabilities(network));
        } else {
            callback.onUnavailable();
        }
    }

    /**
     * 移除一个对默认网络的状态改变事件回调
     *
     * @param callback 回调对象
     * @see #addDefNetworkCallback(INetworkCallback) #addDefNetworkCallback(INetworkCallback)
     */
    public void removeDefNetworkCallback(INetworkCallback callback) {
        mDefNetworkCallbackList.remove(callback);
    }

    /**
     * 添加一个默认网络可用的事件回调，以处理受网络变更影响的业务。注意，这仅仅在系统默认网络可用时触发。
     *
     * @param listener 回调对象
     */
    public void addDefNetworkActiveCallback(ConnectivityManager.OnNetworkActiveListener listener) {
        if (listener == null) {
            mDefNetworkCallbackActiveList.remove(null);
            return;
        }
        if (mDefNetworkCallbackActiveList.contains(listener)) {
            return;
        }
        mDefNetworkCallbackActiveList.add(listener);

        //laiyx 2022/1/5 14:53 触发初始事件
        if (isNetworkAvailable()) {
            listener.onNetworkActive();
        }
    }

    /**
     * 移除一个默认网络可用的事件回调
     *
     * @param listener 回调对象
     */
    public void removeDefNetworkActiveCallback(ConnectivityManager.OnNetworkActiveListener listener) {
        mDefNetworkCallbackActiveList.remove(listener);
    }

    /**
     * 检查网络是否可用，但网络可用不代表可以联网
     * @return the boolean
     */
    public boolean isNetworkAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mContext.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null) {
            return false;
        }
        return info.isAvailable();
    }

    private static String getCurNetworkName(@NetType int netType) {
        switch (netType) {
            case NetType.TYPE_NONE:
                return NetworkName.NET_NONE;
            case NetType.TYPE_WIFI:
                return NetworkName.NET_WIFI;
            case NetType.TYPE_2G:
                return NetworkName.NET_2G;
            case NetType.TYPE_3G:
                return NetworkName.NET_3G;
            case NetType.TYPE_4G:
                return NetworkName.NET_4G;
            case NetType.TYPE_5G:
                return NetworkName.NET_5G;
            default:
                return NetworkName.NET_UNKNOWN;
        }
    }

    /**
     * 获取当前的网络类型具有可读性的名称，如wifi，3g，4g等
     * @param context the context
     *
     * @return the cur network name
     */
    public static String getCurNetworkName(Context context) {
        if (context == null) {
            return NetworkName.NET_UNKNOWN;
        }

        @NetType int type = getCurNetworkType(context);
        return getCurNetworkName(type);
    }

    /**
     * 获取当前的网络类型具有可读性的名称，如wifi，3g，4g等
     * @param context the context
     *
     * @param network the network
     * @return the cur network name
     */
    public static String getCurNetworkName(Context context, Network network) {
        @NetType int type = getCurNetworkType(context, network);
        return getCurNetworkName(type);
    }

    /**
     * 获取当前网络类型
     * @param context the context
     *
     * @return the cur network type
     */
    public static @NetType int getCurNetworkType(Context context) {
        if (context == null) {
            return NetType.TYPE_UNKNOWN;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                LOG.E("缺少权限：" + Manifest.permission.ACCESS_WIFI_STATE);
                return NetType.TYPE_UNKNOWN;
            }
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return NetType.TYPE_NONE;
        }

        Network network = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            network = cm.getActiveNetwork();
        }
        return getNetWorkType(cm.getNetworkInfo(network));
    }

    /**
     * 获取当前网络类型
     * @param context the context
     *
     * @param network the network
     * @return the cur network type
     */
    public static @NetType int getCurNetworkType(Context context, Network network) {
        if (context == null) {
            return NetType.TYPE_UNKNOWN;
        }
        if (network == null) {
            return NetType.TYPE_NONE;
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return NetType.TYPE_NONE;
        }
        return getNetWorkType(cm.getNetworkInfo(network));
    }

    /**
     * 将移动网络制式转换成可读性的网络类型等效值{@link NetType}
     *
     * @param info 当前网络信息
     * @return 网络类型 net work type
     */
    public static @NetType int getNetWorkType(NetworkInfo info) {
        if (info == null || !info.isAvailable()) {
            return NetType.TYPE_NONE;
        }

        int type = info.getType();
        if (type == ConnectivityManager.TYPE_WIFI) {
            return NetType.TYPE_WIFI;
        } else {
            int subType = info.getSubtype();
            String subTypeName = info.getSubtypeName();
            int netType = NetType.TYPE_UNKNOWN;
            switch (subType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    netType = NetType.TYPE_2G;
                    break;
                case TelephonyManager.NETWORK_TYPE_EVDO_A: // 电信3g
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    netType = NetType.TYPE_3G;
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    netType = NetType.TYPE_4G;
                    break;
                default:
                    //sdk29及以上才会有这个常量
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        if (subType == TelephonyManager.NETWORK_TYPE_NR) {
                            netType = NetType.TYPE_5G;
                            break;
                        }
                    }

                    if (subTypeName.equalsIgnoreCase("TD-SCDMA")
                            || subTypeName.equalsIgnoreCase("WCDMA")
                            || subTypeName.equalsIgnoreCase("CDMA2000")) {
                        netType = NetType.TYPE_3G;
                    } else if (subTypeName.contains("LTE")) {
                        netType = NetType.TYPE_4G;
                    } else if (subTypeName.contains("NR")) {
                        netType = NetType.TYPE_5G;
                    } else {
                        LOG.I("Unknown network type: " + netType);
                        netType = NetType.TYPE_UNKNOWN;
                    }
                    break;
            }
            return netType;
        }
    }

    private static Context getContextMyself() {
        Class<?> activityThreadCls = null;
        try {
            activityThreadCls = Class.forName("android.app.ActivityThread");
        } catch (Exception ignore) { }

        Object activityThread = null;
        try {
            Field field = activityThreadCls.getDeclaredField("sCurrentActivityThread");
            field.setAccessible(true);
            activityThread = field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        if (activityThread == null) {
            return null;
        }

        Application result = null;
        try {
            Method method = activityThreadCls.getMethod("getApplication");
            result = (Application)method.invoke(activityThread);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static final class OnNetworkCallback extends NetworkCallback {

        private final List<INetworkCallback> list;

        /**
         * Instantiates a new On network callback.
         *
         * @param list the list
         */
        public OnNetworkCallback(List<INetworkCallback> list) {
            this.list = list;
        }

        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);

            LOG.D("Network available.");

            for (int i = 0; i < list.size(); ) {
                INetworkCallback callback = list.get(i);
                try {
                    callback.onAvailable(network);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!list.contains(callback)) {
                    //保持不变，因为已经被移除
                    continue;
                }
                i++;
            }
        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            super.onLosing(network, maxMsToLive);

            LOG.D("Network losing! tryToHoldMs=" + maxMsToLive);

            for (int i = 0; i < list.size(); ) {
                INetworkCallback callback = list.get(i);
                try {
                    callback.onLosing(network, maxMsToLive);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!list.contains(callback)) {
                    //保持不变，因为已经被移除
                    continue;
                }
                i++;
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);

            LOG.D("Network lost.");

            for (int i = 0; i < list.size(); ) {
                INetworkCallback callback = list.get(i);
                try {
                    callback.onLost(network);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!list.contains(callback)) {
                    //保持不变，因为已经被移除
                    continue;
                }
                i++;
            }
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();

            LOG.D("onUnavailable().");
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);

            //优先wifi网络
            boolean hasCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            int transport = NetType.TYPE_UNKNOWN;
            if (hasWifi) {
                transport = NetworkCapabilities.TRANSPORT_WIFI;
            } else if (hasCellular) {
                transport = NetworkCapabilities.TRANSPORT_CELLULAR;
            }
            LOG.D("onCapabilitiesChanged(). type=" + transport
                    + ",downstreamBandwidth=" + networkCapabilities.getLinkDownstreamBandwidthKbps() + "Kbps"
                    + ",upstreamBandwidth=" + networkCapabilities.getLinkUpstreamBandwidthKbps() + "Kbps");

            for (int i = 0; i < list.size();) {
                INetworkCallback callback = list.get(i);
                try {
                    callback.onCapabilitiesChanged(network, networkCapabilities);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!list.contains(callback)) {
                    //保持不变，因为已经被移除
                    continue;
                }
                i++;
            }
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties);
            LOG.D("onLinkPropertiesChanged().");

            for (int i = 0; i < list.size();) {
                INetworkCallback callback = list.get(i);
                if (callback != null) {
                    callback.onLinkPropertiesChanged(network, linkProperties);
                }
                if (!list.contains(callback)) {
                    //保持不变，因为已经被移除
                    continue;
                }
                i++;
            }
        }
    }

    private static final class LOG {
        /**
         * .
         *
         * @param msg the msg
         */
        public static void I(String msg) {
            Log.i(TAG, msg);
        }

        /**
         * D.
         *
         * @param msg the msg
         */
        public static void D(String msg) {
            Log.d(TAG, msg);
        }

        /**
         * E.
         *
         * @param msg the msg
         */
        public static void E(String msg) {
            Log.e(TAG, msg);
        }
    }
}
