package com.toolbox.filetoolbox.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.toolbox.filetoolbox.ui.screens.excel.CsvToExcelScreen
import com.toolbox.filetoolbox.ui.screens.excel.ExcelMergeScreen
import com.toolbox.filetoolbox.ui.screens.excel.ExcelScreen
import com.toolbox.filetoolbox.ui.screens.excel.ExcelToPdfScreen
import com.toolbox.filetoolbox.ui.screens.home.HomeScreen
import com.toolbox.filetoolbox.ui.screens.pdf.PdfExtractImageScreen
import com.toolbox.filetoolbox.ui.screens.pdf.PdfExtractPageScreen
import com.toolbox.filetoolbox.ui.screens.pdf.PdfScreen
import com.toolbox.filetoolbox.ui.screens.pdf.PdfSplitScreen
import com.toolbox.filetoolbox.ui.screens.photo.PhotoBackgroundReplaceScreen
import com.toolbox.filetoolbox.ui.screens.photo.PhotoCompressScreen
import com.toolbox.filetoolbox.ui.screens.photo.PhotoScanToPdfScreen
import com.toolbox.filetoolbox.ui.screens.photo.PhotoScreen
import com.toolbox.filetoolbox.ui.screens.tools.ApkInstallerScreen
import com.toolbox.filetoolbox.ui.screens.word.WordExtractImageScreen
import com.toolbox.filetoolbox.ui.screens.word.WordMergeScreen
import com.toolbox.filetoolbox.ui.screens.word.WordScreen
import com.toolbox.filetoolbox.ui.screens.word.WordToPdfScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Main screens
        composable(NavRoutes.Home.route) {
            HomeScreen(navController = navController)
        }
        
        composable(NavRoutes.Photo.route) {
            PhotoScreen(navController = navController)
        }
        
        composable(NavRoutes.Pdf.route) {
            PdfScreen(navController = navController)
        }
        
        composable(NavRoutes.Word.route) {
            WordScreen(navController = navController)
        }
        
        composable(NavRoutes.Excel.route) {
            ExcelScreen(navController = navController)
        }
        
        // Photo sub-screens
        composable(NavRoutes.PhotoBackgroundReplace.route) {
            PhotoBackgroundReplaceScreen(navController = navController)
        }
        
        composable(NavRoutes.PhotoScanToPdf.route) {
            PhotoScanToPdfScreen(navController = navController)
        }
        
        composable(NavRoutes.PhotoCompress.route) {
            PhotoCompressScreen(navController = navController)
        }
        
        // PDF sub-screens
        composable(NavRoutes.PdfSplit.route) {
            PdfSplitScreen(navController = navController)
        }
        
        composable(NavRoutes.PdfExtractPage.route) {
            PdfExtractPageScreen(navController = navController)
        }
        
        composable(NavRoutes.PdfExtractImage.route) {
            PdfExtractImageScreen(navController = navController)
        }
        
        // Word sub-screens
        composable(NavRoutes.WordToPdf.route) {
            WordToPdfScreen(navController = navController)
        }
        
        composable(NavRoutes.WordMerge.route) {
            WordMergeScreen(navController = navController)
        }
        
        composable(NavRoutes.WordExtractImage.route) {
            WordExtractImageScreen(navController = navController)
        }
        
        // Excel sub-screens
        composable(NavRoutes.ExcelToPdf.route) {
            ExcelToPdfScreen(navController = navController)
        }
        
        composable(NavRoutes.CsvToExcel.route) {
            CsvToExcelScreen(navController = navController)
        }
        
        composable(NavRoutes.ExcelMerge.route) {
            ExcelMergeScreen(navController = navController)
        }

        // Tool sub-screens
        composable(NavRoutes.ApkInstaller.route) {
            ApkInstallerScreen(navController = navController)
        }
    }
}
