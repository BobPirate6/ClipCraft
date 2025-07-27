package com.example.clipcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import com.example.clipcraft.models.EditHistory
import com.example.clipcraft.models.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User?,
    editHistory: List<EditHistory>,
    tutorialEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onUpdateName: (String) -> Unit,
    onLoadHistoryItem: (EditHistory) -> Unit,
    onDeleteAccount: () -> Unit,  // Добавлено
    onSignOut: () -> Unit,  // Добавлено
    onTutorialEnabledChange: (Boolean) -> Unit,
    onStartTutorial: () -> Unit
) {
    var displayName by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
    var isHistoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (displayName != (user?.displayName ?: "")) {
                        IconButton(onClick = { onUpdateName(displayName) }) {
                            Icon(Icons.Default.Done, contentDescription = "Сохранить имя")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (user == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Пользователь не найден")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                UserInfoSection(
                    user = user,
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it }
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = { isHistoryExpanded = !isHistoryExpanded }
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
                                "История монтажей",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                if (editHistory.isEmpty()) 
                                    "Ваша история пока пуста"
                                else 
                                    "${editHistory.size} монтажей",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isHistoryExpanded) "Свернуть" else "Развернуть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Анимированное раскрытие истории
            item {
                AnimatedVisibility(
                    visible = isHistoryExpanded && editHistory.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        editHistory.sortedByDescending { it.timestamp }.forEach { historyItem ->
                            HistoryItemCard(
                                item = historyItem,
                                onLoadForEdit = { onLoadHistoryItem(historyItem) }
                            )
                        }
                    }
                }
            }

            // Секция настроек
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Настройки",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                HorizontalDivider()
            }
            
            // Настройка туториала
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                                "Обучающий режим",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Показывать подсказки при использовании приложения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tutorialEnabled,
                            onCheckedChange = onTutorialEnabledChange
                        )
                    }
                }
                
                // Кнопка для перезапуска туториала
                if (tutorialEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onStartTutorial,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Начать обучение заново")
                    }
                }
            }

            // Ссылка на форму обратной связи
            item {
                val uriHandler = LocalUriHandler.current
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = { 
                        uriHandler.openUri("https://forms.gle/Ft9CCkpdh1y5Zx7RA")
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
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
                                "Форма обратной связи",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Помогите нам улучшить приложение",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Открыть форму",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Добавлены кнопки выхода и удаления
            item {
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выйти из аккаунта")
                }
            }
        }
    }
}

@Composable
private fun UserInfoSection(
    user: User,
    displayName: String,
    onDisplayNameChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Ваше имя") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Email: ${user.email}", style = MaterialTheme.typography.bodyMedium)
            Text("Кредиты: ${user.creditsRemaining}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HistoryItemCard(item: EditHistory, onLoadForEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onLoadForEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.command,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Создано: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}