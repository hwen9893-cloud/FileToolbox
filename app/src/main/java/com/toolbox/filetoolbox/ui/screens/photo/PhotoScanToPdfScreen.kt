package com.toolbox.filetoolbox.ui.screens.photo

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.toolbox.filetoolbox.ui.components.EmptyStateView
import com.toolbox.filetoolbox.ui.components.FileNameInput
import com.toolbox.filetoolbox.ui.components.GradientButton
import com.toolbox.filetoolbox.ui.components.LoadingOverlay
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar
import com.toolbox.filetoolbox.ui.theme.Error
import com.toolbox.filetoolbox.ui.theme.Primary
import com.toolbox.filetoolbox.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// ─── Enhancement mode definitions ───
private enum class EnhanceMode(val label: String, val desc: String) {
    ORIGINAL("原图", "不做增强处理"),
    GRAYSCALE("灰度增强", "灰度化 + 高对比度 + 降噪"),
    BW_DOCUMENT("黑白文档", "自适应阈值，最像扫描件"),
    SHARPEN("锐化清晰", "降噪 + 对比度 + USM锐化"),
    SUPER_CLEAR("超清扫描", "降噪+自适应阈值+USM锐化")
}

@Composable
fun PhotoScanToPdfScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var enhanceMode by remember { mutableStateOf(EnhanceMode.BW_DOCUMENT) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    val defaultBaseName = "scan_document"

    // Camera
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = (selectedImages + uris).take(20)
            resultMessage = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImages = (selectedImages + listOf(tempCameraUri!!)).take(20)
            resultMessage = null
        }
        tempCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (_, uri) = FileSaver.createCameraTempUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            showPermissionDialog = true
        }
    }

    fun launchCamera() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun removeImage(index: Int) {
        selectedImages = selectedImages.toMutableList().apply { removeAt(index) }
        previewBitmap = null
    }

    // Update preview whenever the selected image or enhance mode changes
    fun updatePreview(uri: Uri, mode: EnhanceMode) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bmp = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    } ?: return@withContext

                    // Scale down for preview to avoid OOM
                    val maxSide = 1200
                    val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
                    val previewW = (bmp.width * scale).toInt().coerceAtLeast(1)
                    val previewH = (bmp.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(bmp, previewW, previewH, true)
                    if (scaled !== bmp) bmp.recycle()

                    previewBitmap = enhanceBitmap(scaled, mode)
                    if (scaled !== previewBitmap) scaled.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Trigger preview update when images or mode change
    LaunchedEffect(selectedImages, enhanceMode) {
        if (selectedImages.isNotEmpty()) {
            val idx = previewIndex.coerceIn(0, selectedImages.size - 1)
            previewIndex = idx
            updatePreview(selectedImages[idx], enhanceMode)
        } else {
            previewBitmap = null
        }
    }

    fun createPdf() {
        if (selectedImages.isEmpty()) return

        scope.launch {
            isProcessing = true
            resultMessage = null

            withContext(Dispatchers.IO) {
                try {
                    val fileName = outputFileName.ifBlank { "$defaultBaseName.pdf" }

                    val saved = FileSaver.saveDocument(
                        context = context,
                        fileName = fileName,
                        mimeType = FileSaver.MIME_PDF
                    ) { outputStream ->
                        val pdfWriter = PdfWriter(outputStream)
                        val pdfDocument = PdfDocument(pdfWriter)
                        val document = Document(pdfDocument)

                        selectedImages.forEach { uri ->
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                // Ensure full resolution
                                inSampleSize = 1
                            }
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bitmap = inputStream?.use {
                                BitmapFactory.decodeStream(it, null, opts)
                            }

                            if (bitmap != null) {
                                // Apply enhancement at full resolution
                                val enhanced = enhanceBitmap(bitmap, enhanceMode)
                                bitmap.recycle()

                                val baos = ByteArrayOutputStream()
                                // Use PNG for B&W/SuperClear (lossless, good compression for B&W)
                                // Use high-quality JPEG for others
                                val useBwMode = enhanceMode == EnhanceMode.BW_DOCUMENT
                                        || enhanceMode == EnhanceMode.SUPER_CLEAR
                                if (useBwMode) {
                                    enhanced.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                } else {
                                    enhanced.compress(Bitmap.CompressFormat.JPEG, 98, baos)
                                }
                                val imageData = ImageDataFactory.create(baos.toByteArray())
                                val image = Image(imageData)

                                val pageWidth = PageSize.A4.width - 72f
                                val pageHeight = PageSize.A4.height - 72f
                                val widthScale = pageWidth / image.imageWidth
                                val heightScale = pageHeight / image.imageHeight
                                val scale = minOf(widthScale, heightScale)
                                image.scale(scale, scale)

                                pdfDocument.addNewPage(PageSize.A4)
                                document.add(image)

                                enhanced.recycle()
                            }
                        }

                        document.close()
                    }

                    resultMessage = if (saved != null) {
                        "PDF已保存到下载目录: $fileName"
                    } else {
                        "创建PDF失败"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    resultMessage = "创建PDF失败: ${e.message}"
                }
            }

            isProcessing = false
        }
    }

    // ── Permission dialog ──
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要相机权限") },
            text = { Text("拍照扫描功能需要使用相机权限，请在系统设置中授予权限。") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    // ── UI ──
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ToolboxTopBar(
                title = "文档扫描",
                onBackClick = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "文档扫描增强",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "拍照或选择文档图片，自动增强为扫描件效果\n支持灰度、黑白、锐化等多种增强模式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Camera / Gallery buttons ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable { launchCamera() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "拍照扫描",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable { imagePicker.launch("image/*") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "从相册选择",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Image list ──
                Text(
                    text = "已选择 ${selectedImages.size} 张图片",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedImages.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateView(
                                icon = Icons.Default.DocumentScanner,
                                title = "暂无图片",
                                subtitle = "点击上方按钮拍照或选择图片"
                            )
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(selectedImages) { index, uri ->
                            ImageThumbnailCard(
                                uri = uri,
                                index = index + 1,
                                isSelected = (index == previewIndex),
                                onClick = {
                                    previewIndex = index
                                    updatePreview(uri, enhanceMode)
                                },
                                onRemove = { removeImage(index) }
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clickable { imagePicker.launch("image/*") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "添加",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Enhancement mode selector ──
                if (selectedImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "增强模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EnhanceMode.values().forEach { mode ->
                            val isSelected = enhanceMode == mode
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { enhanceMode = mode },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        Primary.copy(alpha = 0.15f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    androidx.compose.foundation.BorderStroke(2.dp, Primary)
                                else
                                    null
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = when (mode) {
                                            EnhanceMode.ORIGINAL -> Icons.Default.Image
                                            EnhanceMode.GRAYSCALE -> Icons.Default.Tonality
                                            EnhanceMode.BW_DOCUMENT -> Icons.Default.Description
                                            EnhanceMode.SHARPEN -> Icons.Default.Deblur
                                            EnhanceMode.SUPER_CLEAR -> Icons.Default.AutoFixHigh
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) Primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // ── Enhancement preview ──
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "增强预览 · ${enhanceMode.desc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            previewBitmap?.let { bmp ->
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "增强预览",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } ?: CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Result message ──
                resultMessage?.let { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.startsWith("PDF已保存"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                Error.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (message.startsWith("PDF已保存"))
                                    Icons.Default.CheckCircle
                                else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (message.startsWith("PDF已保存"))
                                    MaterialTheme.colorScheme.primary else Error
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

                // ── Filename ──
                if (selectedImages.isNotEmpty()) {
                    FileNameInput(
                        defaultName = defaultBaseName,
                        extension = ".pdf",
                        onNameChanged = { outputFileName = it }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Generate PDF button ──
                GradientButton(
                    text = "生成扫描PDF",
                    onClick = { createPdf() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedImages.isNotEmpty()
                )

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "正在生成PDF...")
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Thumbnail card for the image list
// ──────────────────────────────────────────────────────────────────
@Composable
private fun ImageThumbnailCard(
    uri: Uri,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // Thumbnails can use 1/4 resolution to save memory
            inSampleSize = 4
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    Box(modifier = Modifier.size(100.dp)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) Modifier.border(
                        2.dp, Primary, RoundedCornerShape(12.dp)
                    ) else Modifier
                )
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .offset(x = 6.dp, y = (-6).dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "删除",
                tint = Error,
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Image enhancement — high-quality document scanning algorithms
// Uses pure Android APIs: ColorMatrix, Canvas, pixel-level processing
// ──────────────────────────────────────────────────────────────────

/**
 * Enhance a bitmap for document scanning effect.
 */
private fun enhanceBitmap(src: Bitmap, mode: EnhanceMode): Bitmap {
    return when (mode) {
        EnhanceMode.ORIGINAL -> src.copy(Bitmap.Config.ARGB_8888, false)
        EnhanceMode.GRAYSCALE -> applyGrayscaleEnhance(src)
        EnhanceMode.BW_DOCUMENT -> applyAdaptiveThreshold(src)
        EnhanceMode.SHARPEN -> applyUsmSharpen(src)
        EnhanceMode.SUPER_CLEAR -> applySuperClearScan(src)
    }
}

// ── Utility: extract grayscale luminance array from bitmap ──
private fun toGrayscaleArray(src: Bitmap): IntArray {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        // ITU-R BT.601 luma weights (same as ColorMatrix saturation=0)
        pixels[i] = ((0.299f * r + 0.587f * g + 0.114f * b) + 0.5f).toInt().coerceIn(0, 255)
    }
    return pixels
}

// ── Utility: IntArray grayscale → ARGB Bitmap ──
private fun grayscaleToBitmap(gray: IntArray, w: Int, h: Int): Bitmap {
    val argb = IntArray(gray.size)
    for (i in gray.indices) {
        val v = gray[i].coerceIn(0, 255)
        argb[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(argb, 0, w, 0, 0, w, h)
    return bmp
}

// ──────────────────────────────────────────────────────────────────
// Gaussian Blur (5×5 kernel) — used for denoising
// ──────────────────────────────────────────────────────────────────
private fun gaussianBlur5x5(gray: IntArray, w: Int, h: Int): IntArray {
    // Separable 5-tap Gaussian: [1, 4, 6, 4, 1] / 16
    val kernel = intArrayOf(1, 4, 6, 4, 1)
    val kSum = 16

    // Horizontal pass
    val temp = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0
            for (k in -2..2) {
                val sx = (x + k).coerceIn(0, w - 1)
                sum += gray[y * w + sx] * kernel[k + 2]
            }
            temp[y * w + x] = sum / kSum
        }
    }

    // Vertical pass
    val result = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0
            for (k in -2..2) {
                val sy = (y + k).coerceIn(0, h - 1)
                sum += temp[sy * w + x] * kernel[k + 2]
            }
            result[y * w + x] = sum / kSum
        }
    }
    return result
}

