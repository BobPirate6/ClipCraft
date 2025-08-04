package com.example.clipcraft.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.example.clipcraft.R

/**
 * Данные для шага туториала
 */
data class EditorTutorialStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val highlightArea: HighlightArea? = null
)

/**
 * Область подсветки
 */
enum class HighlightArea {
    TIMELINE,
    TRIM_HANDLES,
    PLAY_BUTTON,
    SAVE_BUTTON,
    VOICE_EDIT,
    ZOOM_SLIDER,
    DRAG_HANDLE
}

@Composable
fun VideoEditorTutorial(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    
    // Debug logging
    LaunchedEffect(isVisible) {
        android.util.Log.d("VideoEditorTutorial", "Tutorial visibility changed to: $isVisible")
    }
    
    val steps = listOf(
        EditorTutorialStep(
            title = stringResource(R.string.tutorial_editor_welcome),
            description = stringResource(R.string.tutorial_editor_intro),
                icon = Icons.Default.Movie
            ),
            EditorTutorialStep(
            title = stringResource(R.string.tutorial_timeline_title),
            description = stringResource(R.string.tutorial_timeline_desc),
                icon = Icons.Default.Timeline,
                highlightArea = HighlightArea.TIMELINE
            ),
            EditorTutorialStep(
            title = stringResource(R.string.tutorial_zoom_title),
            description = stringResource(R.string.tutorial_zoom_desc),
                icon = Icons.Default.ZoomIn,
                highlightArea = HighlightArea.ZOOM_SLIDER
            ),
            EditorTutorialStep(
            title = stringResource(R.string.tutorial_trim_title),
            description = stringResource(R.string.tutorial_trim_desc),
                icon = Icons.Default.ContentCut,
                highlightArea = HighlightArea.TRIM_HANDLES
            ),
            EditorTutorialStep(
            title = stringResource(R.string.tutorial_reorder_title),
            description = stringResource(R.string.tutorial_reorder_desc),
                icon = Icons.Default.DragHandle,
                highlightArea = HighlightArea.DRAG_HANDLE
            ),
            EditorTutorialStep(
            title = stringResource(R.string.tutorial_add_remove_title),
            description = stringResource(R.string.tutorial_add_remove_desc),
                icon = Icons.Default.Add,
                highlightArea = HighlightArea.TIMELINE
            ),
            EditorTutorialStep(
            title = stringResource(R.string.tutorial_undo_title),
            description = stringResource(R.string.tutorial_undo_desc),
                icon = Icons.Default.Undo,
                highlightArea = HighlightArea.TIMELINE
            )
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
            ) {
                // Кнопка "Пропустить" в правом верхнем углу
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 16.dp)
                        .padding(top = 64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Контент туториала
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val step = steps[currentStep]
                    
                    // Иконка
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Заголовок
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Описание
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Индикаторы шагов
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        steps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == currentStep) Color.White
                                        else Color.White.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Кнопки навигации
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep-- },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Color.White)
                                )
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_back))
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (currentStep < steps.size - 1) {
                                    currentStep++
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                if (currentStep < steps.size - 1) stringResource(R.string.action_continue) else stringResource(R.string.action_start)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                if (currentStep < steps.size - 1) Icons.Default.ArrowForward 
                                else Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}