# PocketBoot release shrinker/obfuscation preservation rules
# Keep Android @JavascriptInterface bridge methods used by WindowsDirectCatalog and NtriverCatalog
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*

# Keep libsu shell callbacks and topjohnwu classes
-keep class com.topjohnwu.superuser.** { *; }

# Keep PocketBoot enums serialized via Enum.valueOf
-keepclassmembers enum com.pocketboot.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
