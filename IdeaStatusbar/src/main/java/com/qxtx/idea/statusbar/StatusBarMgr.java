package com.qxtx.idea.statusbar;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.RequiresApi;
import android.support.annotation.UiThread;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ActionBarOverlayLayout;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.qxtx.idea.statusbar.tools.network.INetworkCallback;
import com.qxtx.idea.statusbar.tools.network.NetStateManager;
import com.qxtx.idea.statusbar.tools.network.NetworkCallbackAdapter;
import com.qxtx.idea.statusbar.tools.network.NetworkEventCallbackAdapter;
import com.qxtx.idea.statusbar.view.BaseStatusBar;
import com.qxtx.idea.statusbar.view.DefaultStatusBar;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author QXTX-WIN
 * <p><b>Create Date</b></p> 2021/7/18 20:30
 * <p><b>Description</b></p> 自定义app状态栏管理类V5。
 * <pre>
 *  以下均以“状态栏”来表示此app状态栏，系统状态栏则会特别注明
 *  · 状态栏一旦处于启用状态，则会自动根据每个activity启动时的实际情况，主动显示/隐藏。
 *  · 状态栏受到界面全屏状态和ActionBar的影响。当两者之一存在，状态栏将不会自动显示出来。
 *  使用步骤：
 *   <b>1. 创建实例</b>
 *       {@link #StatusBarMgr(Application, int)} 或
 *       {@link #StatusBarMgr(Application, com.qxtx.idea.statusbar.view.BaseStatusBar, int)}
 *       注意，每个应用应只创建一个实例
 *   <b>2. 启用/禁用状态栏 </b>
 *      {@link #setStatusBarEnable(boolean)} 此操作会从view树中添加/移除状态栏。
 *       通常，在{@link Application#onCreate()}中调用一次以启用状态栏，将会开始自动地根据界面来显示/隐藏状态栏。
 *       需要注意的是，如果在{@link Activity}中调用，则此时不一定能成功自动显示状态栏，因为状态栏只会响应在{@link Activity}的onResume()事件，
 *       即使此时手动调用{@link #show()}，也不会被显示，因为状态栏还未绑定到界面，下一次{@link Activity}的onResume()到来，状态栏即显示出来（如果可以显示）。
 *   · 更改状态栏可见性
 *      {@link #show()}/{@link #hide()};
 *       此操作并不会添加/移除状态栏，而是仅仅更改可见性。如果当前无状态栏，该方法无意义。
 *   · 跟随系统状态栏状态：对目标window的flag存在检测，如果有全屏标记{@link WindowManager.LayoutParams#FLAG_FULLSCREEN}，
 *       则{@link #setStatusBarEnable(boolean)}方法无效，状态栏将永远不会被显示。
 *   · 当需要显示状态栏时，将尽可能地隐藏系统状态栏（如果有）；当禁用状态栏时，将尝试复原系统状态栏可见性（如果需要）。
 *   · 支持以debug模式创建状态栏。此时，状态栏不会主动更新，而是需要手动实现更新。release版本中总是会禁止debug模式
 * </pre>
 *
 * 2021/7/22 23:49 目前对sim卡的状态检测适用于安卓O及以上，较低版本可能会得不到正确的结果。
 *
 * //laiyx 2022/2/22 14:34 5.1.7版本之后，将充满电状态仍视为正在充电
 */
public class StatusBarMgr implements IStatusBarMgr {
    private static final String TAG = StatusBarMgr.class.getSimpleName();

    /**
     * 用于调试模式。在release版本中，此变量总是为false，对其设置无效。
     */
    private final boolean isDebugMode;

    /**
     * 数据传输状态
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TRANSFER_UNKNOWN, TRANSFER_UPLOAD, TRANSFER_DOWNLOAD, TRANSFER_DUAL})
    public @interface TransferState { }

    /**
     * 未知状态
     */
    public static final int TRANSFER_UNKNOWN = 0;
    /**
     * 仅上行
     */
    public static final int TRANSFER_UPLOAD = 1;
    /**
     * 仅下行
     */
    public static final int TRANSFER_DOWNLOAD = 2;
    /**
     * 双工传输
     */
    public static final int TRANSFER_DUAL = 3;

    /** sim卡信号强度最大级数 */
    private static final int SIM_SIGNAL_LEVEL_MAX = 4;

    /** 是否已经启用状态栏。*/
    private boolean statusBarEnable = false;

    private WeakReference<ViewGroup> curDecorViewWeak = null;

    private WeakReference<Activity> foreActWeak = null;

    private Application.ActivityLifecycleCallbacks lifecycleCallbacks = null;

    /** sim变化的监听方案实现类 */
    private final SimEventMonitor simEventMonitor;

    /** 广播接收者对象集 */
    private final List<BroadcastReceiver> broadcastReceiverList = new ArrayList<>();

    /** Application上下文 */
    private final Application appContext;

    /** 状态栏控件 */
    private final BaseStatusBar statusBar;

    /** 状态栏高度，单位为px */
    private int statusBarHeight;

    /**
     * 使用默认的状态栏样式和布局，自定义状态栏高度
     *
     * @param context the context
     * @param height  状态栏高度，单位为px
     * @throws RuntimeException the runtime exception
     */
    public StatusBarMgr(Application context, int height) throws RuntimeException {
        this(context, new DefaultStatusBar(context), height, false);
    }

    /**
     * Instantiates a new Status bar mgr.
     *
     * @param context     the context
     * @param height      the height
     * @param isDebugMode the is debug mode
     * @throws RuntimeException the runtime exception
     */
    public StatusBarMgr(Application context, int height, boolean isDebugMode) throws RuntimeException {
        this(context, new DefaultStatusBar(context), height, isDebugMode);
    }

    /**
     * Instantiates a new Status bar mgr.
     *
     * @param context the context
     * @param view    the view
     * @param height  the height
     * @throws RuntimeException the runtime exception
     */
    public StatusBarMgr(Application context, BaseStatusBar view, int height) throws RuntimeException {
        this(context, view, height, false);
    }

    /**
     * Instantiates a new Status bar mgr.
     *
     * @param context   the context
     * @param view      自定义状态栏view
     * @param height    状态栏高度，单位为px
     * @param debugMode 是否为调试模式
     * @throws RuntimeException the runtime exception
     */
    public StatusBarMgr(Application context, BaseStatusBar view, int height, boolean debugMode) throws RuntimeException {
        //如果系统版本未达到要求，直接触发异常
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            throw new RuntimeException("Exception! " + getClass().getSimpleName() + "must be work in SDK24 or higher.");
        }
        if (context == null) {
            throw new IllegalArgumentException("Exception! Param context must be not null!");
        }
        if (view == null) {
            throw new IllegalArgumentException("Exception! Param view must be not null!");
        }

        this.isDebugMode = debugMode && BuildConfig.DEBUG;

        StatusBarLog.d("statusbar height=" + height + "px");

        statusBarHeight = height;
        if (height != WindowManager.LayoutParams.WRAP_CONTENT
                && height != WindowManager.LayoutParams.MATCH_PARENT
                && height < 0) {
            throw new IllegalArgumentException("Exception! Param height with a invalid value.");
        }

        appContext = context;

        statusBar = view;
        try {
            statusBar.setVisibility(View.GONE);
        } catch (Exception ignore) { }

        simEventMonitor = new SimEventMonitor();
    }

    /**
     * 检查当前是否处于飞行模式
     * @param context 上下文
     * @return 检查结果
     */
    public static boolean isAirplaneMode(Context context) {
        return Settings.Global.getInt(context.getApplicationContext().getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * 是否处于UI线程中
     * @return 是否在UI线程（主线程）中
     */
    public static boolean isUiThread() {
        return Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper();
    }

    private ViewGroup.LayoutParams generateLayoutParam() {
        ViewGroup.LayoutParams result = new ViewGroup.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, statusBarHeight);
        return result;
    }

    /**
     * 是否为主卡subId
     * 无足够的判断条件（如权限不够，系统版本太低等因素）时，总是返回false
     * 2021/7/29 10:48 SubscriptionManager.getDefaultDataSubscriptionId()得到正确结果的条件：结果subId对应的卡当前可用
     */
    private boolean isPrimarySim(int subId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        int simState = TelephonyManager.SIM_STATE_UNKNOWN;
        TelephonyManager tm = getTelephonyMgr();
        if (tm != null) {
            TelephonyManager subTm = tm.createForSubscriptionId(subId);
            if (subTm != null) {
                simState = subTm.getSimState();
            }
        }
        if (simState != TelephonyManager.SIM_STATE_READY) {
            return false;
        }

        int primaryDataSubId = simEventMonitor.primarySubId;
        if (primaryDataSubId == Integer.MIN_VALUE) {
            primaryDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        }
        return primaryDataSubId == subId;
    }

    /**
     * 是否为主卡卡槽
     * 无足够的判断条件（如权限不够，系统版本太低等因素）时，总是返回false
     * @see #isPrimarySim(int)
     */
    private boolean isPrimarySlot(int slotId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        SubscriptionManager ssm = getSubscriptionMgr();
        if (ssm != null) {
            SubscriptionInfo info = ssm.getActiveSubscriptionInfoForSimSlotIndex(slotId);
            if (info != null) {
                return isPrimarySim(info.getSubscriptionId());
            }
        }
        return false;
    }

    /** 记录activity的window flag，以控制系统状态栏的显示/隐藏 */
    private final HashMap<Integer, Integer> activityFlagMap = new HashMap<>();
    /**
     * 在activity的onResume()时机判断是否应该显示状态栏
     */
    private void listenAllActivityLifeCycle(boolean enable) {
        if (enable) {
            if (lifecycleCallbacks == null) {
                lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        foreActWeak = new WeakReference<>(activity);

                        //2021/10/27 13:56 被结束的activity调用onStop()/onDestroy()有时候会晚于新打开activity的onResume()，可能需要两个状态栏对象？
                        Window window = activity.getWindow();
                        WindowManager.LayoutParams lp = window.getAttributes();
                        int hashCode = activity.hashCode();
                        if (!activityFlagMap.containsKey(hashCode)) {
                            activityFlagMap.put(hashCode, lp.flags);
                        }
                        Integer windowFlags = activityFlagMap.get(hashCode);
                        if (windowFlags != null
                                && ((windowFlags & WindowManager.LayoutParams.FLAG_FULLSCREEN) == WindowManager.LayoutParams.FLAG_FULLSCREEN)) {
                            //全屏时不显示app状态栏
                            StatusBarLog.d("目标window已标记全屏，不显示状态栏");
                            hide();
                        } else {
                            //有ActionBar时不显示app状态栏
                            boolean isActionBarShowing = false;
                            if (activity instanceof AppCompatActivity) {
                                ActionBar actionBar = ((AppCompatActivity)activity).getSupportActionBar();
                                isActionBarShowing = actionBar != null && actionBar.isShowing();
                            } else {
                                android.app.ActionBar actionBar = activity.getActionBar();
                                isActionBarShowing = actionBar != null && actionBar.isShowing();
                            }
                            if (isActionBarShowing) {
                                StatusBarLog.d("目标activity正在展示ActionBar，不显示状态栏");
                                hide();
                                return;
                            }

                            StatusBarLog.d("目标window未标记全屏，显示状态栏");
                            //隐藏系统状态栏
                            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                            //如果有设置背景颜色，则以设置的背景颜色为准，否则跟随window的状态栏颜色
//                            int color = statusBar.getBgColor();
//                            if (color == Integer.MIN_VALUE) {
//                                color = window.getStatusBarColor();
//                            }
//                            StatusBarLog.e("设置状态栏颜色：0x" + Integer.toHexString(color));
//                            statusBar.getContentView().setBackgroundColor(color);

                            if (activity instanceof AppCompatActivity) {
                                ActionBar actionBar = ((AppCompatActivity)activity).getSupportActionBar();
                                if (actionBar != null) {
                                    actionBar.hide();
                                }
                            } else {
                                android.app.ActionBar actionBar = activity.getActionBar();
                                if (actionBar != null) {
                                    actionBar.hide();
                                }
                            }

                            StatusBarLog.d("界面onResume，绑定状态栏...");
                            attachStatusBar(windowFlags);
                            show();
                        }
                    }

                    @Override
                    public void onActivityStarted(Activity activity) { }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (activity.isFinishing()) {
                            StatusBarLog.d("界面正在被销毁，解绑状态栏...");
                            detachStatusBar(activity);
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        if (foreActWeak != null && foreActWeak.get() == activity) {
                            foreActWeak = null;
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

                    @Override
                    public void onActivityDestroyed(Activity activity) { }
                };
            } else {
                ((Application) appContext).unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            }
            ((Application) appContext).registerActivityLifecycleCallbacks(lifecycleCallbacks);
        } else {
            if (lifecycleCallbacks != null) {
                ((Application) appContext).unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            }
        }
    }

    /**
     * 绑定状态栏到当前界面。检查目标window的flags，做响应兼容处理
     * @param windowFlags 当前window的flags
     */
    private void attachStatusBar(int windowFlags) {
        if (statusBar == null) {
            return;
        }

        if (curDecorViewWeak != null) {
            ViewGroup lastDecorView = curDecorViewWeak.get();
            if (lastDecorView != null) {
                lastDecorView.removeView(statusBar);
            }
        }

        listenAllActivityLifeCycle(true);

        int fixContentTopMargin = statusBarHeight;

//        if ((windowFlags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) {
//            //contentView不偏移，同高，且状态栏应设置为透明
//            LYXLog.e("window配置FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS的flag，状态栏将使用透明背景，且contentView不偏移。flags="
//                    + "0x" + Integer.toHexString(windowFlags));
//            fixContentTopMargin = 0;
//            View v = statusBar.getContentView();
//            v.setBackground(null);
//        } else {
//
//        }

        ViewGroup parentView = getRootView();
        if (parentView == null) {
            return;
        }
        int insertIndex = 0;
        if ("DecorView".equals(parentView.getClass().getSimpleName())) {
            insertIndex = -1;
            View contentView = parentView.findViewById(android.R.id.content);
            ViewGroup.LayoutParams lp = contentView.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams)lp).topMargin = fixContentTopMargin;
            } else if (lp instanceof ActionBarOverlayLayout.LayoutParams) {
                ((ActionBarOverlayLayout.LayoutParams) lp).topMargin = fixContentTopMargin;
            }
            contentView.requestLayout();
        }

        statusBar.setVisibility(View.GONE);
        parentView.addView(statusBar, insertIndex, generateLayoutParam());
        curDecorViewWeak = new WeakReference<>(parentView);
    }

    private void detachStatusBar(Activity curActivity) {
        hide();

        if (curActivity != null) {
            int hashCode = curActivity.hashCode();
            if (activityFlagMap.containsKey(hashCode)) {
                //复原activity的原有flags
                int windowFlags = activityFlagMap.get(hashCode);
                Window window = curActivity.getWindow();
                window.getAttributes().flags = windowFlags;
                window.getDecorView().postInvalidate();
                activityFlagMap.remove(curActivity.hashCode());
            }
        }

        if (curDecorViewWeak == null) {
            return;
        }

        ViewGroup decorView = curDecorViewWeak.get();
        if (decorView == null) {
            return;
        }
        if (statusBar != null) {
            decorView.removeView(statusBar);
        }
    }

    @Override
    public boolean isStatusBarEnable() {
        return statusBarEnable;
    }

    /**
     * 启用/禁用状态栏，成功调用后将持久有效。启用时，绑定状态栏。如果此时无前台activity，则不做处理；禁用时，解绑状态栏。
     * 当在启用状态栏的状态下，调用{@link #show()}或{@link #hide()}时，状态栏的可见性将暂时地被改变。但此操作有效范围仅限于当前界面。
     * 若界面可见性改变（从后台回到前台），或者切换到另一个界面，则状态栏可见性将被重置。
     *
     * //2021/7/26 11:33 反复开关，有可能不会成功显示
     * @param statusBarEnable 是否启用状态栏
     */
    @Override
    public void setStatusBarEnable(boolean statusBarEnable) {
        if (this.statusBarEnable == statusBarEnable) {
            return;
        }
        this.statusBarEnable = statusBarEnable;

        listenAllActivityLifeCycle(statusBarEnable);

        if (statusBarEnable) {
            if (foreActWeak != null && foreActWeak.get() != null) {
                WindowManager.LayoutParams lp = foreActWeak.get().getWindow().getAttributes();
                if (lp != null) {
                    StatusBarLog.d("启用状态栏模块，立即绑定一次状态栏...");
                    attachStatusBar(lp.flags);
                }
            }
        } else {
            StatusBarLog.d("禁用状态栏模块，立即解绑状态栏（如果有）...");
            detachStatusBar(foreActWeak.get());
        }
    }

    /**
     * 设置仅对当前界面有效，同时不具有持久性。如果离开当前界面，或者当前界面可见性发生变化，则状态栏的可见性将会被重置
     */
    @Override
    public void show() {
        if (!statusBarEnable) {
            return;
        }

        if (statusBar.getVisibility() == View.VISIBLE) {
            return;
        }

        if (statusBar.getParent() == null) {
            StatusBarLog.e("状态栏控件未绑定到界面，无法显示");
            return;
        }

        StatusBarLog.d("显示状态栏");
        statusBar.setVisibility(View.VISIBLE);

        if (!isDebugMode) {
            initStatusBarIcon();
            listenAnyChange();
        }
    }

    /**
     * 设置仅对当前界面有效，同时不具有持久性。如果离开当前界面，或者当前界面可见性发生变化，则状态栏的可见性将会被重置
     */
    @Override
    public void hide() {
        if (statusBar.getVisibility() == View.GONE) {
            return;
        }

        statusBar.setVisibility(View.GONE);
        removeAnyChangeListener();
    }

    /**
     * 获取状态栏控件对象
     *
     * @return the status bar
     * @deprecated 不应该将此对象继续开放 ，以后可能会移除此方法
     */
    @Deprecated
    public BaseStatusBar getStatusBar() {
        return statusBar;
    }

    /** 获取根布局视图，以在顶部插入状态栏 */
    private ViewGroup getRootView() {
        if (foreActWeak == null || foreActWeak.get() == null) {
            return null;
        }

        ViewGroup result = null;
        Window window = foreActWeak.get().getWindow();
        if (window != null) {
            //由于需要适应window的flags，因此不添加到contentview的父布局中，而是添加到decorView容器中
            result = (ViewGroup) window.getDecorView();
//            ViewGroup decorView = (ViewGroup) window.getDecorView();
//            View contentView = decorView.findViewById(android.R.id.content);
//            result = (ViewGroup) contentView.getParent();
//            if (result == null) {
//                result = decorView;
//            }
        }
        return result;
    }

    private Runnable batteryChangeRunnable = null;
    private Runnable airplaneChangeRunnable = null;
    private Runnable headsetChangeRunnable = null;
    private Runnable networkChangeRunnable = null;
    private Runnable simChangeRunnable = null;

    /**
     * 一次完整的状态栏图标初始化操作
     * //2021/7/20 23:24 某些广播注册后会立即回调一次，则不需要多余的额外检测
     */
    protected void initStatusBarIcon() {
        updateAirplaneMode();

        Intent batteryIntent = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateBattery(batteryIntent);

        updateHeadset();

        updateSimInfo();

        updateNetworkType(NetStateManager.getCurNetworkType(appContext));
    }

    /**
     * 无论如何，检查一次全部sim的状态
     */
    @UiThread
    private void updateSimInfo() {
        if (!isUiThread()) {
            return;
        }
        //2021/7/29 21:01 需要获取ServiceState对象，以获得当前sim卡的dataRegister状态，最低要求安卓O
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            StatusBarLog.e("系统版本过低，无法获得sim卡完整信息");
            return;
        }
        if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            StatusBarLog.e("缺少读取手机状态权限！无法获得sim卡信息");
            return;
        }

        TelephonyManager tm = getTelephonyMgr();
        SubscriptionManager ssm = getSubscriptionMgr();
        if (tm == null || ssm == null) {
            //无法知道卡状态，因此不做任何反应
            return;
        }

        HashMap<Integer, SimInfo> simInfoMap = simEventMonitor.simInfoMap;
        int slotCount = tm.getPhoneCount();
        for (int i = 0; i < slotCount; i++) {
            SubscriptionInfo info = ssm.getActiveSubscriptionInfoForSimSlotIndex(i);
            if (info == null) {
                //认为是空卡槽，移除记录
                simInfoMap.remove(i);
                continue;
            }
            int subId = info.getSubscriptionId();
            int slotId = info.getSimSlotIndex();
            TelephonyManager subTm = tm.createForSubscriptionId(subId);
            if (subTm == null || !SimInfo.isSimReady(subTm.getSimState())) {
                simInfoMap.remove(slotId);
                continue;
            }

            int level = simEventMonitor.getSimSignalLevel(subTm);
            //消除一些厂商，使用[0, 4]之外的等级数值
            level = Math.min(level, SIM_SIGNAL_LEVEL_MAX);

            int simState = subTm.getSimState();
            ServiceState serviceState = subTm.getServiceState();
            SimInfo simInfo = simInfoMap.get(slotId);
            if (simInfo == null) {
                simInfo = new SimInfo(slotId, subId, simState, serviceState, level);
                simInfoMap.put(slotId, simInfo);
            } else {
                simInfo.subId = subId;
                simInfo.simState = simState;
                simInfo.serviceState = serviceState;
                simInfo.signalLevel = level;
            }

            StatusBarLog.d(TAG + ": " + (isPrimarySim(subId) ? "主卡" : "副卡") + ", 默认流量卡：" + SubscriptionManager.getDefaultDataSubscriptionId()
                    + ", 信号等级：" + level + ", 信息：" + simInfo.toString());
        }

        statusBar.onSimChanged(getSimMapClone(), simEventMonitor.primarySubId);
        StatusBarLog.i("更新sim信号， 同时检查网络类型");
    }

    private void postUpdateSimState(long delayMs) {
        removeStatusBarUpdate(simChangeRunnable);
        simChangeRunnable = this::updateSimInfo;
        updateStatusBar(simChangeRunnable, delayMs);
    }

    /** 检查一次耳机状态 */
    @UiThread
    private void updateHeadset() {
        if (!isUiThread()) {
            return;
        }
        statusBar.onHeadSetChanged(isHeadsetExist(appContext));
    }

    private void postUpdateHeadset(long delayMs) {
        removeStatusBarUpdate(headsetChangeRunnable);
        headsetChangeRunnable = this::updateHeadset;
        updateStatusBar(headsetChangeRunnable, delayMs);
    }

    /** 检查一次飞行模式状态 */
    @UiThread
    private void updateAirplaneMode() {
        if (!isUiThread()) {
            return;
        }

        boolean isAirplane = StatusBarMgr.isAirplaneMode(appContext);
        StatusBarLog.d(TAG + ": " + "是否处于飞行模式：" + isAirplane);
        statusBar.onAirplaneChanged(isAirplane);
    }

    private void postUpdateAirplaneMode(long delayMs) {
        removeStatusBarUpdate(airplaneChangeRunnable);
        airplaneChangeRunnable = this::updateAirplaneMode;
        updateStatusBar(airplaneChangeRunnable, delayMs);
    }

    /** 检查一次电池信息 */
    @UiThread
    private void updateBattery(Intent intent) {
        if (!isUiThread()) {
            return;
        }

        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
        float fraction = scale == 0 ? 0f : ((float) level / scale);

        int state = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int pluggedState = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean isCharging = (state == BatteryManager.BATTERY_STATUS_CHARGING
                || state == BatteryManager.BATTERY_STATUS_FULL)
                && pluggedState != 0;
        statusBar.onBatteryChanged(fraction, isCharging);
    }

    private void postUpdateBattery(Intent intent, long delayMs) {
        removeStatusBarUpdate(batteryChangeRunnable);
        batteryChangeRunnable = () -> updateBattery(intent);
        updateStatusBar(batteryChangeRunnable, delayMs);
    }

    /**
     * 检查网络状态
     * @param netType 当前网络类型
     */
    @UiThread
    private void updateNetworkType(@NetStateManager.NetType int netType) {
        if (!isUiThread()) {
            return;
        }

        float signalFraction = 1f;

        switch (netType) {
            case NetStateManager.NetType.TYPE_WIFI:
                WifiManager wm = (WifiManager) getSysMgr(Context.WIFI_SERVICE);
                if (wm != null) {
                    WifiInfo info = wm.getConnectionInfo();
                    int rssi = info.getRssi();
                    int maxLevel = statusBar.getWifiMaxLevel();
                    int level = WifiManager.calculateSignalLevel(rssi, maxLevel + 1);
                    signalFraction = (float) level / maxLevel;
                    StatusBarLog.i("wifi rssi=" + rssi + ", level(max:" + maxLevel + ")=" + level);
                }
                break;
            case NetStateManager.NetType.TYPE_2G:
            case NetStateManager.NetType.TYPE_3G:
            case NetStateManager.NetType.TYPE_4G:
            case NetStateManager.NetType.TYPE_5G:
                break;
            case NetStateManager.NetType.TYPE_NONE:
                signalFraction = 0f;
                break;
            case NetStateManager.NetType.TYPE_UNKNOWN:
            default:
                netType = NetStateManager.NetType.TYPE_UNKNOWN;
                signalFraction = 0f;
                break;
        }

        StatusBarLog.i(String.format("网络类型：type=%s, fraction=%s, transferStat=%s", netType, signalFraction, TRANSFER_UNKNOWN));
        statusBar.onNetworkTypeChanged(netType, signalFraction, TRANSFER_UNKNOWN);
    }

    private void postUpdateNetworkType(@NetStateManager.NetType int netType, int delayMs) {
        removeStatusBarUpdate(networkChangeRunnable);
        networkChangeRunnable = () -> updateNetworkType(netType);
        updateStatusBar(networkChangeRunnable, delayMs);
    }

    /**
     * 检查是否为耳机设备类型（更高的安卓版本增加了一些新类型）
     * @param type 音频设备类型，见{@link AudioDeviceInfo}的TYPE_* 常量
     */
    private static boolean isHeadsetDeviceType(int type) {
        boolean ret = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ret = type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ret = ret || type == AudioDeviceInfo.TYPE_USB_HEADSET;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ret = ret || type == AudioDeviceInfo.TYPE_HEARING_AID;
        }
        return ret;
    }

    /**
     * 检查当前是否仍存在耳机设备类型  @param context the context
     * @param context 上下文
     * @return the boolean
     */
    public static boolean isHeadsetExist(Context context) {
        if (context == null) {
            return false;
        }
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return false;
        }

        boolean headsetExist = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] checkArray = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (checkArray != null) {
                for (AudioDeviceInfo info : checkArray) {
                    if (info == null) {
                        continue;
                    }
                    if (isHeadsetDeviceType(info.getType())) {
                        headsetExist = true;
                        break;
                    }
                }
            }
        } else {
            headsetExist = am.isWiredHeadsetOn() || am.isBluetoothScoOn() || am.isBluetoothA2dpOn();
        }

        return headsetExist;
    }

    /**
     * 监听状态栏上所有图标对应的功能变化，以获得更新。如果重写此方法，则应重写{@link #removeAnyChangeListener()}
     */
    protected void listenAnyChange() {
        listenAirplaneModeChange();
        listenBatteryChange();
        listenHeadsetChange();
        listenNetworkChange();
        listenSimChange();
    }

    /**
     * 移除所有监听。对应{@link #listenAnyChange()}
     */
    protected void removeAnyChangeListener() {
        if (simEventMonitor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager.OnSubscriptionsChangedListener listener = simEventMonitor.getSubscriptionChangeListener();
                if (listener != null) {
                    SubscriptionManager ssm = getSubscriptionMgr();
                    if (ssm != null) {
                        try {
                            ssm.removeOnSubscriptionsChangedListener(listener);
                        } catch (Exception ignore) {}
                    }
                }
            }
        }

        if (headsetCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioManager am = (AudioManager) getSysMgr(Context.AUDIO_SERVICE);
                if (am != null) {
                    try {
                        am.unregisterAudioDeviceCallback(headsetCallback);
                    } catch (Exception ignore) { }
                }
            }
        }

        if (networkEventCallback != null) {
            NetStateManager.getInstance(appContext).removeNetworkCallback(networkEventCallback);
        }

        if (defNetActiveCallback != null) {
            NetStateManager.getInstance(appContext).removeDefNetworkActiveCallback(defNetActiveCallback);
        }

        for (int i = 0; i < broadcastReceiverList.size(); i++) {
            BroadcastReceiver receiver = broadcastReceiverList.get(i);
            if (receiver != null) {
                try {
                    appContext.unregisterReceiver(receiver);
                } catch (Exception ignore) { }
            }
        }
        broadcastReceiverList.clear();
    }

    /**
     * 监听sim卡的变化
     * 流量卡的变化：切换默认流量sim卡的行为（如设置中切换，拔出默认流量卡等），单纯的网络变化并不会导致默认流量卡的改变。
     * sim卡数量的变化：物理插拔sim卡、飞行模式开关、设置中启停sim卡、sim卡自身变得不可用等
     * sim卡信号的变化：实时变化
     */
    protected void listenSimChange() {
        //默认流量卡的变更，只要网络变化，就检查一下默认流量卡
        NetStateManager.getInstance(appContext).addNetworkCallback(simEventMonitor.getDataSimChangedCallback());

        //物理拔插卡的变更
        BroadcastReceiver simEventReceiver = simEventMonitor.getSimEventBroadcastReceiver();
        IntentFilter simFilter = new IntentFilter(SimEventMonitor.ACTION_SIM_STATE_CHANGED);
        appContext.registerReceiver(simEventReceiver, simFilter);
        broadcastReceiverList.add(simEventReceiver);

        //sim卡启停、信号强度的检测
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager ssm = getSubscriptionMgr();
            if (ssm == null) {
                return;
            }
            SubscriptionManager.OnSubscriptionsChangedListener listener = simEventMonitor.getSubscriptionChangeListener();
            ssm.addOnSubscriptionsChangedListener(listener);
        }
    }

    /**
     * 监听电池变化
     */
    protected void listenBatteryChange() {
        BatteryEventReceiver batteryEventReceiver = new BatteryEventReceiver();
        IntentFilter batteryEventFilter = new IntentFilter(BatteryEventReceiver.ACTION);
        appContext.registerReceiver(batteryEventReceiver, batteryEventFilter);
        broadcastReceiverList.add(batteryEventReceiver);
    }

    /**
     * 监听飞行模式变化
     */
    protected void listenAirplaneModeChange() {
        AirplaneEventReceiver airplaneEventReceiver = new AirplaneEventReceiver();
        IntentFilter airplaneEventFilter = new IntentFilter(AirplaneEventReceiver.ACTION);
        appContext.registerReceiver(airplaneEventReceiver, airplaneEventFilter);
        broadcastReceiverList.add(airplaneEventReceiver);
    }

    private AudioDeviceCallback headsetCallback = null;

    /**
     * 监听耳机状态变化
     */
    protected void listenHeadsetChange() {
        AudioManager am = (AudioManager) getSysMgr(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (headsetCallback == null) {
                headsetCallback = new AudioDeviceCallback() {
                    @Override
                    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                        for (AudioDeviceInfo info : addedDevices) {
                            if (isHeadsetDeviceType(info.getType())) {
                                StatusBarLog.d(TAG + ": " + "音频类型设备被添加：" + info.getType());
                                updateStatusBar(() -> statusBar.onHeadSetChanged(true));
                                break;
                            }
                        }
                    }

                    @Override
                    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                        boolean isHeadsetRemove = false;
                        for (AudioDeviceInfo info : removedDevices) {
                            if (isHeadsetDeviceType(info.getType())) {
                                isHeadsetRemove = true;
                                StatusBarLog.d(TAG + ": " + "音频类型设备被移除：" + info.getType());
                                break;
                            }
                        }
                        if (isHeadsetRemove) {
                            boolean headsetExist = false;
                            AudioDeviceInfo[] checkList = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                            if (checkList != null) {
                                for (AudioDeviceInfo item : checkList) {
                                    if (item != null && isHeadsetDeviceType(item.getType())) {
                                        headsetExist = true;
                                        break;
                                    }
                                }
                            }
                            final boolean enable = headsetExist;
                            StatusBarLog.d(TAG + ": " + "耳机图标更新：存在耳机？" + enable);
                            updateStatusBar(() -> statusBar.onHeadSetChanged(enable));
                        }
                    }
                };
            }
            am.registerAudioDeviceCallback(headsetCallback, null);
        } else {
            //并不好用，可能不能同时判断蓝牙+有线的情况？？
            IntentFilter headsetFilter = new IntentFilter(HeadsetEventReceiver.ACTION_HEADSET_PLUG);
            headsetFilter.addAction(HeadsetEventReceiver.ACTION_BLUETOOTH_HEADSET);
            headsetFilter.addAction(HeadsetEventReceiver.ACTION_AUDIO_BECOMING_NOISY);
            HeadsetEventReceiver headsetEventReceiver = new HeadsetEventReceiver();
            appContext.registerReceiver(headsetEventReceiver, headsetFilter);
            broadcastReceiverList.add(headsetEventReceiver);
        }
    }

    private NetworkEventCallback networkEventCallback = null;

    private ConnectivityManager.OnNetworkActiveListener defNetActiveCallback = null;

    /**
     * 监听网络变化
     */
    protected void listenNetworkChange() {
        if (networkEventCallback == null) {
            networkEventCallback = new NetworkEventCallback();
        }
        NetStateManager.getInstance(appContext).addNetworkCallback(networkEventCallback);

        //laiyx 2021/12/17 16:27 增强对移动网络（系统默认网络）可用性的判断
        if (defNetActiveCallback == null) {
            defNetActiveCallback = () -> {
                StatusBarLog.d("发现网络可用");
                int netType = NetStateManager.getCurNetworkType(appContext);
                if (netType != NetStateManager.NetType.TYPE_NONE) {
                    postUpdateNetworkType(netType, 0);
                }
            };
        }
        NetStateManager.getInstance(appContext).addDefNetworkActiveCallback(defNetActiveCallback);
    }

    /** 移除状态栏的更新任务（可能目标任务已经过时，需要丢弃它） */
    private void removeStatusBarUpdate(Runnable runnable) {
        if (runnable != null) {
            statusBar.removeCallbacks(runnable);
        }
    }

    /**
     * Update status bar.
     *
     * @param runnable the runnable
     */
    protected void updateStatusBar(Runnable runnable) {
        updateStatusBar(runnable, 0);
    }

    /**
     * 更新状态栏(支持延时)
     *
     * @param runnable the runnable
     * @param delayMs  延迟执行任务的时长，单位为毫秒
     * @see #updateStatusBar(Runnable) #updateStatusBar(Runnable)
     */
    protected void updateStatusBar(Runnable runnable, long delayMs) {
        statusBar.postDelayed(runnable, Math.max(0, delayMs));
    }

    /** 克隆一份sim信息，用于提供到外部，且防止外部篡改数据 */
    private HashMap<Integer, SimInfo> getSimMapClone() {
        HashMap<Integer, SimInfo> result = new HashMap<>();
        for (Integer key : simEventMonitor.simInfoMap.keySet()) {
            SimInfo value = simEventMonitor.simInfoMap.get(key);
            if (key != null && value != null) {
                result.put(key, value.deepCopy());
            }
        }
        return result;
    }

    private TelephonyManager getTelephonyMgr() {
        TelephonyManager result = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            result = (TelephonyManager) getSysMgr(Context.TELEPHONY_SERVICE);
        }
        return result;
    }

    private Object getSysMgr(String serviceName) {
        return appContext.getSystemService(serviceName);
    }

    private SubscriptionManager getSubscriptionMgr() {
        SubscriptionManager result = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            result = (SubscriptionManager) getSysMgr(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        }
        return result;
    }

    private final class NetworkEventCallback extends NetworkCallbackAdapter {

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            StatusBarLog.i(TAG + ": " + "网络能力改变：" + network.toString() + ", cap=" + networkCapabilities.toString()
                    + ", cur network=" + NetStateManager.getCurNetworkName(appContext, network));

            int netType = NetStateManager.NetType.TYPE_UNKNOWN;
            boolean hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean hasCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            if (hasWifi) {
                netType = NetStateManager.NetType.TYPE_WIFI;
            } else {
                if (hasCellular) {
                    netType = NetStateManager.getCurNetworkType(appContext, network);
                }
            }
            postUpdateNetworkType(netType, 0);
        }

        @Override
        public void onLost(Network network) {
            StatusBarLog.i(TAG + ": " + "某个网络丢失");
            ConnectivityManager cm = (ConnectivityManager) getSysMgr(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                return;
            }

            boolean isNetAvailable = NetStateManager.getInstance(appContext).isNetworkAvailable();
            if (!isNetAvailable) {
                StatusBarLog.i("已经没有网络");
                postUpdateNetworkType(NetStateManager.NetType.TYPE_NONE, 0);

                //laiyx 2021/12/17 16:23 弥补性的检查
                updateStatusBar(() -> {
                    int netType = NetStateManager.getCurNetworkType(appContext);
                    if (netType != NetStateManager.NetType.TYPE_NONE) {
                        updateNetworkType(netType);
                    }
                }, 500);
            }
        }

        @Override
        public void onUnavailable() {
            StatusBarLog.i(TAG + ": " + "网络变得不可用");
            postUpdateNetworkType(NetStateManager.NetType.TYPE_NONE, 0);
        }
    }

    private final class AirplaneEventReceiver extends BroadcastReceiver {

        private static final String ACTION = Intent.ACTION_AIRPLANE_MODE_CHANGED;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (!ACTION.equals(intent.getAction())) {
                return;
            }

            StatusBarLog.d(TAG + ": " + "飞行模式改变");

            postUpdateAirplaneMode(0);
        }
    }

    private final class BatteryEventReceiver extends BroadcastReceiver {

        private static final String ACTION = Intent.ACTION_BATTERY_CHANGED;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (!ACTION.equals(action)) {
                return;
            }

            postUpdateBattery(intent, 0);
        }
    }

    /**
     * 监听设备耳机的变化。可能同时连接着有线耳机和蓝牙耳机
     * //2021/7/22 22:22 广播的方式可能对同时存在有线+蓝牙时，不好判断？
     *      直接使用{@link AudioManager#getDevices(int)}方法的判断有延迟，广播收到的时候，此方法并不能立即得到最新的结果，还是旧的状态
     *      广播能够准确判断，则仅使用广播，并处理同时存在多耳机?
     */
    private final class HeadsetEventReceiver extends BroadcastReceiver {

        private static final String ACTION_HEADSET_PLUG = Intent.ACTION_HEADSET_PLUG;
        private static final String ACTION_BLUETOOTH_HEADSET = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED;
        private static final String ACTION_AUDIO_BECOMING_NOISY = AudioManager.ACTION_AUDIO_BECOMING_NOISY;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            if (!ACTION_HEADSET_PLUG.equals(action)
                    && !ACTION_BLUETOOTH_HEADSET.equals(action)
                    && !ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                return;
            }

            if (BuildConfig.DEBUG) {
                Bundle bundle = intent.getExtras();
                String[] keyArray = bundle == null ? null : bundle.keySet().toArray(new String[0]);
                StatusBarLog.d(TAG + ": " + "耳机广播事件参数：bundle=" + bundle
                        + "\nkeyArray=" + Arrays.toString(keyArray));
            }

            boolean isEnable = false;
            switch (action) {
                case ACTION_HEADSET_PLUG:
                    int wireState = intent.getIntExtra("state", Integer.MIN_VALUE);
                    String name = intent.getStringExtra("name");
                    int microphone = intent.getIntExtra("microphone", Integer.MIN_VALUE);
                    isEnable = wireState == 1;

                    StatusBarLog.d(TAG + ": " + "有线耳机广播事件：state=" + wireState
                            + "\nname=" + name
                            + "\nmicrophone=" + microphone);
                    break;
                case ACTION_BLUETOOTH_HEADSET:
                    int btState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                    int previousState = intent.getIntExtra(BluetoothHeadset.EXTRA_PREVIOUS_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                    List<String> vendorArgs = intent.getStringArrayListExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
                    String cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
                    int cmdType = intent.getIntExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, Integer.MIN_VALUE);

                    isEnable = btState == BluetoothHeadset.STATE_CONNECTED;

                    StatusBarLog.d(TAG + ": " + "蓝牙耳机广播事件：state=" + btState
                            + ", previousState=" + previousState
                            + ", vendorArgs=" + (vendorArgs == null ? null : Arrays.toString(vendorArgs.toArray(new String[0])))
                            + ", cmdTye=" + cmdType
                            + ", cmd=" + cmd);
                    break;
                case ACTION_AUDIO_BECOMING_NOISY:
                    //2021/7/22 22:12 此时应该是完全无耳机了，应该再来一次移除耳机图标？
                    StatusBarLog.d(TAG + ": " + "声音可能产生干扰，可能从耳机切换到扬声器。");
                    break;
                default:
                    break;
            }

            boolean isHeadsetExist = isEnable;
            updateStatusBar(() -> statusBar.onHeadSetChanged(isHeadsetExist));
        }
    }

    /**
     * sim的变化监管类，检测方案的混合实现
     *  感知方案：
     *  1. sim卡的物理拔插： 监听广播action：
     *   {@link SimEventMonitor#ACTION_SIM_STATE_CHANGED}（状态改变，只有“变动的sim卡”才会回调）
     *   {@link SimEventMonitor#ACTION_DEF_SUBSCRIPTION_CHANGED}（因拔插造成的默认订阅强制改变，必要时可使用）
     *  2. 因手动禁用、飞行模式造成的sim卡变动：{@link SubscriptionManager#addOnSubscriptionsChangedListener}可以感知到状态变化，得到sim卡的可用状态改变。
     *        注意：通过添加{@link TelephonyManager#listen(PhoneStateListener, int)}监听，监听ServiceState来获取voice或data的注册状态，这不等同于sim卡本身的可用状态
     *  3. sim卡信号变动：添加对应subId的{@link TelephonyManager#listen(PhoneStateListener, int)}监听，得到sim卡的信号强度改变
     *  4. 默认流量卡的切换：监听网络状态变化，检查流量卡的slotId和subId。
     *   如果wifi过程中发生流量卡切换，不做处理；
     *   如果wiFi过程中，没有必要更新流量卡的变更。几种变化：
     *       双卡->单卡：此时可能默认流量卡未改变，通过当前剩余的卡是否仍为默认流量卡subId判断
     *       单卡->无卡：不做处理，保留当前记录
     *       无卡->单卡：强制更新默认流量卡为当前卡subId
     *       2021/7/29 12:08 无卡->双卡：如果有相同subId，仍然使用最后一次的记录；如果已经没有相同subId，使用当前默认订阅的subId？？？
     *
     *  需要感知的场景：
     *  物理拔插sim卡（广播android.intent.action.SIM_STATE_CHANGED完成，可以感知插卡和拔卡）
     *  手动启停sim卡（OnSubscriptionsChangedListener）
     *  手动切换数据卡、电话卡、短信卡（监听网络变化、？、监听DEFAULT_SMS_SUB_CHANGED广播action）
     *  sim卡信号强度变化（PhoneStateListener）
     */
    private final class SimEventMonitor {

        /** 当前主卡的subId */
        private int primarySubId;

        /** 目前已知的卡信息集。键为slotId，值为对应slotId的sim卡信息 */
        private final HashMap<Integer, SimInfo> simInfoMap = new HashMap<>();

        /** sim状态改变，可感知拔插事件 */
        private static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";

        /**
         * 默认订阅变更（The default subscription has changed）时，系统将发布此类广播。通常指"默认卡槽"。
         * 一般来说，只有发生物理拔插sim卡，才有触发此广播事件的可能。（中途拔插卡、注册广播首次回调）
         * 单卡时，它所在的卡槽被认为是默认卡槽；
         * 无卡时，卡槽0被认为时默认卡槽；
         * 双卡时，卡槽0被认为是默认卡槽。
         * 携带参数：
         * {@link SubscriptionManager#EXTRA_SUBSCRIPTION_INDEX}:int
         * "slot":int
         * "phone":String
         * "subscription":int
         */
        private static final String ACTION_DEF_SUBSCRIPTION_CHANGED = SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED;
//        /** 只要有数据、电话、短信任一发生改变，系统将发布此类广播（好像没作用） */
//        private static final String ACTION_SUB_DEF_CHANGED = "android.intent.action.SUB_DEFAULT_CHANGED";
//        /** sms短信卡发生变动时，系统将发布此类广播 */
//        private static final String ACTION_DEF_SMS_SUB_CHANGED = SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED;
//        /** sim卡插入状态变更时，系统将发布此类广播，仅携带simState参数（{@link #ACTION_STATE_CHANGED}也有） */
//        private static final String ACTION_SIM_INSERT_UPDATED = "android.intent.action.ACTION_SUBINFO_RECORD_UPDATED";

        private SimEventReceiver receiver = null;

        private SubscriptionManager.OnSubscriptionsChangedListener subscriptionChangeListener = null;

        private PhoneStateChangeListener phoneStateListener = null;

        /**
         * 通过网络变化，检测流量卡的变更。
         * 如果当前未在使用移动数据网络时，流量卡发生变更，则无法监听到。但此时也不需要关心其变化。
         */
        private INetworkCallback dataSimEventCallback = null;

        /**
         * 构造方法中获取初始的可用sim卡数量和流量卡subId
         */
        public SimEventMonitor() {
            setPrimarySubId(Integer.MIN_VALUE);
            if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            TelephonyManager tm = getTelephonyMgr();
            SubscriptionManager ssm = getSubscriptionMgr();
            if (tm == null || ssm == null) {
                return;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return;
            }

            setPrimarySubId(SubscriptionManager.getDefaultDataSubscriptionId());

            List<SubscriptionInfo> list = ssm.getActiveSubscriptionInfoList();
            if (list != null) {
                for (SubscriptionInfo info : list) {
                    if (info == null) {
                        continue;
                    }
                    int subId = info.getSubscriptionId();
                    TelephonyManager subTm = tm.createForSubscriptionId(subId);
                    if (subTm == null) {
                        continue;
                    }

                    int simState = subTm.getSimState();
                    ServiceState serviceState = subTm.getServiceState();
                    boolean isSimValid = SimInfo.isSimReady(simState);
                    SimInfo simInfo = simInfoMap.get(info.getSimSlotIndex());
                    if (isSimValid) {
                        if (simInfo == null) {
                            simInfo = new SimInfo(info.getSimSlotIndex());
                            simInfoMap.put(simInfo.slotId, simInfo);
                        }
                        simInfo.subId = subId;
                        simInfo.simState = simState;
                        simInfo.serviceState = serviceState;
                        simInfo.signalLevel = getSimSignalLevel(subTm);

                        //在初始化时，设置一个监听，防止首次sim状态变化时，可能错过当次回调事件
                        subTm.listen(getPhoneStateListener(simInfo.slotId, simInfo.subId), PhoneStateListener.LISTEN_SERVICE_STATE |PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
                    }
                }
            }

            StatusBarLog.d("可用sim卡数量：" + simInfoMap.size() + ", 信息集：" + simInfoMap.toString());
        }

        /** 是否存在可用的sim */
        private boolean isAnySimAvailable() {
            if (simInfoMap.isEmpty()) {
                return false;
            }

            boolean ret = false;
            for (SimInfo info : simInfoMap.values()) {
                ret |= SimInfo.isSimDataReg(info.serviceState);
            }
            return ret;
        }

        /**
         * 获取sim卡信号等级
         * @param tm 目标sim卡信号等级的{@link TelephonyManager}对象
         */
        private int getSimSignalLevel(TelephonyManager tm) {
            int ret = SIM_SIGNAL_LEVEL_MAX;
            if (tm == null) {
                return ret;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SignalStrength strength = tm.getSignalStrength();
                if (strength != null) {
                    ret = strength.getLevel();
                }
            } else {
                if (appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return ret;
                }
                List<CellInfo> cellInfoList = tm.getAllCellInfo();
                if (cellInfoList != null) {
                    CellSignalStrength strength = null;
                    for (CellInfo cellInfo : cellInfoList) {
                        if (cellInfo instanceof CellInfoGsm) {
                            strength = ((CellInfoGsm) cellInfo).getCellSignalStrength();
                        } else if (cellInfo instanceof CellInfoCdma) {
                            strength = ((CellInfoCdma) cellInfo).getCellSignalStrength();
                            break;
                        } else if (cellInfo instanceof CellInfoWcdma) {
                            strength = ((CellInfoWcdma) cellInfo).getCellSignalStrength();
                            break;
                        } else if (cellInfo instanceof CellInfoLte) {
                            strength = ((CellInfoLte) cellInfo).getCellSignalStrength();
                            break;
                        }
                    }

                    if (strength != null) {
                        ret = strength.getLevel();
                    }
                }
            }
            return ret;
        }

        /**
         * 检查sim是否就绪。这只是表示sim可以正常工作，但不意味着sim一定处于启用状态。还需要辅助sim具体的数据状态、语音状态等进行判断。
         * 适用于判断sim广播事件中拿到的”ss“参数字段（即simState的等效字符串形式）
         * @param simState 状态字符串。取值对应{@link TelephonyManager#SIM_STATE_NOT_READY}及其它同类SIM_STATE_XXX常量字段
         * @return
         *
         * @see SimInfo#isSimReady(int)
         */
        private boolean isSimReady(String simState) {
            return "READY".equals(simState) || "LOADED".equals(simState);
        }

        /** 获取默认流量卡的变更回调 */
        private INetworkCallback getDataSimChangedCallback() {
            if (dataSimEventCallback == null) {
                dataSimEventCallback = new NetworkEventCallbackAdapter() {
                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                            return;
                        }
                        //2021/8/2 22:16 可以排除一些没必要的场景
                        int newPrimarySubId = SubscriptionManager.getDefaultDataSubscriptionId();
                        if (newPrimarySubId != primarySubId) {
                            StatusBarLog.d(TAG + ": " + "默认流量卡变更。当前流量卡subId=" + newPrimarySubId);
                            setPrimarySubId(newPrimarySubId);
                        }
                    }

                    @Override
                    public void onLost(Network network) {
                    }

                    @Override
                    public void onUnavailable() {
                    }
                };
            }
            return dataSimEventCallback;
        }

        /** 获取sim拔插变化的广播接收者 */
        private BroadcastReceiver getSimEventBroadcastReceiver() {
            if (receiver == null) {
                receiver = new SimEventReceiver();
            }
            return receiver;
        }

        /** 获取sim订阅变化监听器 */
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
        private SubscriptionManager.OnSubscriptionsChangedListener getSubscriptionChangeListener() {
            if (subscriptionChangeListener == null) {
                subscriptionChangeListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        StatusBarLog.d(TAG + ": " + "onSubscriptionsChanged()事件回调");
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                            return;
                        }

                        TelephonyManager tm = getTelephonyMgr();
                        SubscriptionManager ssm = getSubscriptionMgr();
                        if (ssm == null || tm == null) {
                            return;
                        }
                        if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                            StatusBarLog.e("异常！无法获取必要的权限：" + Manifest.permission.READ_PHONE_STATE);
                            return;
                        }

                        List<SubscriptionInfo> list = ssm.getActiveSubscriptionInfoList();
                        if (list == null || list.isEmpty()) {
                            simEventMonitor.simInfoMap.clear();
                            StatusBarLog.d(TAG + ": " + "没有可用的sim卡，更新...");
                            updateStatusBar(() -> statusBar.onSimChanged(getSimMapClone(), primarySubId));
                            return;
                        }

                        for (SubscriptionInfo info : list) {
                            int subId = Integer.MIN_VALUE;
                            TelephonyManager subTm = null;
                            if (info != null) {
                                subId = info.getSubscriptionId();
                                subTm = tm.createForSubscriptionId(subId);
                            }
                            if (subTm == null) {
                                continue;
                            }

                            int simState = subTm.getSimState();
                            if (SimInfo.isSimReady(simState)) {
                                subTm.listen(new PhoneStateChangeListener(info.getSimSlotIndex(), subId),
                                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_SERVICE_STATE);
                            }
                        }
                    }
                };
            }
            return subscriptionChangeListener;
        }

        private PhoneStateChangeListener getPhoneStateListener(int slotId, int subId) {
            if (phoneStateListener == null) {
                phoneStateListener = new PhoneStateChangeListener(slotId, subId);
            }
            return phoneStateListener;
        }

        private int getPrimarySubId() {
            return primarySubId;
        }

        /** 更新数据卡subId */
        private void setPrimarySubId(int subId) {
            this.primarySubId = subId;
        }

        /**
         * sim状态更新的监听器
         * @see #listenSimChange()
         */
        private final class PhoneStateChangeListener extends PhoneStateListener {

            private final int slotId;
            private int subId;

            private PhoneStateChangeListener(int slotId, int subId) {
                this.slotId = slotId;
                this.subId = subId;
            }

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    return;
                }
                boolean isStateChange = false;

                StatusBarLog.d("SIM " + slotId + "状态变更\n[" + serviceState.toString() + "]");

                //由于这里只改变卡的业务服务状态，但不改变sim卡的数量，因此不会新增/减少sim卡记录

                SimInfo simInfo = simInfoMap.get(slotId);
                if (simInfo == null) {
                    return;
                }

                if (SimInfo.isSimDataReg(serviceState)) {
                    isStateChange = !SimInfo.isSimReady(simInfo.simState) || !SimInfo.isSimDataReg(simInfo.serviceState);

                    SubscriptionManager ssm = getSubscriptionMgr();
                    if (ssm != null) {
                        if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                            StatusBarLog.e("异常！无法获取相关权限：" + Manifest.permission.READ_PHONE_STATE);
                            return;
                        }
                        SubscriptionInfo info = ssm.getActiveSubscriptionInfoForSimSlotIndex(slotId);
                        if (info != null) {
                            subId = info.getSubscriptionId();
                        }
                        simInfo.subId = subId;
                    }

                    //既然服务可用，则肯定是sim卡已就绪
                    simInfo.simState = TelephonyManager.SIM_STATE_READY;
                    simInfo.serviceState = serviceState;
                } else {
                    isStateChange = SimInfo.isSimReady(simInfo.simState) && SimInfo.isSimDataReg(simInfo.serviceState);
                }

                if (isStateChange) {
                    //卡的服务状态改变，有可能会引发默认流量卡的强制改变，就是从其它数量变成单sim卡的时候
                    if (SimInfo.isSimDataReg(serviceState) && primarySubId == subId) {
                        //默认流量卡被关闭了，如果还有其他卡，则立即变更默认流量卡
                        for (Integer slotKey : simInfoMap.keySet()) {
                            if (slotKey == slotId) {
                                continue;
                            }
                            SimInfo info = simInfoMap.get(slotKey);
                            if (info != null
                                    && SimInfo.isSimReady(info.simState)
                                    && SimInfo.isSimDataReg(info.getServiceState())) {
                                StatusBarLog.d("因关闭当前流量卡，引起的默认流量卡变更");
                                setPrimarySubId(info.subId);
                            }
                        }
                    }

                    StatusBarLog.d("sim" + slotId + "数据服务状态变更");
                    updateStatusBar(() -> {
                        statusBar.onSimChanged(getSimMapClone(), primarySubId);

                        //laiyx 2021/12/17 10:38 为了逻辑效果一致，更新一下信号类型。这也可以做到一定程度的容错
                        updateNetworkType(NetStateManager.getCurNetworkType(appContext));
                    });
                }
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    return;
                }

                StatusBarLog.d("SIM " + slotId + "信号变更\n[" + signalStrength.toString() + "]");

