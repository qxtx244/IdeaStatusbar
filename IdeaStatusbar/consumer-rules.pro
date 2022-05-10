-keep class com.qxtx.idea.statusbar.** {*;}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

-assumenosideeffects class com.qxtx.idea.statusbar.StatusBarLog {
    public static void d(...);
}