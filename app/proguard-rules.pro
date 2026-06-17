# Server/native integration rules
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the JNI entry point classes
-keep class com.stremio.core.** { *; }

# Keep protobuf runtime classes used by pbandk
-keep class pbandk.** { *; }
-dontwarn pbandk.**

# Keep Android components registered in Manifest
-keep class com.stremio.mobile.MainApplication { *; }
-keep class com.stremio.mobile.MainActivity { *; }
-keep class com.stremio.mobile.server.ServerService { *; }
-keep class com.stremio.mobile.server.BootReceiver { *; }

