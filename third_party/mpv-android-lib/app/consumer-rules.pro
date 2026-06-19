-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.BaseMPVView { *; }
-keepclasseswithmembernames class is.xyz.mpv.** {
    native <methods>;
}

