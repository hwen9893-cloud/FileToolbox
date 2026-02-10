package com.toolbox.filetoolbox.ui.screens.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.toolbox.filetoolbox.ui.components.EmptyStateView
import com.toolbox.filetoolbox.ui.components.FileNameInput
import com.toolbox.filetoolbox.ui.components.GradientButton
import com.toolbox.filetoolbox.ui.components.LoadingOverlay
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar
import com.toolbox.filetoolbox.ui.theme.Success
import com.toolbox.filetoolbox.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
fun PdfExtractPageScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileName by remember { mutableStateOf("") }
    var totalPages by remember { mutableIntStateOf(0) }
    var pageNumberText by remember { mutableStateOf("1") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    
    val defaultBaseName by remember(pdfFileName, pageNumberText) {
        derivedStateOf {
            val base = pdfFileName.substringBeforeLast(".")
            "page_${pageNumberText}_${base}"
        }
    }
    
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedPdfUri = it
            resultMessage = null
            
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
                        if (bytes != null) {
                            val pdfReader = PdfReader(ByteArrayInputStream(bytes))
                            val pdfDocument = PdfDocument(pdfReader)
                            totalPages = pdfDocument.numberOfPages
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
    
    fun extractPage() {
        val pageNumber = pageNumberText.toIntOrNull() ?: return
        if (pageNumber < 1 || pageNumber > totalPages) {
            resultMessage = "页码无效，请输入 1-$totalPages 之间的数字"
            return
        }
        
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
                        
                        val finalName = outputFileName.ifBlank { "$defaultBaseName.pdf" }
                        
                        val saved = FileSaver.saveDocument(
                            context, finalName, FileSaver.MIME_PDF
                        ) { outputStream ->
                            val pdfReader = PdfReader(ByteArrayInputStream(sourceBytes))
                            val sourcePdf = PdfDocument(pdfReader)
                            
                            val pdfWriter = PdfWriter(outputStream)
                            val destPdf = PdfDocument(pdfWriter)
                            
                            sourcePdf.copyPagesTo(pageNumber, pageNumber, destPdf)
                            
                            destPdf.close()
                            sourcePdf.close()
                        }
                        
                        resultMessage = if (saved != null) {
                            "已保存到下载目录: $finalName"
                        } else {
                            "提取失败"
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
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ToolboxTopBar(
                title = "提取页面",
                onBackClick = { navController.popBackStack() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
                                subtitle = "支持所有PDF文件"
                            )
                        }
                    }
                }
                
                if (totalPages > 0) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Page Number Input
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = "输入要提取的页码",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "可输入 1 到 $totalPages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = pageNumberText,
                                onValueChange = { pageNumberText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("页码") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Filename input
                    FileNameInput(
                        defaultName = defaultBaseName,
                        extension = ".pdf",
                        onNameChanged = { outputFileName = it }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Result Message
                    resultMessage?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (message.startsWith("已保存"))
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
                                    imageVector = if (message.startsWith("已保存"))
                                        Icons.Default.CheckCircle
                                    else
                                        Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (message.startsWith("已保存"))
                                        Success
                                    else
                                        MaterialTheme.colorScheme.error
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
                    
                    // Extract Button
                    GradientButton(
                        text = "提取页面",
                        onClick = { extractPage() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
        
        if (isProcessing) {
            LoadingOverlay(message = "正在提取页面...")
        }
    }
}
