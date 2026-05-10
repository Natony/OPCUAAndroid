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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== Eclipse Milo (Direct OPC UA backup connection) =====
# Milo uses reflection in its binary stream codecs; without these keep rules
# we hit BadEncodingError at runtime when minification is enabled.
-dontwarn org.eclipse.milo.**
-dontwarn io.netty.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn jakarta.xml.bind.**

-keep class org.eclipse.milo.opcua.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class io.netty.** { *; }

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
