# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# =====================================================
# DISABLE OBFUSCATION — keep R8 shrinking only
# =====================================================
# R8 will still tree-shake unused code and optimize bytecode,
# but class/method/field names stay intact. This eliminates
# all reflection-related crashes (Retrofit, Gson, Room, WorkManager,
# ViewModel factories, sealed classes, etc.) in one line.
-dontobfuscate

# =====================================================
# PRESERVE GENERIC SIGNATURES
# =====================================================
# R8 can strip the Signature attribute even without obfuscation.
# Retrofit needs it to resolve generic return types like Response<List<T>>.
# Without it: "java.lang.Class cannot be cast to ParameterizedType"
-keepattributes Signature

# =====================================================
# CRASHLYTICS — readable stack traces
# =====================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =====================================================
# SUPPRESS WARNINGS — optional dependencies
# =====================================================
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-dontwarn sun.misc.Unsafe