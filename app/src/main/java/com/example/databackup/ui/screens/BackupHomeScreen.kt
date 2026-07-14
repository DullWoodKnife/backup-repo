package com.example.databackup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.databackup.data.local.SCIDocumentFormatter
import com.example.databackup.ui.components.BackupButton
import com.example.databackup.viewmodel.BackupStatus
import com.example.databackup.viewmodel.BackupTarget
import com.example.databackup.viewmodel.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupHomeScreen(
    onPickFile: () -> Unit,
    viewModel: BackupViewModel,
    onRequestManageStorage: () -> Unit = {}
) {
    val selectedUri by viewModel.selectedFileUri.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupStatus) {
        when (backupStatus) {
            is BackupStatus.Error -> {
                snackbarHostState.showSnackbar((backupStatus as BackupStatus.Error).message)
                viewModel.clearStatus()
            }
            is BackupStatus.Success -> {
                snackbarHostState.showSnackbar((backupStatus as BackupStatus.Success).message)
                viewModel.clearStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "资料备份工具",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4B3FE3),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedUri == null) {
                    Text(
                        text = "请先选择要备份的文件",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onPickFile) {
                        Text("浏览本地文件")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "使用系统文件选择器，无需额外存储权限",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "已选择文件",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedUri.toString().takeLast(50),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onPickFile) {
                        Text("重新选择")
                    }
                }

                if (backupStatus is BackupStatus.Loading) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "正在备份至 ${(backupStatus as BackupStatus.Loading).targetName}...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                LocalFormatCard(
                    viewModel = viewModel,
                    onRequestManageStorage = onRequestManageStorage
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val isLoading = backupStatus is BackupStatus.Loading
                BackupButton(
                    label = "备份至\n我的GitHub",
                    containerColor = Color(0xFF24292F),
                    onClick = {
                        if (selectedUri == null) onPickFile()
                        else viewModel.performBackup(BackupTarget.GITHUB)
                    },
                    enabled = !isLoading
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalFormatCard(
    viewModel: BackupViewModel,
    onRequestManageStorage: () -> Unit
) {
    val needsPermission = remember { viewModel.needsAllFilesPermission() }
    val backupPath = remember { viewModel.getLocalBackupPath() }
    val formatConfig by viewModel.formatConfig.collectAsState()
    var showConfigDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SCI 论文格式化",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "自动按 SCI 期刊通用投稿格式排版",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "目录: $backupPath",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${formatConfig.fontFamily} ${if (formatConfig.lineSpacing == "double") "双倍行距" else formatConfig.lineSpacing}")
                    append(" \u00b7 ${formatConfig.paperSize}")
                    append(" \u00b7 ${if (formatConfig.align == "justified") "两端对齐" else "左对齐"}")
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (needsPermission) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u26a0\ufe0f Android 11+ 需开启"所有文件访问权限"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestManageStorage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("去设置开启权限")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showConfigDialog = true }) {
                        Text("配置参数", fontSize = 13.sp)
                    }
                    Button(onClick = { viewModel.initAndFormatLocalDocuments() }) {
                        Text("扫描并格式化论文", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        FormatConfigDialog(
            currentConfig = formatConfig,
            onConfirm = { newConfig ->
                viewModel.updateFormatConfig(newConfig)
                showConfigDialog = false
            },
            onDismiss = { showConfigDialog = false }
        )
    }
}

@Composable
private fun FormatConfigDialog(
    currentConfig: SCIDocumentFormatter.FormatConfig,
    onConfirm: (SCIDocumentFormatter.FormatConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var paperSize by remember { mutableStateOf(currentConfig.paperSize) }
    var lineSpacing by remember { mutableStateOf(currentConfig.lineSpacing) }
    var fontFamily by remember { mutableStateOf(currentConfig.fontFamily) }
    var align by remember { mutableStateOf(currentConfig.align) }
    var enableTitlePage by remember { mutableStateOf(currentConfig.enableTitlePage) }
    var enableAbstract by remember { mutableStateOf(currentConfig.enableAbstract) }
    var enableKeywords by remember { mutableStateOf(currentConfig.enableKeywords) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SCI 论文格式配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("纸张大小", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paperSize == "A4",
                        onClick = { paperSize = "A4" },
                        label = { Text("A4") }
                    )
                    FilterChip(
                        selected = paperSize == "Letter",
                        onClick = { paperSize = "Letter" },
                        label = { Text("Letter") }
                    )
                }
                Text("字体", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = fontFamily == "Times New Roman",
                        onClick = { fontFamily = "Times New Roman" },
                        label = { Text("Times New Roman") }
                    )
                    FilterChip(
                        selected = fontFamily == "Arial",
                        onClick = { fontFamily = "Arial" },
                        label = { Text("Arial") }
                    )
                }
                Text("行距", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = lineSpacing == "double",
                        onClick = { lineSpacing = "double" },
                        label = { Text("双倍") }
                    )
                    FilterChip(
                        selected = lineSpacing == "1.5",
                        onClick = { lineSpacing = "1.5" },
                        label = { Text("1.5倍") }
                    )
                    FilterChip(
                        selected = lineSpacing == "single",
                        onClick = { lineSpacing = "single" },
                        label = { Text("单倍") }
                    )
                }
                Text("对齐方式", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = align == "justified",
                        onClick = { align = "justified" },
                        label = { Text("两端对齐") }
                    )
                    FilterChip(
                        selected = align == "left",
                        onClick = { align = "left" },
                        label = { Text("左对齐") }
                    )
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableTitlePage, onCheckedChange = { enableTitlePage = it })
                    Text("启用标题页格式化（Title/Authors）")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableAbstract, onCheckedChange = { enableAbstract = it })
                    Text("启用 Abstract 段落识别")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableKeywords, onCheckedChange = { enableKeywords = it })
                    Text("启用 Keywords 段落识别")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    SCIDocumentFormatter.FormatConfig(
                        paperSize = paperSize,
                        lineSpacing = lineSpacing,
                        fontFamily = fontFamily,
                        align = align,
                        enableTitlePage = enableTitlePage,
                        enableAbstract = enableAbstract,
                        enableKeywords = enableKeywords
                    )
                )
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
