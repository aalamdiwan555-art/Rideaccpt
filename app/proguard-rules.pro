# Dr. Clicker ProGuard rules

# Keep OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep accessibility service
-keep class com.drclicker.AutoAcceptEngineService { *; }
-keep class com.drclicker.FloatingOverlayService { *; }
-keep class com.drclicker.MainActivity { *; }

# Keep view binding
-keepclassmembers class com.drclicker.databinding.** { *; }
