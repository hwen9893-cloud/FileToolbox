package com.toolbox.filetoolbox.ui.screens.excel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.toolbox.filetoolbox.navigation.NavRoutes
import com.toolbox.filetoolbox.ui.components.FeatureCard
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar

@Composable
fun ExcelScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ToolboxTopBar(
            title = "Excel表格",
            onBackClick = { navController.popBackStack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Excel to PDF
            FeatureCard(
                title = "Excel转PDF",
                subtitle = "将Excel表格转换为PDF文档",
                icon = Icons.Default.PictureAsPdf,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
                onClick = { navController.navigate(NavRoutes.ExcelToPdf.route) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CSV to Excel
            FeatureCard(
                title = "CSV转Excel",
                subtitle = "将CSV文件转换为Excel格式",
                icon = Icons.Default.SwapHoriz,
                gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                onClick = { navController.navigate(NavRoutes.CsvToExcel.route) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Excel Merge
            FeatureCard(
                title = "表格合并",
                subtitle = "将多个Excel文件的工作表合并",
                icon = Icons.Default.MergeType,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)),
                onClick = { navController.navigate(NavRoutes.ExcelMerge.route) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
