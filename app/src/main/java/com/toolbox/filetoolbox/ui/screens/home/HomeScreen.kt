package com.toolbox.filetoolbox.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.toolbox.filetoolbox.navigation.NavRoutes
import com.toolbox.filetoolbox.ui.components.FeatureCard
import com.toolbox.filetoolbox.ui.components.SectionTitle
import com.toolbox.filetoolbox.ui.theme.*

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // Header
        HomeHeader()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Feature Categories
        SectionTitle(title = "文件处理工具")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Photo Tools
        FeatureCard(
            title = "照片处理",
            subtitle = "背景替换 · 扫描成PDF · 压缩",
            icon = Icons.Default.Photo,
            gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
            onClick = { navController.navigate(NavRoutes.Photo.route) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // PDF Tools
        FeatureCard(
            title = "PDF工具",
            subtitle = "拆分 · 提取页面 · 提取图片",
            icon = Icons.Default.PictureAsPdf,
            gradientColors = listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
            onClick = { navController.navigate(NavRoutes.Pdf.route) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Word Tools
        FeatureCard(
            title = "Word文档",
            subtitle = "转PDF · 合并文档 · 提取图片",
            icon = Icons.Default.Description,
            gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
            onClick = { navController.navigate(NavRoutes.Word.route) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Excel Tools
        FeatureCard(
            title = "Excel表格",
            subtitle = "转PDF · CSV转换 · 表格合并",
            icon = Icons.Default.TableChart,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
            onClick = { navController.navigate(NavRoutes.Excel.route) }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        SectionTitle(title = "实用工具")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // APK Installer
        FeatureCard(
            title = "APK安装器",
            subtitle = "安装APK · 安装后自动删除源文件",
            icon = Icons.Default.Android,
            gradientColors = listOf(Color(0xFF78909C), Color(0xFF546E7A)),
            onClick = { navController.navigate(NavRoutes.ApkInstaller.route) }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Quick Tips
        QuickTipsCard()
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun HomeHeader() {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Primary, PrimaryDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "文件工具箱",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "照片 · PDF · Word · Excel · 工具",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "使用提示",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TipItem("• 照片压缩支持批量处理，最多9张")
            TipItem("• PDF提取图片保持原始质量")
            TipItem("• Word/Excel文件可转换为PDF格式")
            TipItem("• 支持CSV文件转Excel表格")
            TipItem("• 处理后的文件自动保存到下载目录")
            TipItem("• APK安装器支持安装后自动清理源文件")
        }
    }
}

@Composable
private fun TipItem(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
