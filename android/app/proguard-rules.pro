# JavaMail (android-mail 1.6.x) usa reflexión sobre sus providers
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn javax.**
-dontwarn com.sun.mail.**
