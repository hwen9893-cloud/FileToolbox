package com.toolbox.filetoolbox.ui.screens.word

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
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
import org.apache.poi.xwpf.usermodel.XWPFDocument

@Composable
fun WordToPdfScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDocUri by remember { mutableStateOf<Uri?>(null) }
    var docFileName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var outputFileName by remember { mutableStateOf("") }
    val defaultBaseName by remember(docFileName) {
        derivedStateOf { docFileName.substringBeforeLast(".") + "_converted" }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedDocUri = it
            resultMessage = null

            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    docFileName = cursor.getString(nameIndex) ?: "unknown.docx"
                }
            }
        }
    }

    fun convertToPdf() {
        selectedDocUri?.let { uri ->
            scope.launch {
                isProcessing = true
                resultMessage = null

                withContext(Dispatchers.IO) {
                    try {
                        val docBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw Exception("无法读取文件")
                        val doc = XWPFDocument(java.io.ByteArrayInputStream(docBytes))

                        val finalName = outputFileName.ifBlank { "$defaultBaseName.pdf" }

                        val saved = FileSaver.saveDocument(
                            context, finalName, FileSaver.MIME_PDF
                        ) { outputStream ->

                        val pdfWriter = PdfWriter(outputStream)
                        val pdfDocument = PdfDocument(pdfWriter)
                        val document = Document(pdfDocument, PageSize.A4)
                        document.setMargins(72f, 72f, 72f, 72f)

                        // Extract text from Word document and write to PDF
                        val paragraphs = doc.paragraphs
                        for (para in paragraphs) {
                            val text = para.text
                            if (text.isNotEmpty()) {
                                val fontSize = when {
                                    para.style == "Heading1" || para.style == "heading 1" -> 24f
                                    para.style == "Heading2" || para.style == "heading 2" -> 20f
                                    para.style == "Heading3" || para.style == "heading 3" -> 16f
                                    else -> 12f
                                }

                                val pdfParagraph = Paragraph(text)
                                    .setFontSize(fontSize)

                                // Handle alignment
                                when (para.alignment) {
                                    org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER ->
                                        pdfParagraph.setTextAlignment(TextAlignment.CENTER)
                                    org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT ->
                                        pdfParagraph.setTextAlignment(TextAlignment.RIGHT)
                                    else ->
                                        pdfParagraph.setTextAlignment(TextAlignment.LEFT)
                                }

                                document.add(pdfParagraph)
                            } else {
                                // Add empty paragraph for spacing
                                document.add(Paragraph("\n").setFontSize(12f))
                            }
                        }

                        // Process tables
                        for (table in doc.tables) {
                            val iTable = com.itextpdf.layout.element.Table(
                                table.rows.firstOrNull()?.tableCells?.size ?: 1
                            )
                            iTable.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))

                            for (row in table.rows) {
                                for (cell in row.tableCells) {
                                    val cellText = cell.text
                                    val iCell = com.itextpdf.layout.element.Cell()
                                        .add(Paragraph(cellText).setFontSize(11f))
                                        .setPadding(4f)
                                    iTable.addCell(iCell)
                                }
                            }

                            document.add(iTable)
                            document.add(Paragraph("\n"))
                        }

                        document.close()
                        doc.close()

                        } // end saveDocument lambda

                        resultMessage = if (saved != null) "已保存到下载目录: $finalName" else "转换失败"
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resultMessage = "转换失败: ${e.message}"
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
                title = "Word转PDF",
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "选择Word文档(.docx)，转换为PDF格式",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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
                                Text(
                                    text = "Word文档",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                subtitle = "支持.docx格式"
                            )
                        }
                    }
                }

                if (selectedDocUri != null) {
                    Spacer(modifier = Modifier.height(24.dp))

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

                    // File Name Input
                    FileNameInput(
                        defaultName = defaultBaseName,
                        extension = ".pdf",
                        onNameChanged = { outputFileName = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Convert Button
                    GradientButton(
                        text = "开始转换",
                        onClick = { convertToPdf() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "正在转换...")
        }
    }
}
