# Proguard rules for OneUIWidget
-keepclassmembers class * extends android.appwidget.AppWidgetProvider {
    public void *(...);
}
-keep class com.oneui.widget.data.** { *; }
-keep class com.oneui.widget.lunar.** { *; }