// ──────────────────────────────────────────────────────────────────
// Box Blur (integral image) — fast mean filter for adaptive threshold
// ──────────────────────────────────────────────────────────────────
private fun boxBlur(gray: IntArray, w: Int, h: Int, radius: Int): IntArray {
    // Build integral image (using Long to avoid overflow on large images)
    val integral = LongArray((w + 1) * (h + 1))
    val iw = w + 1
    for (y in 0 until h) {
        var rowSum = 0L
        for (x in 0 until w) {
            rowSum += gray[y * w + x]
            integral[(y + 1) * iw + (x + 1)] = rowSum + integral[y * iw + (x + 1)]
        }
    }

    val result = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val x1 = (x - radius).coerceAtLeast(0)
            val y1 = (y - radius).coerceAtLeast(0)
            val x2 = (x + radius).coerceAtMost(w - 1)
            val y2 = (y + radius).coerceAtMost(h - 1)
            val count = (x2 - x1 + 1) * (y2 - y1 + 1)

            val sum = integral[(y2 + 1) * iw + (x2 + 1)] -
                    integral[y1 * iw + (x2 + 1)] -
                    integral[(y2 + 1) * iw + x1] +
                    integral[y1 * iw + x1]
            result[y * w + x] = (sum / count).toInt()
        }
    }
    return result
}

