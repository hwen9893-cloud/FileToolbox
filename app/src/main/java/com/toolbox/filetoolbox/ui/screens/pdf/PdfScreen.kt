package com.toolbox.filetoolbox.ui.screens.pdf

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
fun PdfScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ToolboxTopBar(
            title = "PDF工具",
            onBackClick = { navController.popBackStack() }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // PDF Split
            FeatureCard(
                title = "PDF拆分",
                subtitle = "将PDF文件拆分为多个独立文件",
                icon = Icons.Default.ContentCut,
                gradientColors = listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
                onClick = { navController.navigate(NavRoutes.PdfSplit.route) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Extract Page
            FeatureCard(
                title = "提取页面",
                subtitle = "提取指定页面为新的PDF文件",
                icon = Icons.Default.FileCopy,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)),
                onClick = { navController.navigate(NavRoutes.PdfExtractPage.route) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Extract Images
            FeatureCard(
                title = "提取图片",
                subtitle = "从PDF中提取高质量无损图片",
                icon = Icons.Default.Image,
                gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2)),
                onClick = { navController.navigate(NavRoutes.PdfExtractImage.route) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
