package com.toolbox.filetoolbox.ui.screens.pdf

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import com.toolbox.filetoolbox.ui.components.EmptyStateView
import com.toolbox.filetoolbox.ui.components.GradientButton
import com.toolbox.filetoolbox.ui.components.LoadingOverlay
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar
import com.toolbox.filetoolbox.ui.theme.Success
import com.toolbox.filetoolbox.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Holds an extracted image along with metadata */
private data class ExtractedImage(
    val bitmap: Bitmap,
    val pageNum: Int,
    val index: Int,
    val width: Int,
    val height: Int,
    val rawBytes: ByteArray  // Keep raw bytes for lossless saving
)

@Composable
fun PdfExtractImageScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileName by remember { mutableStateOf("") }
    var totalPages by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var extractedImages by remember { mutableStateOf<List<ExtractedImage>>(emptyList()) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // Page range for extraction
    var extractStartText by remember { mutableStateOf("1") }
    var extractEndText by remember { mutableStateOf("1") }
    var extractAll by remember { mutableStateOf(true) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedPdfUri = it
            extractedImages.forEach { img -> img.bitmap.recycle() }
            extractedImages = emptyList()
            resultMessage = null

            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(it)?.use { s ->
                            s.readBytes()
                        }
                        if (bytes != null) {
                            val pdfReader = PdfReader(ByteArrayInputStream(bytes))
                            val pdfDocument = PdfDocument(pdfReader)
                            totalPages = pdfDocument.numberOfPages
                            extractStartText = "1"
                            extractEndText = totalPages.toString()
                            extractAll = true
                            pdfDocument.close()
                        }

                        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0 && cursor.moveToFirst()) {
                                pdfFileName = cursor.getString(nameIndex) ?: "unknown.pdf"
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resultMessage = "无法读取PDF文件: ${e.message}"
                    }
                }
            }
        }
    }

    fun extractImages() {
        selectedPdfUri?.let { uri ->
            scope.launch {
                isProcessing = true
                resultMessage = null

                withContext(Dispatchers.IO) {
                    try {
                        val sourceBytes = context.contentResolver.openInputStream(uri)?.use { s ->
                            s.readBytes()
                        }
                        if (sourceBytes == null) {
                            resultMessage = "无法读取PDF文件"
                            return@withContext
                        }

                        val pdfReader = PdfReader(ByteArrayInputStream(sourceBytes))
                        val pdfDocument = PdfDocument(pdfReader)

                        val startPage: Int
                        val endPage: Int
                        if (extractAll) {
                            startPage = 1
                            endPage = pdfDocument.numberOfPages
                        } else {
                            startPage = (extractStartText.toIntOrNull() ?: 1).coerceIn(1, pdfDocument.numberOfPages)
                            endPage = (extractEndText.toIntOrNull() ?: pdfDocument.numberOfPages).coerceIn(startPage, pdfDocument.numberOfPages)
                        }

                        val images = mutableListOf<ExtractedImage>()
                        var imgCounter = 0

                        for (pageNum in startPage..endPage) {
                            val page = pdfDocument.getPage(pageNum)
                            val resources = page.resources

                            val xObjectNames = resources.getResourceNames(PdfName.XObject)

                            for (name in xObjectNames) {
                                try {
                                    val xObject = resources.getResourceObject(PdfName.XObject, name)
                                    if (xObject is PdfStream) {
                                        val subtype = xObject.getAsName(PdfName.Subtype)
                                        if (subtype == PdfName.Image) {
                                            val imageXObject = PdfImageXObject(xObject)
                                            val imageBytes = imageXObject.imageBytes

                                            if (imageBytes != null && imageBytes.isNotEmpty()) {
                                                // Try decoding as bitmap
                                                val bitmap = BitmapFactory.decodeByteArray(
                                                    imageBytes, 0, imageBytes.size
                                                )
                                                if (bitmap != null) {
                                                    images.add(
                                                        ExtractedImage(
                                                            bitmap = bitmap,
                                                            pageNum = pageNum,
                                                            index = imgCounter,
                                                            width = bitmap.width,
                                                            height = bitmap.height,
                                                            rawBytes = imageBytes
                                                        )
                                                    )
                                                    imgCounter++
                                                } else {
                                                    // If BitmapFactory can't decode (e.g. raw pixel data),
                                                    // try to construct from raw ARGB data
                                                    val w = imageXObject.width.toInt()
                                                    val h = imageXObject.height.toInt()
                                                    if (w > 0 && h > 0) {
                                                        try {
                                                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                            val pixelBytes = imageXObject.imageBytes
                                                            if (pixelBytes != null) {
                                                                // Attempt to decode as RGB
                                                                val pixels = IntArray(w * h)
                                                                val bpp = pixelBytes.size / (w * h)
                                                                for (i in pixels.indices) {
                                                                    val offset = i * bpp
                                                                    if (offset + 2 < pixelBytes.size) {
                                                                        val r = pixelBytes[offset].toInt() and 0xFF
                                                                        val g = pixelBytes[offset + 1].toInt() and 0xFF
                                                                        val b = if (bpp >= 3) pixelBytes[offset + 2].toInt() and 0xFF else r
                                                                        pixels[i] = android.graphics.Color.rgb(r, g, b)
                                                                    }
                                                                }
                                                                bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                                                                images.add(
                                                                    ExtractedImage(
                                                                        bitmap = bmp,
                                                                        pageNum = pageNum,
                                                                        index = imgCounter,
                                                                        width = w,
                                                                        height = h,
                                                                        rawBytes = pixelBytes
                                                                    )
                                                                )
                                                                imgCounter++
                                                            }
                                                        } catch (_: Exception) {
                                                            // Skip if we can't handle the format
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        pdfDocument.close()
                        extractedImages = images

                        val rangeText = if (extractAll) "全部页面" else "第${startPage}-${endPage}页"
                        resultMessage = if (images.isEmpty()) {
                            "在${rangeText}中未找到可提取的图片"
                        } else {
                            "在${rangeText}中共找到 ${images.size} 张图片"
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

    fun saveImage(img: ExtractedImage) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val baseName = pdfFileName.substringBeforeLast(".")
                val fileName = "${baseName}_p${img.pageNum}_img${img.index + 1}_${System.currentTimeMillis()}.png"
                val saved = FileSaver.saveImage(context, img.bitmap, fileName)
                resultMessage = if (saved != null) "已保存到相册: $fileName" else "保存失败"
            }
        }
    }

    fun saveAllImages() {
        scope.launch {
            isProcessing = true
            var savedCount = 0

            withContext(Dispatchers.IO) {
                val baseName = pdfFileName.substringBeforeLast(".")
                extractedImages.forEach { img ->
                    val fileName = "${baseName}_p${img.pageNum}_img${img.index + 1}_${System.currentTimeMillis()}.png"
                    val saved = FileSaver.saveImage(context, img.bitmap, fileName)
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
                        .clickable { pdfPicker.launch("application/pdf") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (selectedPdfUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pdfFileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "共 $totalPages 页",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pdfPicker.launch("application/pdf") }) {
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
                                title = "点击选择PDF文件",
                                subtitle = "提取PDF中的所有图片"
                            )
                        }
                    }
                }

                if (selectedPdfUri != null && totalPages > 0) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Page range selector
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "提取范围",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = extractAll,
                                    onClick = { extractAll = true }
                                )
                                Text(
                                    text = "全部页面",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.clickable { extractAll = true }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                RadioButton(
                                    selected = !extractAll,
                                    onClick = { extractAll = false }
                                )
                                Text(
                                    text = "指定页码",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.clickable { extractAll = false }
                                )
                            }

                            if (!extractAll) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = extractStartText,
                                        onValueChange = { v -> extractStartText = v.filter { it.isDigit() } },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("起始页") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Text("—")
                                    OutlinedTextField(
                                        value = extractEndText,
                                        onValueChange = { v -> extractEndText = v.filter { it.isDigit() } },
                                        modifier = Modifier.weight(1f),
                                        label = { Text("结束页") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Extract Button
                    if (extractedImages.isEmpty()) {
                        GradientButton(
                            text = "开始提取图片",
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
                                containerColor = if (message.startsWith("已保存") || message.contains("共找到"))
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
                                    imageVector = if (message.startsWith("已保存") || message.contains("共找到"))
                                        Icons.Default.CheckCircle
                                    else
                                        Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (message.startsWith("已保存") || message.contains("共找到"))
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
                            itemsIndexed(extractedImages) { _, img ->
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable { saveImage(img) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        androidx.compose.foundation.Image(
                                            bitmap = img.bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Page badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "P${img.pageNum}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }

                                        // Size + save indicator
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${img.width}×${img.height}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "保存",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
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
