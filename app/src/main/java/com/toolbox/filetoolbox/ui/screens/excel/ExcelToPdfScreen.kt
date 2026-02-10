package com.toolbox.filetoolbox.ui.screens.excel

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
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
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
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory

@Composable
fun ExcelToPdfScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedExcelUri by remember { mutableStateOf<Uri?>(null) }
    var excelFileName by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("") }
    var sheetCount by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val defaultBaseName by remember(excelFileName) {
        derivedStateOf { excelFileName.substringBeforeLast(".") + "_converted" }
    }

    val excelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedExcelUri = it
            resultMessage = null

            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    excelFileName = cursor.getString(nameIndex) ?: "unknown.xlsx"
                }
            }

            // Read sheet count
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
                        if (bytes != null) {
                            val workbook = WorkbookFactory.create(java.io.ByteArrayInputStream(bytes))
                            sheetCount = workbook.numberOfSheets
                            workbook.close()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun convertToPdf() {
        selectedExcelUri?.let { uri ->
            scope.launch {
                isProcessing = true
                resultMessage = null

                withContext(Dispatchers.IO) {
                    try {
                        val excelBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw Exception("无法读取文件")
                        val workbook = WorkbookFactory.create(java.io.ByteArrayInputStream(excelBytes))

                        val finalName = outputFileName.ifBlank { "$defaultBaseName.pdf" }

                        val saved = FileSaver.saveDocument(
                            context, finalName, FileSaver.MIME_PDF
                        ) { outputStream ->

                        val pdfWriter = PdfWriter(outputStream)
                        val pdfDocument = PdfDocument(pdfWriter)
                        val document = Document(pdfDocument, PageSize.A4.rotate())
                        document.setMargins(36f, 36f, 36f, 36f)

                        for (sheetIdx in 0 until workbook.numberOfSheets) {
                            val sheet = workbook.getSheetAt(sheetIdx)

                            // Add sheet title
                            document.add(
                                Paragraph(sheet.sheetName)
                                    .setFontSize(16f)
                                    .setBold()
                            )
                            document.add(Paragraph("\n"))

                            if (sheet.physicalNumberOfRows == 0) continue

                            // Determine max columns
                            var maxCols = 0
                            for (row in sheet) {
                                if (row.lastCellNum > maxCols) {
                                    maxCols = row.lastCellNum.toInt()
                                }
                            }

                            if (maxCols == 0) continue

                            // Create table
                            val table = Table(maxCols)
                            table.setWidth(UnitValue.createPercentValue(100f))

                            // Process rows
                            for (row in sheet) {
                                for (colIdx in 0 until maxCols) {
                                    val cell = row.getCell(colIdx)
                                    val cellText = if (cell != null) {
                                        when (cell.cellType) {
                                            CellType.STRING -> cell.stringCellValue
                                            CellType.NUMERIC -> {
                                                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                                    cell.dateCellValue?.toString() ?: ""
                                                } else {
                                                    val num = cell.numericCellValue
                                                    if (num == num.toLong().toDouble()) {
                                                        num.toLong().toString()
                                                    } else {
                                                        num.toString()
                                                    }
                                                }
                                            }
                                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                            CellType.FORMULA -> {
                                                try {
                                                    cell.stringCellValue
                                                } catch (e: Exception) {
                                                    try {
                                                        cell.numericCellValue.toString()
                                                    } catch (e2: Exception) {
                                                        ""
                                                    }
                                                }
                                            }
                                            else -> ""
                                        }
                                    } else {
                                        ""
                                    }

                                    val pdfCell = Cell()
                                        .add(Paragraph(cellText).setFontSize(9f))
                                        .setPadding(3f)

                                    // Style header row
                                    if (row.rowNum == 0) {
                                        pdfCell.setBold()
                                    }

                                    table.addCell(pdfCell)
                                }
                            }

                            document.add(table)

                            // Add page break between sheets
                            if (sheetIdx < workbook.numberOfSheets - 1) {
                                document.add(com.itextpdf.layout.element.AreaBreak())
                            }
                        }

                        document.close()
                        workbook.close()

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
                title = "Excel转PDF",
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
                            text = "选择Excel文件，转换为PDF表格",
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
                        .clickable { excelPicker.launch("application/*") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (selectedExcelUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = excelFileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (sheetCount > 0) {
                                    Text(
                                        text = "共 $sheetCount 个工作表",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { excelPicker.launch("application/*") }) {
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
                                title = "点击选择Excel文件",
                                subtitle = "支持.xlsx和.xls格式"
                            )
                        }
                    }
                }

                if (selectedExcelUri != null) {
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
