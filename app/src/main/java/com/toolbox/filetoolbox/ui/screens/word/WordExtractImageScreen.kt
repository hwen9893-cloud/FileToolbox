package com.toolbox.filetoolbox.ui.screens.word

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.toolbox.filetoolbox.ui.components.EmptyStateView
import com.toolbox.filetoolbox.ui.components.GradientButton
import com.toolbox.filetoolbox.ui.components.LoadingOverlay
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar
import com.toolbox.filetoolbox.ui.theme.Success
import com.toolbox.filetoolbox.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument

@Composable
fun WordExtractImageScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDocUri by remember { mutableStateOf<Uri?>(null) }
    var docFileName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var extractedImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedDocUri = it
            extractedImages = emptyList()
            resultMessage = null

            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                docFileName = cursor.getString(nameIndex) ?: "unknown.docx"
            }
        }
    }

    fun extractImages() {
        selectedDocUri?.let { uri ->
            scope.launch {
                isProcessing = true
                resultMessage = null

                withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val doc = XWPFDocument(inputStream)
                        inputStream?.close()

                        val images = mutableListOf<Bitmap>()

                        // Extract all pictures from the document
                        for (picture in doc.allPictures) {
                            try {
                                val imageData = picture.data
                                if (imageData != null && imageData.isNotEmpty()) {
                                    val bitmap = BitmapFactory.decodeByteArray(
                                        imageData, 0, imageData.size
                                    )
                                    if (bitmap != null) {
                                        images.add(bitmap)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        doc.close()
                        extractedImages = images

                        resultMessage = if (images.isEmpty()) {
                            "未找到可提取的图片"
                        } else {
                            "共找到 ${images.size} 张图片"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resultMessage = "提取失败: ${e.message}"
                    }
                }

                isProcessing = false
            }
        }
    }

    fun saveImage(bitmap: Bitmap, index: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val fileName = "word_image_${index + 1}_${System.currentTimeMillis()}.png"
                val saved = FileSaver.saveImage(context, bitmap, fileName)
                resultMessage = if (saved != null) "已保存到相册: $fileName" else "保存失败"
            }
        }
    }

    fun saveAllImages() {
        scope.launch {
            isProcessing = true
            var savedCount = 0

            withContext(Dispatchers.IO) {
                extractedImages.forEachIndexed { index, bitmap ->
                    val fileName = "word_image_${index + 1}_${System.currentTimeMillis()}.png"
                    val saved = FileSaver.saveImage(context, bitmap, fileName)
                    if (saved != null) savedCount++
                }
            }

            resultMessage = "已保存 $savedCount 张图片到相册"
            isProcessing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ToolboxTopBar(
                title = "提取图片",
                onBackClick = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // File Selection
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            docPicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (selectedDocUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = docFileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                docPicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "重新选择"
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.UploadFile,
                                title = "点击选择Word文件",
                                subtitle = "提取文档中的所有图片"
                            )
                        }
                    }
                }

                if (selectedDocUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Extract Button
                    if (extractedImages.isEmpty()) {
                        GradientButton(
                            text = "开始提取",
                            onClick = { extractImages() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Result Message
                    resultMessage?.let { message ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (message.startsWith("已保存") || message.startsWith("共找到"))
                                    Success.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (message.startsWith("已保存") || message.startsWith("共找到"))
                                        Icons.Default.CheckCircle
                                    else
                                        Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (message.startsWith("已保存") || message.startsWith("共找到"))
                                        Success
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Extracted Images Grid
                    if (extractedImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "提取的图片",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            TextButton(onClick = { saveAllImages() }) {
                                Text("保存全部")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(extractedImages) { index, bitmap ->
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable { saveImage(bitmap, index) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Save indicator
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "保存",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "正在处理...")
        }
    }
}
