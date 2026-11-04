# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep data models and serialization for Backup / Restore
-keep class ir.mmd.dodota.data.model.** { *; }
-keepclassmembers class ir.mmd.dodota.data.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

