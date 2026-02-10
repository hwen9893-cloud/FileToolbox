package com.toolbox.filetoolbox.ui.screens.word

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
import org.apache.poi.xwpf.usermodel.XWPFDocument

data class DocFile(
    val uri: Uri,
    val name: String
)

@Composable
fun WordMergeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDocs by remember { mutableStateOf<List<DocFile>>(emptyList()) }
    var outputFileName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val defaultBaseName = "merged_docs"

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newDocs = uris.map { uri ->
                var name = "unknown.docx"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: "unknown.docx"
                    }
                }
                DocFile(uri, name)
            }
            selectedDocs = (selectedDocs + newDocs).take(20)
        }
    }

    fun removeDoc(index: Int) {
        selectedDocs = selectedDocs.toMutableList().apply { removeAt(index) }
    }

    fun mergeDocs() {
        if (selectedDocs.size < 2) {
            resultMessage = "请至少选择2个文档进行合并"
            return
        }

        scope.launch {
            isProcessing = true
            resultMessage = null

            withContext(Dispatchers.IO) {
                try {
                    val mergedDoc = XWPFDocument()

                    selectedDocs.forEachIndexed { index, docFile ->
                        val docBytes = context.contentResolver.openInputStream(docFile.uri)?.use { it.readBytes() }
                            ?: throw Exception("无法读取文件: ${docFile.name}")
                        val sourceDoc = XWPFDocument(java.io.ByteArrayInputStream(docBytes))

                        // Add page break between documents (except before first)
                        if (index > 0) {
                            val breakPara = mergedDoc.createParagraph()
                            breakPara.isPageBreak = true
                        }

                        // Copy paragraphs
                        for (para in sourceDoc.paragraphs) {
                            val newPara = mergedDoc.createParagraph()
                            newPara.alignment = para.alignment
                            newPara.style = para.style

                            for (run in para.runs) {
                                val newRun = newPara.createRun()
                                newRun.setText(run.text())
                                newRun.isBold = run.isBold
                                newRun.isItalic = run.isItalic
                                newRun.underline = run.underline
                                if (run.fontSize > 0) {
                                    newRun.fontSize = run.fontSize
                                }
                                if (run.fontFamily != null) {
                                    newRun.fontFamily = run.fontFamily
                                }
                                if (run.color != null) {
                                    newRun.color = run.color
                                }
                            }
                        }

                        // Copy tables
                        for (table in sourceDoc.tables) {
                            val newTable = mergedDoc.createTable(
                                table.numberOfRows,
                                table.rows.firstOrNull()?.tableCells?.size ?: 1
                            )

                            for (rowIdx in 0 until table.numberOfRows) {
                                val sourceRow = table.getRow(rowIdx)
                                val targetRow = newTable.getRow(rowIdx)

                                for (cellIdx in 0 until sourceRow.tableCells.size) {
                                    val sourceCell = sourceRow.getCell(cellIdx)
                                    val targetCell = if (cellIdx < targetRow.tableCells.size) {
                                        targetRow.getCell(cellIdx)
                                    } else {
                                        targetRow.addNewTableCell()
                                    }
                                    targetCell.text = sourceCell.text
                                }
                            }
                        }

                        sourceDoc.close()
                    }

                    val finalName = outputFileName.ifBlank { "$defaultBaseName.docx" }

                    val saved = FileSaver.saveDocument(
                        context, finalName, FileSaver.MIME_DOCX
                    ) { out ->
                        mergedDoc.write(out)
                    }
                    mergedDoc.close()

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
                title = "文档合并",
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
                            text = "选择多个Word文档，按顺序合并为一个文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Header with count and add button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择 ${selectedDocs.size} 个文档",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    FilledTonalButton(
                        onClick = {
                            docPicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加文档")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Document List
                if (selectedDocs.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable {
                                docPicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            },
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
                                title = "点击添加Word文档",
                                subtitle = "支持.docx格式"
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(selectedDocs) { index, doc ->
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
                                    // Order number
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
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = doc.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )

                                    IconButton(
                                        onClick = { removeDoc(index) },
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

                // File Name Input
                if (selectedDocs.size >= 2) {
                    FileNameInput(
                        defaultName = defaultBaseName,
                        extension = ".docx",
                        onNameChanged = { outputFileName = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Merge Button
                GradientButton(
                    text = "合并文档",
                    onClick = { mergeDocs() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedDocs.size >= 2
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "正在合并文档...")
        }
    }
}
