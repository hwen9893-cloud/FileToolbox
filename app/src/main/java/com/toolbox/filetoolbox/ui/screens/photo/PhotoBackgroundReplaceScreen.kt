package com.toolbox.filetoolbox.ui.screens.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resume

data class BackgroundColor(
    val name: String,
    val color: Color
)

@Composable
fun PhotoBackgroundReplaceScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var segmentationMask by remember { mutableStateOf<FloatArray?>(null) }
    var maskWidth by remember { mutableIntStateOf(0) }
    var maskHeight by remember { mutableIntStateOf(0) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var segmentationDone by remember { mutableStateOf(false) }
    var outputFileName by remember { mutableStateOf("") }
    
    val defaultBaseName = "bg_replaced"

    val backgroundColors = remember {
        listOf(
            BackgroundColor("白色", Color.White),
            BackgroundColor("蓝色", Color(0xFF438EDB)),
            BackgroundColor("红色", Color(0xFFD64541)),
            BackgroundColor("浅蓝", Color(0xFF5DADE2)),
            BackgroundColor("灰色", Color(0xFF95A5A6)),
            BackgroundColor("深蓝", Color(0xFF2C3E50)),
            BackgroundColor("绿色", Color(0xFF27AE60)),
            BackgroundColor("渐变蓝", Color(0xFF667EEA))
        )
    }

    // ML Kit selfie segmenter (single image mode)
    val segmenter = remember {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            processedBitmap = null
            segmentationMask = null
            segmentationDone = false
            resultMessage = null

            // Load bitmap
            val bmp = context.contentResolver.openInputStream(it)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            originalBitmap = bmp
        }
    }

    // Run ML Kit segmentation
    fun runSegmentation() {
        val bmp = originalBitmap ?: return

        scope.launch {
            isProcessing = true
            resultMessage = null

            try {
                val inputImage = InputImage.fromBitmap(bmp, 0)

                val result = suspendCancellableCoroutine { cont ->
                    segmenter.process(inputImage)
                        .addOnSuccessListener { mask ->
                            if (cont.isActive) cont.resume(mask)
                        }
                        .addOnFailureListener { e ->
                            if (cont.isActive) {
                                resultMessage = "人像识别失败: ${e.message}"
                                cont.resume(null)
                            }
                        }
                    cont.invokeOnCancellation {
                        // ML Kit task will complete but resume is guarded by isActive
                    }
                }

                if (result != null) {
                    val mw = result.width
                    val mh = result.height
                    val buffer: ByteBuffer = result.buffer
                    buffer.rewind()

                    val floats = FloatArray(mw * mh)
                    for (i in floats.indices) {
                        floats[i] = buffer.float
                    }

                    maskWidth = mw
                    maskHeight = mh
                    segmentationMask = floats
                    segmentationDone = true

                    // Apply the first selected color immediately
                    applyBackground(bmp, floats, mw, mh, backgroundColors[selectedColorIndex].color)?.let {
                        processedBitmap = it
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                resultMessage = "处理失败: ${e.message}"
            }

            isProcessing = false
        }
    }

    // Apply background color using existing mask
    fun applyWithColor(colorIndex: Int) {
        val bmp = originalBitmap ?: return
        val mask = segmentationMask ?: return

        selectedColorIndex = colorIndex

        scope.launch {
            isProcessing = true
            withContext(Dispatchers.Default) {
                applyBackground(bmp, mask, maskWidth, maskHeight, backgroundColors[colorIndex].color)?.let {
                    processedBitmap = it
                }
            }
            isProcessing = false
        }
    }

    fun saveImage() {
        processedBitmap?.let { bitmap ->
            scope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    try {
                        val fileName = outputFileName.ifBlank { "$defaultBaseName.png" }
                        val saved = FileSaver.saveImage(
                            context, bitmap, fileName,
                            Bitmap.CompressFormat.PNG, 100
                        )
                        resultMessage = if (saved != null) "已保存到相册: $fileName" else "保存失败"
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resultMessage = "保存失败: ${e.message}"
                    }
                }
                isProcessing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ToolboxTopBar(
                title = "智能背景替换",
                onBackClick = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Instructions
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
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "AI 人像分割",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "使用 ML Kit 自动识别人像轮廓，精准替换背景颜色\n适用于证件照、头像等人像照片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Preview Area
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f)
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
                        when {
                            processedBitmap != null -> {
                                // Show checkerboard pattern behind transparent areas
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = processedBitmap!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            originalBitmap != null -> {
                                androidx.compose.foundation.Image(
                                    bitmap = originalBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            else -> {
                                EmptyStateView(
                                    icon = Icons.Default.Person,
                                    title = "点击选择人像照片",
                                    subtitle = "支持JPG、PNG格式"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (originalBitmap != null) {
                    // Step 1: Run segmentation if not done
                    if (!segmentationDone) {
                        GradientButton(
                            text = "识别人像",
                            onClick = { runSegmentation() },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Hint
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Primary.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "点击\"识别人像\"后，AI将自动分析照片中的人物轮廓",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Step 2: Choose background color (after segmentation)
                    if (segmentationDone) {
                        Text(
                            text = "选择背景颜色",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(backgroundColors) { index, bgColor ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(bgColor.color)
                                            .border(
                                                width = if (selectedColorIndex == index) 3.dp else 1.dp,
                                                color = if (selectedColorIndex == index) Primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                applyWithColor(index)
                                            }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bgColor.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedColorIndex == index)
                                            Primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Result message
                        resultMessage?.let { message ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (message.startsWith("已保存"))
                                        Color(0xFF10B981).copy(alpha = 0.15f)
                                    else
                                        Error.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (message.startsWith("已保存"))
                                            Icons.Default.CheckCircle
                                        else
                                            Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (message.startsWith("已保存"))
                                            Color(0xFF10B981)
                                        else
                                            Error
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
                            extension = ".png",
                            onNameChanged = { outputFileName = it }
                        )

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("换一张")
                            }

                            GradientButton(
                                text = "保存图片",
                                onClick = { saveImage() },
                                modifier = Modifier.weight(1f),
                                enabled = processedBitmap != null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = if (!segmentationDone) "AI识别中..." else "处理中...")
        }
    }
}

/**
 * Apply a background color to an image using the segmentation mask.
 * Pixels where the mask confidence is high (foreground/person) keep the original pixel.
 * Pixels where the mask confidence is low (background) get the new color.
 * Edge pixels are alpha-blended for a smooth transition.
 */
private fun applyBackground(
    original: Bitmap,
    mask: FloatArray,
    maskW: Int,
    maskH: Int,
    bgColor: Color
): Bitmap? {
    try {
        val w = original.width
        val h = original.height

        val bgArgb = android.graphics.Color.argb(
            255,
            (bgColor.red * 255).toInt(),
            (bgColor.green * 255).toInt(),
            (bgColor.blue * 255).toInt()
        )

        val bgR = android.graphics.Color.red(bgArgb)
        val bgG = android.graphics.Color.green(bgArgb)
        val bgB = android.graphics.Color.blue(bgArgb)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val origPixels = IntArray(w * h)
        original.getPixels(origPixels, 0, w, 0, 0, w, h)

        val resultPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Map pixel coordinates to mask coordinates
                val mx = (x.toFloat() / w * maskW).toInt().coerceIn(0, maskW - 1)
                val my = (y.toFloat() / h * maskH).toInt().coerceIn(0, maskH - 1)
                val confidence = mask[my * maskW + mx]

                val origPixel = origPixels[y * w + x]

                if (confidence > 0.9f) {
                    // Definitely foreground — keep original
                    resultPixels[y * w + x] = origPixel
                } else if (confidence < 0.1f) {
                    // Definitely background — use new color
                    resultPixels[y * w + x] = bgArgb
                } else {
                    // Edge area — blend smoothly
                    val alpha = confidence
                    val oR = android.graphics.Color.red(origPixel)
                    val oG = android.graphics.Color.green(origPixel)
                    val oB = android.graphics.Color.blue(origPixel)

                    val rr = (oR * alpha + bgR * (1 - alpha)).toInt().coerceIn(0, 255)
                    val gg = (oG * alpha + bgG * (1 - alpha)).toInt().coerceIn(0, 255)
                    val bb = (oB * alpha + bgB * (1 - alpha)).toInt().coerceIn(0, 255)

                    resultPixels[y * w + x] = android.graphics.Color.argb(255, rr, gg, bb)
                }
            }
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return result
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
