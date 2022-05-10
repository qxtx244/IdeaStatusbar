IdeaStatusbar
=============

## **概述**
app全局状态栏
+ 在某些定制设备上需要隐藏系统状态栏，而使用自定义全局状态栏布局，兼容部分系统的状态栏api
+ 仅支持安卓M及更高版本，在安卓O以下，可能存在显示不准确的问题
+ 支持显示双卡信号、耳机、飞行模式/Wifi/移动网络制式、电量图标&百分比&充电动画等图标
+ 预留空位以摆放自定义控件
+ 支持沉浸式状态栏，以及状态栏图标自动反色
+ 支持各种个性化定制，图标颜色，主题色，充电动画级数和速度等
+ 支持检测系统状态栏的显示状态，作冲突处理

## **使用IdeaStatusbar**
通过StatusBarMgr类来完成功能接入，每个应用应始终只创建一个对象。
  1. 创建StatusBarMgr对象，并在构造方法传入xml布局id/高度/BaseStatusBar对象，来完成状态栏的样式配置。
      如果仅配置高度参数，则将使用预置状态栏控件DefaultStatusBar。如需更多的自定义需求，则使用其它。
  2. 启用状态栏：调用StatusBarMgr实例的setStatusBarEnable(true)方法。这通常应在Application的周期回调方法中完成，
      而不是Activity。因为在非Application周期回调中调用的话，很可能已经错过了为当前界面绑定状态栏的时机。
  3. 主动设置主题色：在状态栏成功被绑定到界面后，调用目标StatusBar对象的setThemeColor(true)方法。
      通过StatusBarMgr的getStatusBar()方法也可以拿到相应的StatusBar对象。
  4. 自定义状态栏UI：通过传入自定义BaseStatusBar对象，完成UI的基本定制。
     - BastStatusBar对象的简单UI定制：使用DefaultStatusBar对象；
     - 使用自定义BastStatusBar对象：重写StatusBar的相关方法，来完成UI的自定义控制。在使用的xml布局中，子控件使用模块预置的控件id，可实现部分UI控制。

#### **demo**：其中包含了调试模式和普通模式。
  - 调试模式：可以手动控制和测试状态栏的各种图标变化
  - 普通模式：可观察状态栏在正常使用情况下的变化

#### **代码混淆**
模块本身不做混淆，如果宿主项目开启了混淆，请加入以下代码到混淆规则：
  ```
  -keep class com.qxtx.idea.statusbar.** {*;}
  ```
