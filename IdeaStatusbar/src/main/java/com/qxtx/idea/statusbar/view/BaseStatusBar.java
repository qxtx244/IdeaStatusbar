package com.qxtx.idea.statusbar.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.qxtx.idea.statusbar.R;
import com.qxtx.idea.statusbar.StatusBarLog;
import com.qxtx.idea.statusbar.StatusBarMgr;
import com.qxtx.idea.statusbar.tools.network.NetStateManager;

import java.util.HashMap;

/**
 * @author QXTX-WIN
 * <p><b>Create Date</b></p> 2021/7/14 17:12
 * <p><b>Description</b></p> 基础的状态栏view，可能有些图标无默认方案，需要根据实际业务来实现。
 * · 预置控件id：移动网络信号（主信号+副信号），信号类型，wifi，电池，耳机，飞行模式
 * · 当为此控件设置layout属性时，目标layout中存在预置控件id的功能
 * · 真正的用户状态栏元素为其中的子控件，即{@link #contentView}，通过{@link #getContentView()}获得
 */
public class BaseStatusBar extends FrameLayout implements IStatusBar {

    private final String TAG = getClass().getSimpleName();

    private HashMap<View, Boolean> viewReadyMap = new HashMap<>();

    /**
     * 自定义状态栏view，并不是状态栏根布局
     */
    protected View contentView;

    /**
     * sim信号图标
     */
    protected View simView;

    /**
     * 网络类型图标，比如3G/4G、wifi等
     */
    protected View networkTypeView;

    /**
     * 飞行模式图标
     */
    protected View airplaneModeView;

    /**
     * 耳机图标
     */
    protected View headsetView;

    /**
     * 电池
     */
    protected View batteryView;

    /**
     * 构造方法。在目标状态栏布局中，应使用预置的状态栏控件id。
     *
     * @param context  the context
     * @param layoutId 状态栏内容布局id
     */
    public BaseStatusBar(Context context, int layoutId) {
        this(context, LayoutInflater.from(context).inflate(layoutId, null, false));
    }

    /**
     * 构造方法。在目标view中，应使用预置的状态栏控件id。
     *
     * @param context the context
     * @param view    状态栏view
     */
    public BaseStatusBar(Context context, View view) {
        super(context);
        contentView = view;
        init();
    }

    //不提供xml布局实现
//    public BaseStatusBar(Context context, AttributeSet attrs) {
//        super(context, attrs);
//
//        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LAStatusBar, -1, -1);
//
//        int layoutId = a.getResourceId(R.styleable.LAStatusBar_layout, -1);
//        layoutId = layoutId == -1 ? R.layout.def_status_bar : layoutId;
//        contentView = LayoutInflater.from(context).inflate(layoutId, null ,false);
//        init();
//    }

    /**
     * 在构造方法中做一些初始化操作
     * @see #BaseStatusBar
     */
    private void init() {
        if (contentView == null) {
            return;
        }

        addView(contentView);

        simView = contentView.findViewById(R.id.sb_sim);
        networkTypeView = contentView.findViewById(R.id.sb_networkType);
        airplaneModeView = contentView.findViewById(R.id.sb_airplaneMode);
        headsetView = contentView.findViewById(R.id.sb_headset);

        batteryView = contentView.findViewById(R.id.sb_battery);
    }

    @Override
    public int getWifiMaxLevel() {
        return 4;
    }

    @Override
    public void onSimChanged(HashMap<Integer, StatusBarMgr.SimInfo> simInfoMap, int primarySubId) {
        //do nothing.
    }

    @Override
    public void onBatteryChanged(float fraction, boolean isCharging) {
        //do nothing.
    }

    @Override
    public void onAirplaneChanged(boolean enable) {
        if (airplaneModeView == null || !isUiThread() || isGone()) {
            return ;
        }

        int oldVisibleState = airplaneModeView.getVisibility();
        airplaneModeView.setVisibility(enable ? VISIBLE : GONE);

        if (enable && oldVisibleState == GONE) {
            if (simView != null) {
                simView.setVisibility(GONE);
            }

            if (networkTypeView != null) {
                networkTypeView.setVisibility(GONE);
            }

            //蓝牙应该被隐藏，如果是蓝牙耳机
        }
    }

    @Override
    public void onNetworkTypeChanged(@NetStateManager.NetType int type, float signalFraction, int transferState) {
        if (networkTypeView == null || !isUiThread() || isGone()) {
            return ;
        }

        StatusBarLog.d("可能需要更新网络类型：type=" + type + ", signalFraction=" + signalFraction + ", transferState=" + transferState);

        int resId;
        switch (type) {
            case NetStateManager.NetType.TYPE_3G:
                resId = R.drawable.sb_network_3g_ic;
                break;
            case NetStateManager.NetType.TYPE_4G:
                resId = R.drawable.sb_network_4g_ic;
                break;
            case NetStateManager.NetType.TYPE_5G:
                resId = R.drawable.sb_network_5g_ic;
                break;
            case NetStateManager.NetType.TYPE_WIFI:
                //LYX_TAG 2021/7/20 23:34 这里应该使用线性的图案来做，以支持自定义的信号级数（自己手写个svg）
                //  通过最大等级和图标高度得到扇形边长。背景色为最大扇形阴影，前景色为当前信号强度扇形（通过fraction来计算）
                //int maxLevel = getWifiMaxLevel();

                if (signalFraction > 0.85f) {
                    resId = R.drawable.sb_network_wifi_4_ic;
                } else if (signalFraction > 0.65f) {
                    resId = R.drawable.sb_network_wifi_3_ic;
                } else if (signalFraction > 0.45f) {
                    resId = R.drawable.sb_network_wifi_2_ic;
                } else if (signalFraction > 0.2f) {
                    resId = R.drawable.sb_network_wifi_1_ic;
                } else {
                    resId = R.drawable.sb_network_wifi_0_ic;
                }
                break;
            case NetStateManager.NetType.TYPE_2G:
            case NetStateManager.NetType.TYPE_NONE:
            case NetStateManager.NetType.TYPE_UNKNOWN:
            default:
                resId = View.NO_ID;
                break;
        }

        boolean networkEnable = resId != View.NO_ID;
        networkTypeView.setVisibility(networkEnable ? VISIBLE : GONE);
        if (!networkEnable) {
            networkTypeView.setBackground(null);
        } else {
            networkTypeView.setBackgroundResource(resId);
        }
    }

    @Override
    public void onHeadSetChanged(boolean exist) {
        if (headsetView == null || !isUiThread() || isGone()) {
            return;
        }
        headsetView.setVisibility(exist ? VISIBLE : GONE);
    }

    /** 获取自定义状态栏view */
    @Override
    public View getContentView() {
        return contentView;
    }

    /**
     * Is gone boolean.
     *
     * @return the boolean
     */
    public boolean isGone() {
        return getVisibility() != VISIBLE;
    }

    /**
     * Update.
     *
     * @param target the target
     * @param action the action
     */
    protected void update(View target,  Runnable action) {
        if (target == null) {
            return ;
        }

        boolean viewReady = viewReadyMap.containsKey(target);
        if (viewReady) {
            action.run();
        } else {
            target.post(() -> {
                action.run();

                if (!viewReadyMap.containsKey(target)) {
                    viewReadyMap.put(target, true);
                }
            });
        }
    }
}
