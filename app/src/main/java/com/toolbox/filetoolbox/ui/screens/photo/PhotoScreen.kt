package com.toolbox.filetoolbox.ui.screens.photo

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
fun PhotoScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ToolboxTopBar(
            title = "照片处理",
            onBackClick = { navController.popBackStack() }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Background Replace
            FeatureCard(
                title = "背景替换",
                subtitle = "智能识别人像，一键更换背景颜色",
                icon = Icons.Default.Wallpaper,
                gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                onClick = { navController.navigate(NavRoutes.PhotoBackgroundReplace.route) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Scan to PDF
            FeatureCard(
                title = "扫描成PDF",
                subtitle = "拍照或选择图片，生成PDF文档",
                icon = Icons.Default.DocumentScanner,
                gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                onClick = { navController.navigate(NavRoutes.PhotoScanToPdf.route) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Image Compress
            FeatureCard(
                title = "图片压缩",
                subtitle = "智能压缩，指定目标大小",
                icon = Icons.Default.Compress,
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
                onClick = { navController.navigate(NavRoutes.PhotoCompress.route) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
