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
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import androidx.compose.ui.res.stringResource
import com.example.clipcraft.R
import com.example.clipcraft.models.EditHistory
import com.example.clipcraft.models.User
import com.example.clipcraft.utils.LocaleManager
import com.example.clipcraft.utils.LocaleHelper
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val localeManager: LocaleManager
) : ViewModel()

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
    onStartTutorial: () -> Unit,
    onNavigateToSubscription: () -> Unit
) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val localeManager = viewModel.localeManager
    val context = LocalContext.current
    val activity = context as? Activity
    var displayName by remember(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    val currentLanguage by localeManager.localeFlow.collectAsState(initial = LocaleManager.DEFAULT_LOCALE)
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_profile)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (displayName != (user?.displayName ?: "")) {
                        IconButton(onClick = { onUpdateName(displayName) }) {
                            Icon(Icons.Default.Done, contentDescription = stringResource(R.string.profile_save_name))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (user == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.profile_user_not_found))
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
                                stringResource(R.string.profile_edit_history),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                if (editHistory.isEmpty()) 
                                    stringResource(R.string.profile_history_empty)
                                else 
                                    stringResource(R.string.profile_history_count, editHistory.size),
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
            
            // Кнопка управления подписками
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = onNavigateToSubscription,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                                stringResource(R.string.profile_subscription_management),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                stringResource(R.string.profile_subscription_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.profile_open_subscriptions),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Секция настроек
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    stringResource(R.string.profile_settings),
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
                                stringResource(R.string.profile_tutorial_mode),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.profile_tutorial_description),
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
                        Text(stringResource(R.string.profile_restart_tutorial))
                    }
                }
            }
            
            // Language selector
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.profile_language),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        ExposedDropdownMenuBox(
                            expanded = isLanguageDropdownExpanded,
                            onExpandedChange = { isLanguageDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = LocaleManager.SUPPORTED_LOCALES[currentLanguage] ?: LocaleManager.SUPPORTED_LOCALES[LocaleManager.DEFAULT_LOCALE]!!,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = isLanguageDropdownExpanded
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = isLanguageDropdownExpanded,
                                onDismissRequest = { isLanguageDropdownExpanded = false }
                            ) {
                                LocaleManager.SUPPORTED_LOCALES.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            isLanguageDropdownExpanded = false
                                            if (code != currentLanguage) {
                                                kotlinx.coroutines.GlobalScope.launch {
                                                    localeManager.setLocale(code)
                                                }
                                                activity?.let { act ->
                                                    LocaleHelper.setLanguage(act, code)
                                                    act.recreate()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
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
                                stringResource(R.string.profile_feedback_form),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                stringResource(R.string.profile_feedback_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.profile_open_form),
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
                    Text(stringResource(R.string.profile_sign_out))
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
                label = { Text(stringResource(R.string.profile_your_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(stringResource(R.string.profile_email_format, user.email), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.profile_credits_format, user.creditsRemaining), style = MaterialTheme.typography.bodyMedium)
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