//                if (StatusBarLog.isDebugEnable()) {
//                    String s = signalStrength.toString();
//                    //StatusBarLog.d("平台：" + signalStrength.getClass().getName());
//                    //MTK：mediatek.telephony.MtkSignalStrength.java
//                    //  MtkSignalStrength: 0 66 -120 -160 -120 -1 -1 99 -74 -4 300 2147483647 0 2147483647 gsm|lte 0]
//                    //高通：android.telephony.SignalStrength
//                    //  SignalStrength:{mCdma=CellSignalStrengthCdma: cdmaDbm=2147483647 cdmaEcio=2147483647 evdoDbm=2147483647 evdoEcio=2147483647 evdoSnr=2147483647 level=0 oplevel=0,mGsm=CellSignalStrengthGsm: rssi=2147483647 ber=2147483647 mTa=2147483647 mLevel=0,mWcdma=CellSignalStrengthWcdma: ss=2147483647 ber=2147483647 rscp=2147483647 ecno=2147483647 level=0 oplevel=0,mTdscdma=CellSignalStrengthTdscdma: rssi=2147483647 ber=2147483647 rscp=2147483647 level=0,mLte=CellSignalStrengthLte: rssi=-53 rsrp=-84 rsrq=-12 rssnr=104 cqi=2147483647 ta=2147483647 level=4 oplevel=4,mNr=CellSignalStrengthNr:{ csiRsrp = 2147483647 csiRsrq = 2147483647 csiSinr = 2147483647 ssRsrp = 2147483647 ssRsrq = 2147483647 ssSinr = 2147483647 level = 0 },primary=CellSignalStrengthLte,voice level =4,data level =4,isGsm =true}
//                    String platformRecognize = signalStrength.getClass().getName();
//                    if (platformRecognize.equals("mediatek.telephony.MtkSignalStrength")) {
//                        String fixS = s.substring(
//                                s.indexOf(": ") + 2,
//                                s.indexOf("2147483647 0 2147483647"));
//                        statusBar.post(() -> {
//                            SimInfo info = simInfoMap.get(slotId);
//                            Toast.makeText(statusBar.getContext(),
//                                    "SIM" + slotId
//                                            + ", 信号=" + (info == null ? -1 : signalStrength.getLevel())
//                                            + "\n" + fixS, Toast.LENGTH_SHORT).show();
//                        });
//                    } else if (platformRecognize.equals("android.telephony.SignalStrength")) {
//                        int lteIndex = s.indexOf("CellSignalStrengthLte:");
//                        int start = s.indexOf("rssi=", lteIndex);
//                        int end = s.indexOf("rsrq=", start);
//                        String lteSignal = s.substring(start, end);
//                        String primaryLevel = s.substring(s.indexOf(",primary=") + 1);
//                        statusBar.post(() -> {
//                            SimInfo info = simInfoMap.get(slotId);
//                            Toast.makeText(statusBar.getContext(),
//                                    "SIM" + slotId
//                                            + ", 信号=" + (info == null ? -1 : signalStrength.getLevel())
//                                            + "\n" + primaryLevel + "\n" + lteSignal, Toast.LENGTH_SHORT).show();
//                        });
//                    }
//                }

                SimInfo simInfo = simInfoMap.get(slotId);
                boolean simEnable = false;
                if (simInfo != null) {
                    simEnable = SimInfo.isSimReady(simInfo.simState) && SimInfo.isSimDataReg(simInfo.serviceState);
                }
                if (!simEnable) {
                    return ;
                }

                int level = Math.min(signalStrength.getLevel(), SIM_SIGNAL_LEVEL_MAX);
