package com.toolbox.filetoolbox.ui.screens.excel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.toolbox.filetoolbox.ui.theme.Error
import com.toolbox.filetoolbox.ui.theme.Success
import com.toolbox.filetoolbox.util.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

data class ExcelFile(
    val uri: Uri,
    val name: String
)

@Composable
fun ExcelMergeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFiles by remember { mutableStateOf<List<ExcelFile>>(emptyList()) }
    var outputFileName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val defaultBaseName = "merged_excel"

    val excelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newFiles = uris.map { uri ->
                var name = "unknown.xlsx"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: "unknown.xlsx"
                    }
                }
                ExcelFile(uri, name)
            }
            selectedFiles = (selectedFiles + newFiles).take(20)
        }
    }

    fun removeFile(index: Int) {
        selectedFiles = selectedFiles.toMutableList().apply { removeAt(index) }
    }

    fun mergeFiles() {
        if (selectedFiles.size < 2) {
            resultMessage = "请至少选择2个文件进行合并"
            return
        }

        scope.launch {
            isProcessing = true
            resultMessage = null

            withContext(Dispatchers.IO) {
                try {
                    val mergedWorkbook = XSSFWorkbook()

                    selectedFiles.forEachIndexed { fileIdx, excelFile ->
                        val excelBytes = context.contentResolver.openInputStream(excelFile.uri)?.use { it.readBytes() }
                            ?: throw Exception("无法读取文件: ${excelFile.name}")
                        val sourceWorkbook = WorkbookFactory.create(java.io.ByteArrayInputStream(excelBytes))

                        for (sheetIdx in 0 until sourceWorkbook.numberOfSheets) {
                            val sourceSheet = sourceWorkbook.getSheetAt(sheetIdx)
                            val sheetName = "${excelFile.name.substringBeforeLast(".")}_${sourceSheet.sheetName}"
                            // Ensure unique sheet name (max 31 chars)
                            val safeName = sheetName.take(31)
                            val targetSheet = try {
                                mergedWorkbook.createSheet(safeName)
                            } catch (e: Exception) {
                                mergedWorkbook.createSheet("${safeName.take(25)}_$fileIdx")
                            }

                            for (row in sourceSheet) {
                                val targetRow = targetSheet.createRow(row.rowNum)
                                for (cell in row) {
                                    val targetCell = targetRow.createCell(cell.columnIndex)
                                    when (cell.cellType) {
                                        CellType.STRING -> targetCell.setCellValue(cell.stringCellValue)
                                        CellType.NUMERIC -> targetCell.setCellValue(cell.numericCellValue)
                                        CellType.BOOLEAN -> targetCell.setCellValue(cell.booleanCellValue)
                                        CellType.FORMULA -> {
                                            try {
                                                targetCell.setCellValue(cell.stringCellValue)
                                            } catch (e: Exception) {
                                                try {
                                                    targetCell.setCellValue(cell.numericCellValue)
                                                } catch (e2: Exception) {
                                                    targetCell.setCellValue("")
                                                }
                                            }
                                        }
                                        else -> targetCell.setCellValue("")
                                    }
                                }
                            }
                        }

                        sourceWorkbook.close()
                    }

                    val finalName = outputFileName.ifBlank { "$defaultBaseName.xlsx" }

                    val saved = FileSaver.saveDocument(
                        context, finalName, FileSaver.MIME_XLSX
                    ) { out ->
                        mergedWorkbook.write(out)
                    }
                    mergedWorkbook.close()

                    resultMessage = if (saved != null) "已保存到下载目录: $finalName" else "合并失败"
                } catch (e: Exception) {
                    e.printStackTrace()
                    resultMessage = "合并失败: ${e.message}"
                }
            }

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
                title = "Excel合并",
                onBackClick = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            text = "选择多个Excel文件，所有工作表将合并到一个文件中",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择 ${selectedFiles.size} 个文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    FilledTonalButton(
                        onClick = { excelPicker.launch("application/*") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加文件")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // File List
                if (selectedFiles.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { excelPicker.launch("application/*") },
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
                                icon = Icons.Default.NoteAdd,
                                title = "点击添加Excel文件",
                                subtitle = "支持.xlsx和.xls格式"
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(selectedFiles) { index, file ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )

                                    IconButton(
                                        onClick = { removeFile(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "删除",
                                            tint = Error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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

                FileNameInput(
                    defaultName = defaultBaseName,
                    extension = ".xlsx",
                    onNameChanged = { outputFileName = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Merge Button
                GradientButton(
                    text = "合并文件",
                    onClick = { mergeFiles() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFiles.size >= 2
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "正在合并文件...")
        }
    }
}
