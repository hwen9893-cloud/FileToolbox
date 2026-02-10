# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ── iText7 (PDF generation) ──
# Only keep classes actually used: PdfWriter, PdfDocument, Document, Image, PageSize
-keep class com.itextpdf.kernel.pdf.PdfWriter { *; }
-keep class com.itextpdf.kernel.pdf.PdfDocument { *; }
-keep class com.itextpdf.kernel.pdf.PdfReader { *; }
-keep class com.itextpdf.kernel.geom.PageSize { *; }
-keep class com.itextpdf.layout.Document { *; }
-keep class com.itextpdf.layout.element.Image { *; }
-keep class com.itextpdf.io.image.ImageDataFactory { *; }
-keep class com.itextpdf.io.image.ImageData { *; }
# iText uses ServiceLoader for internal SPI
-keep class com.itextpdf.kernel.pdf.PdfName { *; }
-keep class com.itextpdf.io.source.** { *; }
-keepclassmembers class com.itextpdf.** {
    <init>(...);
}
-dontwarn com.itextpdf.**

# ── Apache POI (Word/Excel processing) ──
# Only keep XSSF (Excel .xlsx) and XWPF (Word .docx) related classes
-keep class org.apache.poi.xssf.** { *; }
-keep class org.apache.poi.xwpf.** { *; }
-keep class org.apache.poi.ss.** { *; }
-keep class org.apache.poi.openxml4j.** { *; }
-keep class org.apache.poi.util.** { *; }
-keep class org.apache.poi.common.** { *; }
# POI uses reflection for internal factories
-keepclassmembers class org.apache.poi.** {
    <init>(...);
}
-dontwarn org.apache.poi.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.**
-dontwarn org.slf4j.**

# ── Bouncy Castle (used by iText for crypto) ──
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keepclassmembers class org.bouncycastle.** {
    <init>(...);
}
-dontwarn org.bouncycastle.**

# ── Kotlin Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose (R8 has built-in rules, no blanket keep needed) ──
# Only keep stability for runtime reflection
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ── Google ML Kit (Selfie Segmentation) ──
-keep class com.google.mlkit.vision.segmentation.** { *; }
-keep class com.google.mlkit.common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
