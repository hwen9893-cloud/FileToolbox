package com.toolbox.filetoolbox.ui.screens.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.toolbox.filetoolbox.ui.components.EmptyStateView
import com.toolbox.filetoolbox.ui.components.GradientButton
import com.toolbox.filetoolbox.ui.components.LoadingOverlay
import com.toolbox.filetoolbox.ui.components.ToolboxTopBar
import com.toolbox.filetoolbox.ui.theme.Error
import com.toolbox.filetoolbox.ui.theme.Primary
import com.toolbox.filetoolbox.ui.theme.PrimaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ── Data class for parsed APK info ──
private data class ApkFileInfo(
    val sourceUri: Uri,
    val fileName: String,
    val fileSize: Long,
    val packageName: String?,
    val appLabel: String?,
    val versionName: String?,
    val cacheFile: File,
    var installed: Boolean = false
)

@Composable
fun ApkInstallerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apkList by remember { mutableStateOf<List<ApkFileInfo>>(emptyList()) }
    var autoDelete by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Track the currently installing APK
    var installingIndex by remember { mutableIntStateOf(-1) }

    // ── Launcher for install result ──
    val installLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // When user returns from the system installer, check if the package was installed
        if (installingIndex in apkList.indices) {
            val apkInfo = apkList[installingIndex]
            val pkgName = apkInfo.packageName

            val wasInstalled = if (pkgName != null) {
                try {
                    context.packageManager.getPackageInfo(pkgName, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            } else false

            if (wasInstalled) {
                // Mark as installed
                apkList = apkList.toMutableList().also {
                    it[installingIndex] = apkInfo.copy(installed = true)
                }

                // Auto-delete the source file if enabled
                if (autoDelete) {
                    val deleted = tryDeleteSource(context, apkInfo.sourceUri)
                    val label = apkInfo.appLabel ?: apkInfo.fileName
                    resultMessage = if (deleted) {
                        "✓ $label 安装成功，源文件已删除"
                    } else {
                        "✓ $label 安装成功（源文件需手动删除）"
                    }
                } else {
                    resultMessage = "✓ ${apkInfo.appLabel ?: apkInfo.fileName} 安装成功"
                }

                // Clean up cache copy
                apkInfo.cacheFile.delete()
            } else {
                resultMessage = "安装已取消或未完成"
            }
        }
        installingIndex = -1
    }

    // ── Launcher for picking APK files ──
    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                resultMessage = null

                val newApks = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        parseApkUri(context, uri)
                    }
                }

                apkList = apkList + newApks
                isProcessing = false
            }
        }
    }

    // ── Permission settings launcher ──
    val permissionSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from settings, user can retry
    }

    // ── Install function ──
    fun installApk(index: Int) {
        val apkInfo = apkList[index]

        // Check install permission (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                showPermissionDialog = true
                return
            }
        }

        installingIndex = index
        resultMessage = null

        val installUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkInfo.cacheFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(installUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        installLauncher.launch(intent)
    }

    // ── Install all function ──
    // Note: system installer is sequential, so we install one at a time.
    // This installs the first non-installed APK.
    fun installNext() {
        val nextIndex = apkList.indexOfFirst { !it.installed }
        if (nextIndex >= 0) {
            installApk(nextIndex)
        }
    }

    fun removeApk(index: Int) {
        val apk = apkList[index]
        apk.cacheFile.delete()
        apkList = apkList.toMutableList().apply { removeAt(index) }
    }

    fun clearInstalled() {
        apkList.filter { it.installed }.forEach { it.cacheFile.delete() }
        apkList = apkList.filter { !it.installed }
        if (apkList.isEmpty()) resultMessage = null
    }

    // ── Permission dialog ──
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Primary
                )
            },
            title = { Text("需要安装权限") },
            text = {
                Text("请在设置中允许此应用安装未知来源的应用，然后返回重试。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        permissionSettingsLauncher.launch(intent)
                    }
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ── Cleanup on dispose ──
    DisposableEffect(Unit) {
        onDispose {
            // Clean up cache files when leaving
            apkList.forEach { it.cacheFile.delete() }
        }
    }

    // ──────────── UI ────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ToolboxTopBar(
                title = "APK安装器",
                onBackClick = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Action buttons row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Select APK button
                    OutlinedButton(
                        onClick = {
                            apkPicker.launch(arrayOf("application/vnd.android.package-archive"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("选择APK")
                    }

                    // Clear installed
                    if (apkList.any { it.installed }) {
                        OutlinedButton(
                            onClick = { clearInstalled() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("清除已安装")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Auto-delete toggle ──
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = if (autoDelete) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "安装后自动删除",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "安装成功后自动删除源APK文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDelete,
                            onCheckedChange = { autoDelete = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Result message ──
                resultMessage?.let { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.startsWith("✓"))
                                MaterialTheme.colorScheme.primaryContainer
                            else Error.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (message.startsWith("✓"))
                                    Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (message.startsWith("✓"))
                                    MaterialTheme.colorScheme.primary else Error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── APK list or empty state ──
                if (apkList.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Android,
                        title = "未选择APK文件",
                        subtitle = "点击上方按钮选择要安装的APK文件",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Stats bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "共 ${apkList.size} 个文件",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val installed = apkList.count { it.installed }
                        if (installed > 0) {
                            Text(
                                text = "已安装 $installed/${apkList.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(apkList) { index, apkInfo ->
                            ApkItemCard(
                                apkInfo = apkInfo,
                                isInstalling = installingIndex == index,
                                onInstall = { installApk(index) },
                                onRemove = { removeApk(index) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }

                // ── Install all button ──
                if (apkList.any { !it.installed }) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GradientButton(
                        text = "安装下一个",
                        onClick = { installNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        enabled = installingIndex == -1
                    )
                }
            }
        }

        if (isProcessing) {
            LoadingOverlay(message = "正在解析APK文件...")
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// APK item card
// ──────────────────────────────────────────────────────────────────
@Composable
private fun ApkItemCard(
    apkInfo: ApkFileInfo,
    isInstalling: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit
) {
    val alpha = if (apkInfo.installed) 0.6f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (apkInfo.installed)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // APK icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            if (apkInfo.installed)
                                listOf(Color(0xFF4CAF50), Color(0xFF388E3C))
                            else
                                listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (apkInfo.installed)
                        Icons.Default.CheckCircle else Icons.Default.Android,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apkInfo.appLabel ?: apkInfo.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (apkInfo.appLabel != null) {
                    Text(
                        text = apkInfo.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (apkInfo.versionName != null) {
                        Text(
                            text = "v${apkInfo.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary.copy(alpha = alpha)
                        )
                    }
                    Text(
                        text = formatFileSize(apkInfo.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                    if (apkInfo.packageName != null) {
                        Text(
                            text = apkInfo.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (apkInfo.installed) {
                    Text(
                        text = "已安装",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // Actions
            if (!apkInfo.installed) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                } else {
                    IconButton(
                        onClick = onInstall,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = "安装",
                            tint = Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Parse APK from content URI — copy to cache & extract package info
// ──────────────────────────────────────────────────────────────────
private fun parseApkUri(context: Context, uri: Uri): ApkFileInfo? {
    return try {
        // Try to take persistable permissions for later deletion
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Not all providers support this — that's fine
        }

        // Query file name and size
        var fileName = "unknown.apk"
        var fileSize = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
            }
        }

        // Copy to app cache for installation via FileProvider
        val cacheFile = File(context.cacheDir, "install_${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        // Extract package info from the cached copy
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkgInfo = pm.getPackageArchiveInfo(cacheFile.absolutePath, 0)

        // Fix: need to set sourceDir for loadLabel to work
        pkgInfo?.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = cacheFile.absolutePath
            appInfo.publicSourceDir = cacheFile.absolutePath
        }

        val appLabel = pkgInfo?.applicationInfo?.loadLabel(pm)?.toString()
        val versionName = pkgInfo?.versionName
        val packageName = pkgInfo?.packageName

        ApkFileInfo(
            sourceUri = uri,
            fileName = fileName,
            fileSize = fileSize,
            packageName = packageName,
            appLabel = appLabel,
            versionName = versionName,
            cacheFile = cacheFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ──────────────────────────────────────────────────────────────────
// Try to delete the source APK file
// ──────────────────────────────────────────────────────────────────
private fun tryDeleteSource(context: Context, uri: Uri): Boolean {
    return try {
        // Method 1: DocumentsContract (works for SAF document URIs)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            // Method 2: ContentResolver.delete (works for MediaStore URIs)
            val deleted = context.contentResolver.delete(uri, null, null)
            deleted > 0
        }
    } catch (e: SecurityException) {
        // No write permission for this URI — user will need to delete manually
        false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// ──────────────────────────────────────────────────────────────────
// Format file size
// ──────────────────────────────────────────────────────────────────
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
