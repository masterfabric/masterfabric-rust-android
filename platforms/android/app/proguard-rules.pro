# UniFFI/JNA uses native symbols, callbacks and reflection. Do not rename or remove them.
-keep class com.sun.jna.** { *; }
-keep class co.masterfabric.rust.core.** { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * extends com.sun.jna.Structure { <fields>; }
-dontwarn java.awt.**
-dontwarn javax.swing.**
