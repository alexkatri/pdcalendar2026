# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\sgt_o\AppData\Local\Android\Sdk\tools\proguard\proguard-android.txt
# You can edit the include line and the file paths to fit your project structure.

# Firebase Firestore rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