// ──────────────────────────────────────────────────────────────────
// Grayscale + Denoise + Contrast Boost
// ──────────────────────────────────────────────────────────────────
private fun applyGrayscaleEnhance(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val gray = toGrayscaleArray(src)

    // Step 1: Denoise with Gaussian blur
    val denoised = gaussianBlur5x5(gray, w, h)

    // Step 2: Contrast stretch — map [darkest..brightest] to [0..255]
    // then apply a gamma curve for more punch
    var minV = 255; var maxV = 0
    for (v in denoised) {
        if (v < minV) minV = v
        if (v > maxV) maxV = v
    }
    // Keep some headroom for paper whites
    val lo = minV + ((maxV - minV) * 0.02f).toInt()
    val hi = maxV - ((maxV - minV) * 0.02f).toInt()
    val range = (hi - lo).coerceAtLeast(1).toFloat()

    // Build a lookup table for speed
    val lut = IntArray(256) { v ->
        val normalized = ((v - lo) / range).coerceIn(0f, 1f)
        // Apply S-curve contrast with gamma ≈ 0.7 (brightens midtones)
        val contrasted = Math.pow(normalized.toDouble(), 0.7).toFloat()
        (contrasted * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    val result = IntArray(w * h)
    for (i in denoised.indices) {
        result[i] = lut[denoised[i].coerceIn(0, 255)]
    }

    return grayscaleToBitmap(result, w, h)
}

// ──────────────────────────────────────────────────────────────────
// Adaptive Threshold — best for B&W document scanning
// Uses local mean with integral image for O(1) per-pixel lookup
// ──────────────────────────────────────────────────────────────────
private fun applyAdaptiveThreshold(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val gray = toGrayscaleArray(src)

    // Step 1: Light denoise
    val denoised = gaussianBlur5x5(gray, w, h)

    // Step 2: Compute local mean with box blur
    // Window size ≈ 1/8 of the shorter dimension (adapts to image size)
    val radius = (minOf(w, h) / 8).coerceIn(15, 80)
    val localMean = boxBlur(denoised, w, h, radius)

    // Step 3: Apply threshold: pixel < localMean * (1 - C) → black, else white
    // C controls sensitivity; lower = more aggressive whitening of background
    val c = 0.08f
    val result = IntArray(w * h)
    for (i in denoised.indices) {
        val threshold = (localMean[i] * (1.0f - c)).toInt()
        result[i] = if (denoised[i] < threshold) 0 else 255
    }

    // Step 4: Simple morphological cleanup — remove isolated noise pixels
    // (a pixel surrounded by 6+ opposite neighbors gets flipped)
    val cleaned = result.copyOf()
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val idx = y * w + x
            val current = result[idx]
            var opposite = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    if (result[(y + dy) * w + (x + dx)] != current) opposite++
                }
            }
            if (opposite >= 6) {
                cleaned[idx] = if (current == 0) 255 else 0
            }
        }
    }

    return grayscaleToBitmap(cleaned, w, h)
}