//                StatusBarLog.d("SIM " + slotId + "信号强度回调, 新level=" + level + ", 旧level=" + simInfo.signalLevel);
                if (simInfo.signalLevel != level) {
                    simInfo.signalLevel = level;
                    updateStatusBar(() -> {
                        statusBar.onSimChanged(getSimMapClone(), primarySubId);

                        //laiyx 2021/12/22 10:36 为了逻辑效果一致，更新一下网络类型。这也可以做到一定程度的容错
                        updateNetworkType(NetStateManager.getCurNetworkType(appContext));
                    });

                    StatusBarLog.d("SIM 信号强度更新，slotId=" + slotId + ", level=" + level);
                }
            }
        }

        /**
         * 只做sim的拔插检测
         */
        private final class SimEventReceiver extends BroadcastReceiver {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (!ACTION_SIM_STATE_CHANGED.equals(action)) {
                    return;
                }
                TelephonyManager tm = getTelephonyMgr();
                SubscriptionManager ssm = getSubscriptionMgr();
                if (tm == null || ssm == null) {
                    return;
                }
                if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                    return;
                }

//                List<SubscriptionInfo> infos = ssm.getActiveSubscriptionInfoList();
//                int simCount = 0;
//                if (infos != null) {
//                    for (SubscriptionInfo info : infos) {
//                        TelephonyManager subTm = null;
//                        if (info != null) {
//                            subTm = tm.createForSubscriptionId(info.getSubscriptionId());
//                        }
//                        if (subTm == null) {
//                            continue;
//                        }
//                        int simState = subTm.getSimState();
//                        ServiceState ss = subTm.getServiceState();
//                        if (!isSimReady(simState) || !isSimDataReg(ss)) {
//                            continue;
//                        }
//
//                        simCount++;
//
//                        LYXLog.e("检查sim卡：slotId=" + info.getSimSlotIndex()
//                                + ", subId=" + info.getSubscriptionId() + ", iccid=" + info.getIccId());
//                    }
//                }

                int subIndex = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", Integer.MIN_VALUE);
                int slot = intent.getIntExtra("slot", Integer.MIN_VALUE);
                int subscription = intent.getIntExtra("subscription", Integer.MIN_VALUE);
                String ss = intent.getStringExtra("ss");

                if (BuildConfig.DEBUG) {
                    Bundle bundle = intent.getExtras();
                    StatusBarLog.d("SIM广播！"
                            + "\nsubIndex=" + subIndex
                            + "\naction=" + intent.getAction()
                            + "\nslot=" + slot
                            + "\nss=" + ss
                            + "\nsubscription=" + subscription
                            + "\n, keys=" + (bundle == null ? null : Arrays.toString(bundle.keySet().toArray(new String[0]))));
                }

                if (!isSimReady(ss)) {
                    //移除不可用的sim卡记录
                    simInfoMap.remove(slot);
                } else {
                    TelephonyManager subTm = tm.createForSubscriptionId(subscription);
                    if (subTm == null) {
                        simInfoMap.remove(slot);
                    } else {
                        int simState = subTm.getSimState();
                        ServiceState serviceState = subTm.getServiceState();
                        int signalLevel = getSimSignalLevel(subTm);
                        SimInfo simInfo = simInfoMap.get(slot);
                        if (simInfo == null) {
                            simInfo = new SimInfo(slot, subscription, simState, serviceState, signalLevel);
                            simInfoMap.put(slot, simInfo);
                        } else {
                            simInfo.subId = subscription;
                            simInfo.simState = simState;
                            simInfo.serviceState = serviceState;
                            simInfo.signalLevel = signalLevel;
                        }
                    }
                }

                //拔插卡有可能强制改变默认流量卡，就是从其它数量变成单sim卡的时候
                if (simInfoMap.size() == 1) {
                    for (Integer key : simInfoMap.keySet()) {
                        SimInfo info = simInfoMap.get(key);
                        if (info != null && SimInfo.isSimReady(info.simState) && SimInfo.isSimDataReg(info.serviceState)) {
                            StatusBarLog.d("由于拔插卡导致的流量卡变更: slot=" + info.slotId);
                            setPrimarySubId(info.subId);
                        }
                        break;
                    }
                }

                StatusBarLog.d("sim数量变更... 当前sim信息集=" + simInfoMap);
                updateStatusBar(() -> statusBar.onSimChanged(getSimMapClone(), primarySubId));
            }
        }
    }

    /**
     * sim信息类
     */
    public static final class SimInfo {

        /** sim卡槽id，从0开始计数。一般在插卡的情况下，slotId为0的卡槽上的卡，是默认订阅（default subscription） */
        private final int slotId;

        /** sim卡id */
        private int subId;

        /** sim卡状态，取值见{@link TelephonyManager#SIM_STATE_UNKNOWN}等SIM_STATE_XXX系列常量 */
        private int simState;

        /** sim卡的服务状态对象，可获得sim卡的某种服务状态，如果数据服务是否可用，语音电话服务是否可用等 */
        private ServiceState serviceState;

        /**
         * sim卡信号等级。当sim卡不可用时，信号强度比无意义（但一般为{@link #SIM_SIGNAL_LEVEL_MAX}）;
         *  当sim卡存在有效信号时，变量取值范围为[0, 4]，值越高表示信号强度越大。
         *  在处理信号强度之前，应先通过{@link #simState}和{@link #serviceState}判断此时信号强度是否有效。
         */
        private @IntRange(from = 0, to = 4) int signalLevel;

        /**
         * Instantiates a new Sim info.
         *
         * @param slotId the slot id
         */
        public SimInfo(int slotId) {
            this(slotId, Integer.MIN_VALUE, TelephonyManager.SIM_STATE_UNKNOWN, null, StatusBarMgr.SIM_SIGNAL_LEVEL_MAX);
        }

        /**
         * Instantiates a new Sim info.
         *
         * @param slotId       the slot id
         * @param subId        the sub id
         * @param simState     the sim state
         * @param serviceState the service state
         * @param level        the level
         */
        public SimInfo(int slotId, int subId, int simState, ServiceState serviceState, @IntRange(from = 0, to = 4) int level) {
            this.slotId = slotId;
            this.subId = subId;
            this.simState = simState;
            this.serviceState = serviceState;
            this.signalLevel = level;
        }

        /**
         * 检查sim是否就绪。这只是表示sim可以正常工作，但不意味着sim一定处于启用状态。还需要辅助sim具体的数据状态、语音状态等进行判断
         *
         * @param simState 状态字符串。取值见{@link TelephonyManager#SIM_STATE_READY}及其它同类SIM_STATE_XXX常量字段
         * @return boolean
         */
        public static boolean isSimReady(int simState) {
            return simState == TelephonyManager.SIM_STATE_READY;
        }

        /**
         * sim的数据业务是否可用。这表示sim的数据功能可以正常使用，但无法知道sim是否正在使用数据网络
         * 如需获取详细的数据业务注册状态，调用{@link #getSimDataRegState(ServiceState)}
         *
         * @param serviceState the service state
         * @return the boolean
         */
        public static boolean isSimDataReg(ServiceState serviceState) {
            //原代码
            return getSimDataRegState(serviceState) == ServiceState.STATE_IN_SERVICE;
        }

        /**
         * 获得数据业务的注册状态，用于判断sim是否已经关闭数据业务（如停用sim操作是关闭sim的所有业务）
         * 如果只想知道sim的数据业务是否已可用（已注册），调用{@link #isSimDataReg(ServiceState)}即可。
         *
         * @param serviceState the service state
         * @return the sim data reg state
         */
        public static int getSimDataRegState(ServiceState serviceState) {
            int result = ServiceState.STATE_POWER_OFF;
            if (serviceState == null) {
                return result;
            }
            String matchStr = "mDataRegState=";
            String s = serviceState.toString();
            int fieldIndex = s.indexOf(matchStr) + matchStr.length();
            if (fieldIndex < matchStr.length()) {
                return result;
            }
            result = s.charAt(fieldIndex) - 48;
            return result;
        }

        /**
         * Gets slot id.
         *
         * @return the slot id
         */
        public int getSlotId() {
            return slotId;
        }

        /**
         * Gets sub id.
         *
         * @return the sub id
         */
        public int getSubId() {
            return subId;
        }

        /**
         * Sets sub id.
         *
         * @param subId the sub id
         */
        public void setSubId(int subId) {
            this.subId = subId;
        }

        /**
         * Gets sim state.
         *
         * @return the sim state
         */
        public int getSimState() {
            return simState;
        }

        /**
         * Sets sim state.
         *
         * @param simState the sim state
         */
        public void setSimState(int simState) {
            this.simState = simState;
        }

        /**
         * Gets service state.
         *
         * @return the service state
         */
        public ServiceState getServiceState() {
            return serviceState;
        }

        /**
         * Sets service state.
         *
         * @param serviceState the service state
         */
        public void setServiceState(ServiceState serviceState) {
            this.serviceState = serviceState;
        }

        /**
         * Gets signal level.
         *
         * @return the signal level
         */
        public int getSignalLevel() {
            return signalLevel;
        }

        /**
         * Sets signal level.
         *
         * @param signalLevel the signal level
         */
        public void setSignalLevel(int signalLevel) {
            this.signalLevel = signalLevel;
        }

        /**
         * 对象深拷贝，防止外部篡改  @return the sim info
         * @return {@link SimInfo}对象
         */
        public SimInfo deepCopy() {
            return new SimInfo(slotId, subId, simState, new ServiceState(serviceState), signalLevel);
        }

        @Override
        public String toString() {
            int dataRegState = Integer.MIN_VALUE;
            if (serviceState != null) {
                String matchStr = "mDataRegState=";
                String s = serviceState.toString();
                int fieldIndex = s.indexOf(matchStr) + matchStr.length();
                if (fieldIndex > matchStr.length()) {
                    dataRegState = s.charAt(fieldIndex) - 48;
                }
            }

            return "SimInfo{" +
                    "slotId=" + slotId +
                    ", subId=" + subId +
                    ", simState=" + simState +
                    ", serviceState=" + dataRegState +
                    ", signalLevel=" + signalLevel +
                    '}';
        }
    }
}