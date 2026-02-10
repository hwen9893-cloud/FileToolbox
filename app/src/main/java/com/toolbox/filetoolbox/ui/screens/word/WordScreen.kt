package com.toolbox.filetoolbox.ui.screens.word

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
fun WordScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ToolboxTopBar(
            title = "Word文档",
            onBackClick = { navController.popBackStack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Word to PDF
            FeatureCard(
                title = "Word转PDF",
                subtitle = "将Word文档转换为PDF格式",
                icon = Icons.Default.PictureAsPdf,
                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                onClick = { navController.navigate(NavRoutes.WordToPdf.route) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Word Merge
            FeatureCard(
                title = "文档合并",
                subtitle = "将多个Word文档合并为一个文件",
                icon = Icons.Default.MergeType,
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)),
                onClick = { navController.navigate(NavRoutes.WordMerge.route) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Extract Images from Word
            FeatureCard(
                title = "提取图片",
                subtitle = "从Word文档中提取所有图片",
                icon = Icons.Default.Image,
                gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2)),
                onClick = { navController.navigate(NavRoutes.WordExtractImage.route) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