// ──────────────────────────────────────────────────────────────────
// USM (Unsharp Mask) Sharpen — much better than simple Laplacian
// Formula: sharpened = original + amount * (original - blurred)
// ──────────────────────────────────────────────────────────────────
private fun applyUsmSharpen(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val gray = toGrayscaleArray(src)

    // Step 1: Light denoise first to avoid amplifying noise
    val denoised = gaussianBlur5x5(gray, w, h)

    // Step 2: Contrast enhancement with LUT (same as grayscale enhance)
    var minV = 255; var maxV = 0
    for (v in denoised) {
        if (v < minV) minV = v
        if (v > maxV) maxV = v
    }
    val lo = minV + ((maxV - minV) * 0.02f).toInt()
    val hi = maxV - ((maxV - minV) * 0.02f).toInt()
    val range = (hi - lo).coerceAtLeast(1).toFloat()
    val lut = IntArray(256) { v ->
        val n = ((v - lo) / range).coerceIn(0f, 1f)
        val c = Math.pow(n.toDouble(), 0.75).toFloat()
        (c * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
    val enhanced = IntArray(w * h)
    for (i in denoised.indices) {
        enhanced[i] = lut[denoised[i].coerceIn(0, 255)]
    }

    // Step 3: USM — Gaussian blur the enhanced image, then sharpen
    val blurred = gaussianBlur5x5(enhanced, w, h)
    val amount = 1.8f  // Sharpen strength
    val threshold = 4   // Don't sharpen below this difference (avoids noise)
    val result = IntArray(w * h)
    for (i in enhanced.indices) {
        val diff = enhanced[i] - blurred[i]
        if (kotlin.math.abs(diff) > threshold) {
            result[i] = (enhanced[i] + (amount * diff).toInt()).coerceIn(0, 255)
        } else {
            result[i] = enhanced[i]
        }
    }

    return grayscaleToBitmap(result, w, h)
}

// ──────────────────────────────────────────────────────────────────
// Super Clear Scan — combines all techniques for best quality
// Pipeline: denoise → contrast stretch → adaptive threshold → USM edge sharpen
// ──────────────────────────────────────────────────────────────────
private fun applySuperClearScan(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val gray = toGrayscaleArray(src)

    // Step 1: Two-pass denoise for heavy noise reduction
    val pass1 = gaussianBlur5x5(gray, w, h)
    val denoised = gaussianBlur5x5(pass1, w, h)

    // Step 2: Adaptive threshold with tighter parameters
    val radius = (minOf(w, h) / 8).coerceIn(15, 80)
    val localMean = boxBlur(denoised, w, h, radius)

    // Use a slightly less aggressive threshold to preserve fine details
    val c = 0.06f
    val thresholded = IntArray(w * h)
    for (i in denoised.indices) {
        val threshold = (localMean[i] * (1.0f - c)).toInt()
        thresholded[i] = if (denoised[i] < threshold) 0 else 255
    }

    // Step 3: Morphological cleanup — remove isolated noise
    val cleaned = thresholded.copyOf()
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val idx = y * w + x
            val current = thresholded[idx]
            var opposite = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    if (thresholded[(y + dy) * w + (x + dx)] != current) opposite++
                }
            }
            if (opposite >= 7) {
                cleaned[idx] = if (current == 0) 255 else 0
            }
        }
    }

    // Step 4: USM sharpen on the B&W result to crisp up text edges
    // Since it's already B&W, use a lighter sharpen
    val blurred = gaussianBlur5x5(cleaned, w, h)
    val amount = 1.5f
    val result = IntArray(w * h)
    for (i in cleaned.indices) {
        val diff = cleaned[i] - blurred[i]
        if (kotlin.math.abs(diff) > 2) {
            val v = (cleaned[i] + (amount * diff).toInt()).coerceIn(0, 255)
            // Re-threshold to keep pure B&W
            result[i] = if (v < 128) 0 else 255
        } else {
            result[i] = cleaned[i]
        }
    }

    return grayscaleToBitmap(result, w, h)
}
