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
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun CsvToExcelScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCsvUri by remember { mutableStateOf<Uri?>(null) }
    var csvFileName by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("") }
    var previewLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val defaultBaseName by remember(csvFileName) {
        derivedStateOf { csvFileName.substringBeforeLast(".") + "_converted" }
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedCsvUri = it
            resultMessage = null

            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                csvFileName = cursor.getString(nameIndex) ?: "unknown.csv"
            }

            // Read preview
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val lines = mutableListOf<String>()
                        var count = 0
                        reader.forEachLine { line ->
                            if (count < 5) {
                                lines.add(line)
                                count++
                            }
                        }
                        reader.close()
                        inputStream?.close()
                        previewLines = lines
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun convertToExcel() {
        selectedCsvUri?.let { uri ->
            scope.launch {
                isProcessing = true
                resultMessage = null

                withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val reader = BufferedReader(InputStreamReader(inputStream))

                        val workbook = XSSFWorkbook()
                        val sheet = workbook.createSheet("Sheet1")

                        // Create header style
                        val headerStyle = workbook.createCellStyle()
                        val headerFont = workbook.createFont()
                        headerFont.bold = true
                        headerStyle.setFont(headerFont)

                        var rowIndex = 0
                        reader.forEachLine { line ->
                            val row = sheet.createRow(rowIndex)
                            // Parse CSV line (handle quoted fields)
                            val fields = parseCsvLine(line)

                            fields.forEachIndexed { colIndex, field ->
                                val cell = row.createCell(colIndex)
                                // Try to parse as number
                                val numValue = field.toDoubleOrNull()
                                if (numValue != null) {
                                    cell.setCellValue(numValue)
                                } else {
                                    cell.setCellValue(field)
                                }

                                // Apply header style to first row
                                if (rowIndex == 0) {
                                    cell.cellStyle = headerStyle
                                }
                            }
                            rowIndex++
                        }

                        reader.close()
                        inputStream?.close()

                        // Auto-size columns
                        val firstRow = sheet.getRow(0)
                        if (firstRow != null) {
                            for (colIdx in 0 until firstRow.lastCellNum) {
                                sheet.autoSizeColumn(colIdx)
                            }
                        }

                        val finalName = outputFileName.ifBlank { "$defaultBaseName.xlsx" }

                        val saved = FileSaver.saveDocument(
                            context, finalName, FileSaver.MIME_XLSX
                        ) { out ->
                            workbook.write(out)
                        }
                        workbook.close()

                        resultMessage = if (saved != null) {
                            "已保存到下载目录: $finalName (共 $rowIndex 行)"
                        } else {
                            "转换失败"
                        }
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
                title = "CSV转Excel",
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
                            text = "选择CSV文件，转换为Excel(.xlsx)格式",
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
                        .clickable { csvPicker.launch("text/*") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (selectedCsvUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = csvFileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "CSV文件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { csvPicker.launch("text/*") }) {
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
                                title = "点击选择CSV文件",
                                subtitle = "支持.csv格式"
                            )
                        }
                    }
                }

                // Preview
                if (previewLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "数据预览",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            previewLines.forEachIndexed { index, line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (index == 0)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                if (index < previewLines.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                            if (previewLines.size >= 5) {
                                Text(
                                    text = "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (selectedCsvUri != null) {
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
                        extension = ".xlsx",
                        onNameChanged = { outputFileName = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Convert Button
                    GradientButton(
                        text = "开始转换",
                        onClick = { convertToExcel() },
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

/**
 * Parse a CSV line handling quoted fields
 */
private fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    for (char in line) {
        when {
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                fields.add(current.toString().trim())
                current.clear()
            }
            else -> current.append(char)
        }
    }
    fields.add(current.toString().trim())

    return fields
}
