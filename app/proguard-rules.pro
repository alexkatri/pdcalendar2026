# Add project specific ProGuard rules here.

# Firebase Firestore rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Keep app models and serialization classes from being obfuscated by R8
-keep class com.example.androidapp.CalendarEvent { *; }
-keep class com.example.androidapp.CalendarStorage { *; }
-keepclassmembers class com.example.androidapp.** { *; }
-keep @androidx.annotation.Keep class * { *; }
