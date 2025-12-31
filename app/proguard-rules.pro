# GEMS - Bank Negara Malaysia
# ProGuard Rules for Release Build

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all classes in your package
-keep class com.supremainc.sfm_sdk_android.** { *; }

# Keep Suprema SDK classes
-keep class com.supremainc.sfm_sdk.** { *; }
-keep interface com.supremainc.sfm_sdk.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Activity classes
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends com.google.android.material.bottomsheet.BottomSheetDialogFragment

# Lottie animations
-keep class com.airbnb.lottie.** { *; }

# Material Design
-keep class com.google.android.material.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}