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
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFilePath by viewModel.selectedFilePath.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val githubToken by viewModel.githubToken.collectAsState()
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
                        text = selectedFileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedFilePath,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onPickFile) {
                        Text("重新选择")
                    }
                }

                // GitHub Token 输入框
                Spacer(modifier = Modifier.height(20.dp))
                GitHubTokenInput(
                    token = githubToken,
                    onTokenChange = viewModel::updateGitHubToken
                )

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

                // 本地备份目录提示
                Spacer(modifier = Modifier.height(24.dp))
                val backupPath = remember { viewModel.getLocalBackupPath() }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "本地备份目录",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = backupPath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击备份时会自动同步仓库文件到此目录",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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

/**
 * GitHub Token 输入组件
 */
@Composable
private fun GitHubTokenInput(
    token: String,
    onTokenChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember(token) { mutableStateOf(token) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GitHub Token",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开", fontSize = 12.sp)
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Personal Access Token") },
                    placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Token 将加密保存在本地，用于备份到私有仓库 backup-doc",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onTokenChange(text) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存 Token")
                }
            } else {
                val status = if (token.isNotBlank()) "已配置 \u2713" else "未配置 \u2717"
                val color = if (token.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text(
                    text = status,
                    fontSize = 12.sp,
                    color = color
                )
            }
        }
    }
}
