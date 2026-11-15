# PdfBox-Android ships optional references to desktop-only classes.
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.commons.logging.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-keep class com.tom_roush.pdfbox.** { *; }
