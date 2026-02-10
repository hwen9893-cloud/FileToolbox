package com.toolbox.filetoolbox.navigation

sealed class NavRoutes(val route: String) {
    // Main tabs
    object Home : NavRoutes("home")
    object Photo : NavRoutes("photo")
    object Pdf : NavRoutes("pdf")
    object Word : NavRoutes("word")
    object Excel : NavRoutes("excel")
    
    // Photo sub-screens
    object PhotoBackgroundReplace : NavRoutes("photo/background_replace")
    object PhotoScanToPdf : NavRoutes("photo/scan_to_pdf")
    object PhotoCompress : NavRoutes("photo/compress")
    
    // PDF sub-screens
    object PdfSplit : NavRoutes("pdf/split")
    object PdfExtractPage : NavRoutes("pdf/extract_page")
    object PdfExtractImage : NavRoutes("pdf/extract_image")
    
    // Word sub-screens
    object WordToPdf : NavRoutes("word/to_pdf")
    object WordMerge : NavRoutes("word/merge")
    object WordExtractImage : NavRoutes("word/extract_image")
    
    // Excel sub-screens
    object ExcelToPdf : NavRoutes("excel/to_pdf")
    object CsvToExcel : NavRoutes("excel/csv_to_excel")
    object ExcelMerge : NavRoutes("excel/merge")

    // Tool sub-screens
    object ApkInstaller : NavRoutes("tools/apk_installer")
}
