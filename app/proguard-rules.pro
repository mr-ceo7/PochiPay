# Keep this to preserve line numbers, which is useful for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# --- General rules for libraries using reflection ---

# Gson uses generic type information stored in a class file when working with fields.
# Proguard removes such information by default, so we configure it to keep all of it.
-keepattributes Signature

# Keep annotations, which are used by many libraries (like Gson's @Expose).
-keepattributes *Annotation*

# --- Gson ---

# Prevent warnings about sun.misc.Unsafe, which Gson may use for performance.
-dontwarn sun.misc.**
-keep class sun.misc.Unsafe { *; }

# Keep the data model classes that Gson uses for serialization/deserialization.
# This prevents their fields from being renamed or removed.
-keepclassmembers class com.galvaniytechnologies.ntfy5.network.** { *; }

# Keep GSON specific classes
-keep class com.google.gson.stream.** { *; }

# Keep TypeToken to preserve generic signatures, especially for newer R8 versions.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# --- OkHttp ---
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Retrofit ---
# Keep the API interface and its methods.
-keep interface com.galvaniytechnologies.ntfy5.network.ApiService { *; }

# Keep Retrofit's annotation and method metadata.
-keepclassmembers,allowshrinking interface * {
    @retrofit2.http.GET *;
    @retrofit2.http.POST *;
    @retrofit2.http.PUT *;
    @retrofit2.http.DELETE *;
    @retrofit2.http.HEAD *;
    @retrofit2.http.OPTIONS *;
    @retrofit2.http.PATCH *;
}
-dontwarn retrofit2.Platform$Java8

# Original rules from the file, keeping them in case they are needed for other parts of the app.
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
