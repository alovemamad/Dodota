package ir.mmd.dodota.ui.screens.trash

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.CategoryEntity
import ir.mmd.dodota.data.model.TransactionEntity
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    categories: List<CategoryEntity>,
    onBack: () -> Unit
) {
    val trashFlow = remember { repository.trashTransactions }
    val trashList by trashFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "سطل زباله و بازیابی",
                onBackClick = onBack,
                actions = {
                    if (trashList.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "تخلیه کامل سطل زباله",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (trashList.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "سطل زباله خالی است",
                        description = "تراکنش‌های حذف شده به اینجا منتقل می‌شوند و تا زمان تخلیه قابل بازیابی هستند.",
                        icon = Icons.Outlined.Delete
                    )
                }
            } else {
                items(trashList) { tx ->
                    val cat = tx.categoryId?.let { categoryMap[it] }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconView(
                                iconName = cat?.iconName ?: "category",
                                colorHex = cat?.colorHex ?: "#94A3B8",
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.title.ifBlank { cat?.name ?: "تراکنش" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${PersianNumberFormatter.formatCurrency(tx.amount, userPreferences.currency)} • ${PersianDateHelper.formatToPersianDate(tx.dateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.restoreFromTrash(tx)
                                        Toast.makeText(context, "تراکنش با موفقیت بازیابی شد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = "بازیابی",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("تخلیه کامل سطل زباله") },
            text = { Text("آیا مطمئنید؟ تمامی موارد به صورت دائمی حذف خواهند شد و قابل بازگشت نیستند.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.emptyTrash()
                            Toast.makeText(context, "سطل زباله تخلیه شد", Toast.LENGTH_SHORT).show()
                            showEmptyTrashConfirm = false
                        }
                    }
                ) {
                    Text("حذف همیشگی", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
