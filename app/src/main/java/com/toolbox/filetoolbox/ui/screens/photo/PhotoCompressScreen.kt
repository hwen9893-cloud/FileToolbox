package com.toolbox.filetoolbox.ui.screens.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.toolbox.filetoolbox.ui.components.EmptyStateView
import com.toolbox.filetoolbox.ui.components.FileNameInput
import com.toolbox.filetoolbox.ui.components.GradientButton
import com.toolbox.filetoolbox.ui.components.LoadingOverlay
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar
import com.toolbox.filetoolbox.ui.theme.Primary
import com.toolbox.filetoolbox.ui.theme.Success
import com.toolbox.filetoolbox.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun PhotoCompressScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var originalSize by remember { mutableLongStateOf(0L) }
    var compressedSize by remember { mutableLongStateOf(0L) }
    var targetSizeKb by remember { mutableFloatStateOf(500f) }
    var isProcessing by remember { mutableStateOf(false) }
    var compressedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compressedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    
    val defaultBaseName = "compressed_photo"
    
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            compressedBitmap = null
            compressedBytes = null
            compressedSize = 0L
            resultMessage = null

            // Get original file size via ContentResolver query
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    originalSize = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    
    fun compressImage() {
        selectedImageUri?.let { uri ->
            scope.launch {
                isProcessing = true
                resultMessage = null
                
                withContext(Dispatchers.IO) {
                    try {
                        val originalBitmap = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }

                        if (originalBitmap != null) {
                            try {
                                var quality = 95
                                var compressed: ByteArray
                                val targetBytes = (targetSizeKb * 1024).toLong()

                                // Binary search for optimal quality
                                var minQuality = 10
                                var maxQuality = 95

                                while (minQuality <= maxQuality) {
                                    quality = (minQuality + maxQuality) / 2
                                    val outputStream = ByteArrayOutputStream()
                                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                                    compressed = outputStream.toByteArray()

                                    if (compressed.size > targetBytes) {
                                        maxQuality = quality - 1
                                    } else {
                                        minQuality = quality + 1
                                    }
                                }

                                // Final compression with found quality
                                val finalQuality = maxOf(quality, 10)
                                val finalOutputStream = ByteArrayOutputStream()
                                originalBitmap.compress(Bitmap.CompressFormat.JPEG, finalQuality, finalOutputStream)
                                compressed = finalOutputStream.toByteArray()

                                compressedSize = compressed.size.toLong()
                                compressedBytes = compressed
                                compressedBitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.size)

                                resultMessage = "压缩完成！质量: $finalQuality%"
                            } finally {
                                originalBitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resultMessage = "压缩失败: ${e.message}"
                    }
                }
                
                isProcessing = false
            }
        }
    }
    
    fun saveCompressedImage() {
        val bytes = compressedBytes ?: return
        scope.launch {
            isProcessing = true

            withContext(Dispatchers.IO) {
                try {
                    val fileName = outputFileName.ifBlank { "$defaultBaseName.jpg" }
                    // Save the already-compressed bytes directly to preserve exact compression
                    val saved = FileSaver.saveDocument(
                        context, fileName, "image/jpeg"
                    ) { out ->
                        out.write(bytes)
                    }
                    resultMessage = if (saved != null) "已保存到相册: $fileName" else "保存失败"
                } catch (e: Exception) {
                    e.printStackTrace()
                    resultMessage = "保存失败: ${e.message}"
                }
            }

            isProcessing = false
        }
    }
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ToolboxTopBar(
                title = "图片压缩",
                onBackClick = { navController.popBackStack() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Image Preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { imagePicker.launch("image/*") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmapToShow = compressedBitmap ?: selectedImageUri?.let { uri ->
                            remember(uri) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                            }
                        }
                        
                        if (bitmapToShow != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmapToShow.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            EmptyStateView(
                                icon = Icons.Default.AddPhotoAlternate,
                                title = "点击选择图片",
                                subtitle = "支持JPG、PNG格式"
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Size Info
                if (selectedImageUri != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "原始大小",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatSize(originalSize),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                if (compressedSize > 0) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "压缩后",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatSize(compressedSize),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Success
                                        )
                                    }
                                }
                            }
                            
                            if (compressedSize > 0 && originalSize > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                val savedPercent = ((originalSize - compressedSize) * 100 / originalSize).toInt()
                                Text(
                                    text = "节省 $savedPercent% 空间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Success
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Target Size Slider
                    Text(
                        text = "目标大小: ${targetSizeKb.toInt()} KB",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = targetSizeKb,
                        onValueChange = { targetSizeKb = it },
                        valueRange = 50f..2000f,
                        steps = 38,
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "50 KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "2 MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Result Message
                    resultMessage?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Success.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Success
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    FileNameInput(
                        defaultName = defaultBaseName,
                        extension = ".jpg",
                        onNameChanged = { outputFileName = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { compressImage() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("压缩图片")
                        }
                        
                        GradientButton(
                            text = "保存",
                            onClick = { saveCompressedImage() },
                            modifier = Modifier.weight(1f),
                            enabled = compressedBitmap != null
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
        
        if (isProcessing) {
            LoadingOverlay()
        }
    }
}
