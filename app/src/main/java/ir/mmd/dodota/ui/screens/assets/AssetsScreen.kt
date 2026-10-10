package ir.mmd.dodota.ui.screens.assets

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun AssetsScreen(
    assets: List<AssetEntity>,
    netWorth: Long,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var assetToEdit by remember { mutableStateOf<AssetEntity?>(null) }
    var assetToDelete by remember { mutableStateOf<AssetEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val totalAssetsValue = assets.sumOf { it.estimatedValue }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "دارایی‌ها و ارزش خالص",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن دارایی")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Net Worth Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "ارزش خالص دارایی‌های شما (Net Worth)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(netWorth, userPreferences.currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "مجموع اموال غیرنقدی: ${PersianNumberFormatter.formatCurrency(totalAssetsValue, userPreferences.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (assets.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "دارایی غیرنقدی ثبت نشده است",
                        description = "می‌توانید ارزش تخمینی خودرو، ملک، طلا و سکه یا بورس را ثبت کنید تا ارزش دارایی شما دقیق محاسبه شود.",
                        icon = Icons.Outlined.Savings
                    )
                }
            } else {
                items(assets, key = { it.id }) { asset ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { assetToEdit = asset }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = asset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${asset.type.title}${if (asset.notes.isNotBlank()) " • " + asset.notes else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(asset.estimatedValue, userPreferences.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { assetToEdit = asset },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "ویرایش دارایی",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { assetToDelete = asset },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "حذف دارایی",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAssetDialog(
            currency = userPreferences.currency,
            onDismiss = { showAddDialog = false },
            onSave = { name, type, value, notes ->
                coroutineScope.launch {
                    repository.insertAsset(
                        AssetEntity(
                            name = name,
                            type = type,
                            estimatedValue = value,
                            notes = notes
                        )
                    )
                    Toast.makeText(context, "دارایی ثبت شد", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }

    assetToEdit?.let { asset ->
        EditAssetDialog(
            asset = asset,
            currency = userPreferences.currency,
            onDismiss = { assetToEdit = null },
            onSave = { newName, newType, newValue, newNotes ->
                coroutineScope.launch {
                    repository.updateAsset(
                        asset.copy(
                            name = newName,
                            type = newType,
                            estimatedValue = newValue,
                            notes = newNotes
                        )
                    )
                    Toast.makeText(context, "دارایی با موفقیت ویرایش شد", Toast.LENGTH_SHORT).show()
                    assetToEdit = null
                }
            }
        )
    }

    assetToDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { assetToDelete = null },
            title = { Text("حذف دارایی") },
            text = { Text("آیا از حذف دارایی «${asset.name}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteAsset(asset)
                            Toast.makeText(context, "دارایی حذف شد", Toast.LENGTH_SHORT).show()
                            assetToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { assetToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun AddAssetDialog(
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AssetType, value: Long, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AssetType.GOLD) }
    var valueText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت دارایی جدید") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام دارایی (مثال: سکه تمام، پژو ۲۰۶)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع دارایی:", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AssetType.values()) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.title) }
                        )
                    }
                }

                PersianAmountInput(
                    value = valueText,
                    onValueChange = { valueText = it },
                    currency = currency,
                    label = "ارزش تخمینی روز"
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات (اختیاری)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = PersianNumberFormatter.parseAmount(valueText)
                    if (name.isNotBlank() && value > 0L) {
                        onSave(name, selectedType, value, notes)
                    }
                }
            ) {
                Text("ثبت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun EditAssetDialog(
    asset: AssetEntity,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (newName: String, newType: AssetType, newValue: Long, newNotes: String) -> Unit
) {
    var name by remember { mutableStateOf(asset.name) }
    var selectedType by remember { mutableStateOf(asset.type) }
    var valueText by remember { mutableStateOf(asset.estimatedValue.toString()) }
    var notes by remember { mutableStateOf(asset.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش دارایی") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام دارایی") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع دارایی:", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AssetType.values()) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.title) }
                        )
                    }
                }

                PersianAmountInput(
                    value = valueText,
                    onValueChange = { valueText = it },
                    currency = currency,
                    label = "ارزش تخمینی روز"
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات (اختیاری)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = PersianNumberFormatter.parseAmount(valueText)
                    if (name.isNotBlank() && value > 0L) {
                        onSave(name, selectedType, value, notes)
                    }
                }
            ) {
                Text("بروزرسانی")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
