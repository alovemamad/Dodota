package ir.mmd.dodota.ui.screens.backup

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.data.repository.BackupRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.FeatureToggleCard
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.worker.NightlyBackupWorker
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BackupRestoreScreen(
    backupRepository: BackupRepository,
    userPreferences: UserPreferences,
    preferencesRepo: UserPreferencesRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var restoreJsonText by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<File?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf<File?>(null) }
    var backupFiles by remember { mutableStateOf(backupRepository.listBackupFiles()) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }

    fun refreshFileList() {
        backupFiles = backupRepository.listBackupFiles()
    }

    // System file picker to pick JSON backup
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    } ?: ""
                    val result = backupRepository.restoreFromJson(content)
                    result.onSuccess { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        refreshFileList()
                    }.onFailure { err ->
                        Toast.makeText(context, "خطا در بازیابی: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "پشتیبان‌گیری فایل JSON",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "حفظ کامل حریم خصوصی و امنیت",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "کلیه اطلاعات در فایل‌های JSON در پوشه اختصاصی MyFinanceBackup ذخیره می‌شوند و داده‌ها کاملاً آفلاین هستند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Automatic Nightly Backup with WorkManager
            item {
                FeatureToggleCard(
                    icon = Icons.Default.Backup,
                    title = "پشتیبان‌گیری خودکار شبانه (WorkManager)",
                    description = if (userPreferences.lastBackupMillis > 0)
                        "آخرین ذخیره خودکار: ${PersianDateHelper.formatToPersianDate(userPreferences.lastBackupMillis)}"
                    else "پشتیبان‌گیری منظم هر شب ساعت ۳ بامداد در پوشه MyFinanceBackup",
                    isChecked = userPreferences.isWeeklyBackupEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            preferencesRepo.setWeeklyBackup(enabled)
                            if (enabled) {
                                NightlyBackupWorker.schedule(context)
                                Toast.makeText(context, "پشتیبان‌گیری خودکار شبانه فعال شد", Toast.LENGTH_SHORT).show()
                            } else {
                                NightlyBackupWorker.cancel(context)
                                Toast.makeText(context, "پشتیبان‌گیری خودکار غیرفعال شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Export to File Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ذخیره فایل پشتیبان JSON در MyFinanceBackup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "مسیر ذخیره: Documents/MyFinanceBackup\nفایل‌ها با فرمت استاندارد JSON ذخیره شده و به راحتی قابل انتقال و نگهداری هستند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val file = backupRepository.saveBackupToFile()
                                    lastSavedFile = file
                                    refreshFileList()
                                    preferencesRepo.updateLastBackupTime(System.currentTimeMillis())
                                    Toast.makeText(context, "فایل با موفقیت در MyFinanceBackup ذخیره شد:\n${file.name}", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ایجاد و ذخیره فایل پشتیبان جدید")
                        }

                        if (lastSavedFile != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    lastSavedFile?.let { file ->
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                                            putExtra(Intent.EXTRA_SUBJECT, "پشتیبان مالی مای‌فایننس")
                                            putExtra(Intent.EXTRA_TEXT, "فایل پشتیبان اطلاعات مالی اپلیکیشن")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل پشتیبان"))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اشتراک‌گذاری آخرین فایل ساخته‌شده")
                            }
                        }
                    }
                }
            }

            // Restore from File Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "بازیابی اطلاعات از فایل پشتیبان",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "می‌توانید هر فایل پشتیبان JSON قبلی را از حافظه گوشی یا پیام‌رسان‌ها انتخاب کرده و بازگردانید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    filePickerLauncher.launch("application/json")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("انتخاب فایل JSON")
                            }

                            OutlinedButton(
                                onClick = { showRestoreDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جای‌گذاری متن")
                            }
                        }
                    }
                }
            }

            // Existing Backup Files in MyFinanceBackup folder
            item {
                Text(
                    text = "فایل‌های موجود در پوشه MyFinanceBackup (${backupFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (backupFiles.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هنوز فایل پشتیبانی در پوشه MyFinanceBackup ذخیره نشده است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(backupFiles) { file ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val sizeKb = (file.length() / 1024.0).let { String.format("%.1f کیلوبایت", it) }
                                Text(
                                    text = "تاریخ: ${PersianDateHelper.formatToPersianDate(file.lastModified())} | حجم: $sizeKb",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showRestoreConfirmDialog = file }
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = "بازیابی", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(
                                    onClick = { showDeleteConfirmDialog = file }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Restore Dialog
    showRestoreConfirmDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = null },
            title = { Text("تأیید بازیابی فایل") },
            text = {
                Text("آیا از بازیابی اطلاعات از فایل ${file.name} اطمینان دارید؟ اطلاعات فعلی جایگزین خواهند شد.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = backupRepository.restoreFromFile(file)
                            result.onSuccess { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showRestoreConfirmDialog = null
                                refreshFileList()
                            }.onFailure { err ->
                                Toast.makeText(context, "خطا در بازیابی: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("بازیابی اطلاعات")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Confirm Delete Dialog
    showDeleteConfirmDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("حذف فایل پشتیبان") },
            text = {
                Text("آیا از حذف فایل ${file.name} مطمئن هستید؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            backupRepository.deleteBackupFile(file)
                            showDeleteConfirmDialog = null
                            refreshFileList()
                            Toast.makeText(context, "فایل حذف شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Paste text JSON restore dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("بازیابی از متن JSON") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "متن JSON فایل پشتیبان را در کادر زیر جای‌گذاری کنید:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = { restoreJsonText = it },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = backupRepository.restoreFromJson(restoreJsonText)
                            result.onSuccess { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showRestoreDialog = false
                                restoreJsonText = ""
                                refreshFileList()
                            }.onFailure { err ->
                                Toast.makeText(context, "خطا در بازیابی: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("شروع بازیابی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
