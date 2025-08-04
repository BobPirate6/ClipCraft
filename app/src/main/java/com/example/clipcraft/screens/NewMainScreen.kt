package com.example.clipcraft.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clipcraft.models.*
import com.example.clipcraft.ui.MainViewModel
import com.example.clipcraft.ui.components.*
import com.example.clipcraft.components.ProcessingProgressBar
import com.example.clipcraft.components.rememberProcessingStep
import com.example.clipcraft.components.OptimizedEmbeddedVideoPlayer
import com.example.clipcraft.components.SimpleVideoPlayer
import com.example.clipcraft.utils.VideoPlayerPool
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.res.stringResource
import com.example.clipcraft.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewMainScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // States
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val selectedVideos by viewModel.selectedVideos.collectAsStateWithLifecycle()
    val userCommand by viewModel.userCommand.collectAsStateWithLifecycle()
    
    // Логирование для отладки голосового ввода
    LaunchedEffect(userCommand) {
        Log.d("NewMainScreen", "userCommand changed to: '$userCommand'")
    }
    val processingMessages by viewModel.processingChatMessages.collectAsStateWithLifecycle()
    val editingState by viewModel.editingState.collectAsStateWithLifecycle()
    val tutorialState by viewModel.tutorialState.collectAsStateWithLifecycle()
    val backgroundRenderingState by viewModel.backgroundRenderingState.collectAsStateWithLifecycle()

    // Local states
    var showMaxVideosDialog by remember { mutableStateOf(false) }
    var galleryVideos by remember { mutableStateOf<List<SelectedVideo>>(emptyList()) }
    var speechBubbleMessages by remember { mutableStateOf<List<SpeechBubbleMessage>>(emptyList()) }
    
    // Tutorial target bounds
    val targetBounds = remember { mutableStateMapOf<TutorialTarget, androidx.compose.ui.unit.IntRect>() }

    // Force video player refresh when returning from editor
    var videoPlayerRefreshKey by remember { mutableStateOf(0) }
    
    // Определяем, нужно ли показывать видеоплеер
    Log.d("clipcraftlogic", "showVideoPlayer check: processingState=${processingState.javaClass.simpleName}, editMode=${editingState.mode}, currentVideoPath=${editingState.currentVideoPath}")
    val showVideoPlayer = when {
        processingState is ProcessingState.Success -> true
        // При редактировании показываем видеоплеер только если процесс НЕ идет
        editingState.mode == ProcessingMode.EDIT && 
            editingState.currentVideoPath != null && 
            processingState !is ProcessingState.Processing -> true
        // При фоновом рендеринге показываем плеер если есть результат
        backgroundRenderingState.isRendering && 
            processingState is ProcessingState.Success -> true
        else -> false
    }
    
    // Refresh video player when processing state changes to success with new video path
    var lastVideoPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(processingState) {
        val state = processingState
        if (state is ProcessingState.Success) {
            val newPath = state.result
            if (newPath != lastVideoPath && newPath.isNotEmpty()) {
                Log.d("clipcraftlogic", "Processing success detected with new video path, refreshing video player")
                lastVideoPath = newPath
                videoPlayerRefreshKey++
            }
        }
    }
    
    // При успешном редактировании голосом открываем редактор
    // НЕ открываем редактор, если голосовое редактирование началось из самого редактора
    LaunchedEffect(processingState, editingState.mode, editingState.isVoiceEditingFromEditor) {
        if (processingState is ProcessingState.Success && 
            editingState.mode == ProcessingMode.EDIT &&
            !editingState.isVoiceEditingFromEditor) {
            Log.d("NewMainScreen", "Opening editor after voice editing")
            viewModel.navigateTo(MainViewModel.Screen.VideoEditor)
        }
    }

    // Логируем для отладки
    LaunchedEffect(showVideoPlayer, processingState) {
        Log.d("NewMainScreen", "showVideoPlayer: $showVideoPlayer, processingState: $processingState")
        Log.d("clipcraftlogic", "showVideoPlayer: $showVideoPlayer, processingState: ${processingState.javaClass.simpleName}")
        val state = processingState
        if (state is ProcessingState.Success) {
            Log.d("clipcraftlogic", "Success result path: ${state.result}")
        }
    }

    // Permissions
    val galleryPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionsState = rememberMultiplePermissionsState(galleryPermissions)

    // Voice launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val voiceResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        viewModel.handleVoiceResult(voiceResults?.firstOrNull() ?: "")
    }

    // Load gallery videos
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.loadGalleryVideos().collect { videos ->
                galleryVideos = videos
            }
        }
    }

    // Время последнего показанного сообщения
    var lastMessageTime by remember { mutableStateOf(0L) }
    var allVideosProcessed by remember { mutableStateOf(false) }
    var currentVideoProgress by remember { mutableStateOf(0) }
    var totalVideosCount by remember { mutableStateOf(0) }
    
    // Получаем строки заранее для использования в LaunchedEffect
    val videoReadyText = stringResource(R.string.speech_bubble_video_ready)
    val feedbackText = stringResource(R.string.speech_bubble_feedback)
    
    // Convert processing messages to speech bubbles
    LaunchedEffect(processingMessages) {
        val currentTime = System.currentTimeMillis()
        
        processingMessages.forEach { message ->
            val existingIds = speechBubbleMessages.map { it.text }
            if (!existingIds.contains(message)) {
                
                // Обработка прогресса видео (показываем ВСЕГДА)
                if (message.contains("🎬") && (message.contains(" of ") || message.contains(" из "))) {
                    val regex = """(\d+)\s*(?:of|из)\s*(\d+)""".toRegex()
                    val match = regex.find(message)
                    if (match != null) {
                        currentVideoProgress = match.groupValues[1].toIntOrNull() ?: 0
                        totalVideosCount = match.groupValues[2].toIntOrNull() ?: 0
                        
                        speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                            text = message,
                            type = MessageType.VIDEO_PROGRESS,
                            progress = if (totalVideosCount > 0) currentVideoProgress.toFloat() / totalVideosCount else 0f,
                            currentVideo = currentVideoProgress,
                            totalVideos = totalVideosCount
                        )
                    }
                    lastMessageTime = currentTime
                    return@forEach
                }
                
                // Транскрипция (показываем ВСЕГДА)
                if (message.contains("💬") && (message.contains("найдена речь") || message.contains("Speech found"))) {
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = message,
                        type = MessageType.TRANSCRIPTION
                    )
                    lastMessageTime = currentTime
                    return@forEach
                }
                
                // Все видео готовы
                if (message.contains("📊") && (message.contains("готовы") || message.contains("ready"))) {
                    allVideosProcessed = true
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = message,
                        type = MessageType.SYSTEM
                    )
                    lastMessageTime = currentTime
                    return@forEach
                }
                
                // План монтажа (показываем ВСЕГДА)
                if (message.contains("📋") || message.contains("План монтажа готов") || message.contains("plan ready")) {
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = message,
                        type = MessageType.PLAN
                    )
                    lastMessageTime = currentTime
                    return@forEach
                }
                
                // Видео готово
                if (message.contains("🎉") && (message.contains("готово") || message.contains("ready"))) {
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = videoReadyText,
                        type = MessageType.SUCCESS
                    )
                    lastMessageTime = currentTime
                    return@forEach
                }
                
                // Системные сообщения (показываем с ограничением по времени)
                if (currentTime - lastMessageTime < 5000) {
                    return@forEach
                }
                
                speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                    text = message,
                    type = MessageType.SYSTEM
                )
                lastMessageTime = currentTime
            }
        }
    }

    // Очищаем сообщения при успешном завершении или ошибке
    LaunchedEffect(processingState) {
        when (processingState) {
            is ProcessingState.Success -> {
                // Показываем сообщение о готовности
                if (!speechBubbleMessages.any { it.text.contains("готово", ignoreCase = true) }) {
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = videoReadyText,
                        type = MessageType.SUCCESS
                    )
                }
                // Даем время показать последнее сообщение
                delay(2000)
                speechBubbleMessages = emptyList()
            }
            is ProcessingState.Error -> {
                // Даем время показать последнее сообщение
                delay(1000)
                speechBubbleMessages = emptyList()
            }
            else -> {}
        }
    }

    // Добавляем случайный совет если нет сообщений
    LaunchedEffect(processingState) {
        if (processingState is ProcessingState.Processing && speechBubbleMessages.isEmpty()) {
            delay(2000)
            speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                text = getRandomTip(context),
                type = MessageType.TIP
            )
        }
    }

    // Добавляем сообщения прогресса при длительных паузах (только после обработки всех видео)
    LaunchedEffect(processingState, speechBubbleMessages, allVideosProcessed) {
        if (processingState is ProcessingState.Processing && speechBubbleMessages.isNotEmpty() && allVideosProcessed) {
            delay(10000) // Ждем 10 секунд
            val lastMessageTimeFromBubbles = speechBubbleMessages.lastOrNull()?.timestamp ?: 0
            val currentTime = System.currentTimeMillis()

            // Если прошло больше 10 секунд с последнего сообщения и все видео обработаны
            if (currentTime - lastMessageTimeFromBubbles > 10000) {
                // Подсчитываем количество сообщений прогресса
                val progressMessageCount = speechBubbleMessages.count { it.type == MessageType.PROGRESS }
                
                // Показываем предложение обратной связи после 3-го сообщения прогресса
                if (progressMessageCount == 3 && !speechBubbleMessages.any { it.type == MessageType.FEEDBACK }) {
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = feedbackText,
                        type = MessageType.FEEDBACK,
                        action = {
                            // Открываем форму обратной связи
                            val feedbackUrl = "https://forms.gle/gobUCNLg9M6cHVQr9"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(feedbackUrl)
                            }
                            context.startActivity(intent)
                        }
                    )
                } else {
                    speechBubbleMessages = speechBubbleMessages + SpeechBubbleMessage(
                        text = getRandomProgressMessage(context),
                        type = MessageType.PROGRESS
                    )
                }
            }
        }
    }

    // Отладка состояния редактирования
    LaunchedEffect(editingState) {
        Log.d("NewMainScreen", "=== EditingState changed ===")
        Log.d("NewMainScreen", "  mode: ${editingState.mode}")
        Log.d("NewMainScreen", "  originalCommand: ${editingState.originalCommand}")
        Log.d("NewMainScreen", "  editCommand: ${editingState.editCommand}")
        Log.d("NewMainScreen", "  previousPlan: ${editingState.previousPlan?.finalEdit?.size ?: 0} segments")
        Log.d("NewMainScreen", "  currentVideoPath: ${editingState.currentVideoPath}")
        Log.d("NewMainScreen", "  originalVideoAnalyses: ${editingState.originalVideoAnalyses?.size ?: 0} items")
    }

    // Отладка
    LaunchedEffect(processingState) {
        Log.d("NewMainScreen", "Current processing state: $processingState")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Верхний блок
            TopBar(
                user = currentUser,
                onProfileClick = { viewModel.navigateTo(MainViewModel.Screen.Profile) },
                onBoundsChanged = { target, bounds ->
                    targetBounds[target] = bounds
                }
            )

            // Основное содержимое
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val animatedTargetState = when {
                    showVideoPlayer -> "video"
                    processingState is ProcessingState.Processing -> "processing"
                    processingState is ProcessingState.Success -> "success"
                    processingState is ProcessingState.Error -> "error"
                    else -> "idle"
                }
                Log.d("clipcraftlogic", "AnimatedContent targetState: $animatedTargetState, showVideoPlayer: $showVideoPlayer")
                
                AnimatedContent(
                    targetState = animatedTargetState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    }
                ) { targetState ->
                    Log.d("NewMainScreen", "Target state changed to: $targetState")
                    Log.d("clipcraftlogic", "AnimatedContent rendering state: $targetState")
                    when (targetState) {
                        "video" -> {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Видеоплеер
                                val videoUri = when (val state = processingState) {
                                    is ProcessingState.Success -> {
                                        Log.d("NewMainScreen", "Using video from ProcessingState.Success: ${state.result}")
                                        Log.d("clipcraftlogic", "Final screen video path: ${state.result}")
                                        state.result
                                    }
                                    else -> {
                                        val path = editingState.currentVideoPath ?: ""
                                        Log.d("NewMainScreen", "Using video from editingState: $path")
                                        Log.d("clipcraftlogic", "Final screen video path (from editing): $path")
                                        path
                                    }
                                }
                                Log.d("NewMainScreen", "Showing video player for: $videoUri")
                                
                                // Check if file exists
                                val videoFile = if (videoUri.startsWith("content://")) {
                                    null // Can't check content URI easily
                                } else {
                                    java.io.File(videoUri)
                                }
                                if (videoFile != null) {
                                    Log.d("clipcraftlogic", "Video file exists: ${videoFile.exists()}, size: ${videoFile.length()}")
                                }
                                
                                val uri = if (videoUri.startsWith("content://")) {
                                    Uri.parse(videoUri)
                                } else {
                                    Uri.fromFile(java.io.File(videoUri))
                                }
                                Log.d("clipcraftlogic", "Created URI: $uri from path: $videoUri")
                                
                                // Use a unique key based on the video path and refresh counter to force recreation
                                val playerKey = "final_result_${videoUri.hashCode()}_$videoPlayerRefreshKey"
                                Log.d("clipcraftlogic", "Using player key: $playerKey")
                                
                                // Use stable key based only on video URI, not refresh counter
                                val stablePlayerKey = "final_result_${videoUri.hashCode()}"
                                Log.d("clipcraftlogic", "Using stable player key: $stablePlayerKey")
                                
                                // Force complete re-composition with key
                                key(stablePlayerKey) {
                                    // Use SimpleVideoPlayer for more reliable playback on final screen
                                    SimpleVideoPlayer(
                                        videoUri = uri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                
                                // Show rendering progress overlay if background rendering is active
                                Log.d("clipcraftlogic", "Background rendering state: isRendering=${backgroundRenderingState.isRendering}, progress=${backgroundRenderingState.progress}")
                                if (backgroundRenderingState.isRendering) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Card(
                                            modifier = Modifier.padding(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = stringResource(R.string.rendering_video),
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "${(backgroundRenderingState.progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                if (backgroundRenderingState.totalSegments > 0) {
                                                    Text(
                                                        text = stringResource(
                                                            R.string.rendering_segment_format,
                                                            backgroundRenderingState.currentSegment,
                                                            backgroundRenderingState.totalSegments
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "processing" -> {
                            // Speech bubbles
                            Log.d("NewMainScreen", "Showing processing state")
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                SpeechBubbleChat(
                                    messages = speechBubbleMessages,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        else -> {
                            // Галерея видео
                            if (permissionsState.allPermissionsGranted) {
                                // В режиме редактирования с видео показываем пустой экран
                                if (editingState.mode == ProcessingMode.EDIT && editingState.currentVideoPath != null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Режим редактирования",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Text(
                                                "Введите команду для изменения видео",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (selectedVideos.isNotEmpty()) {
                                                Card(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    )
                                                ) {
                                                    Text(
                                                        text = "✓ ${selectedVideos.size} видео готовы к редактированию",
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    TutorialTargetBounds(
                                        target = TutorialTarget.GALLERY,
                                        onBoundsChanged = { bounds ->
                                            targetBounds[TutorialTarget.GALLERY] = bounds
                                        }
                                    ) {
                                        InstagramStyleGallery(
                                            recentVideos = galleryVideos.take(9), // Последние 9 видео
                                            allVideos = galleryVideos, // Все видео
                                            selectedVideos = selectedVideos,
                                            onVideoToggle = { video ->
                                                // Ищем видео в списке выбранных
                                                val selectedVideo = selectedVideos.find { selected ->
                                                    selected.uri.toString() == video.uri.toString() ||
                                                            selected.fileName == video.fileName
                                                }

                                                Log.d("NewMainScreen", "Toggle video: uri=${video.uri}, fileName=${video.fileName}")
                                            Log.d("NewMainScreen", "Selected videos before toggle:")
                                            selectedVideos.forEach { v ->
                                                Log.d("NewMainScreen", "  - uri=${v.uri}, fileName=${v.fileName}")
                                            }

                                            if (selectedVideo != null) {
                                                // Удаляем по имени файла выбранного видео
                                                Log.d("NewMainScreen", "Removing video: ${selectedVideo.fileName}")
                                                viewModel.removeVideo(selectedVideo.fileName)
                                            } else {
                                                Log.d("NewMainScreen", "Adding video from uri: ${video.uri}")
                                                viewModel.addVideos(listOf(video.uri))
                                            }
                                        },
                                        onMaxVideosReached = { showMaxVideosDialog = true },
                                        isEditMode = editingState.mode == ProcessingMode.EDIT,
                                        onManualEdit = {
                                            // Открываем видеоредактор в режиме ручного редактирования
                                            viewModel.startManualEdit()
                                        },
                                        onTutorialBoundsChanged = { target, bounds ->
                                            targetBounds[target] = bounds
                                        }
                                    )
                                    }
                                }
                            } else {
                                // Запрос разрешений
                                PermissionRequestScreen(
                                    onRequestPermission = {
                                        permissionsState.launchMultiplePermissionRequest()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Нижняя панель
            BottomCommandPanel(
                processingState = processingState,
                selectedVideosCount = selectedVideos.size,
                userCommand = userCommand,
                isEditMode = editingState.mode == ProcessingMode.EDIT,
                showVideoControls = showVideoPlayer && processingState !is ProcessingState.Processing && editingState.mode != ProcessingMode.EDIT,
                processingMessages = processingMessages,
                onCommandChange = viewModel::updateCommand,
                onVoiceClick = { viewModel.startVoiceRecognition(voiceLauncher) },
                onProcessClick = {
                    Log.d("NewMainScreen", "Process button clicked")
                    Log.d("NewMainScreen", "  isEditMode: ${editingState.mode == ProcessingMode.EDIT}")
                    Log.d("NewMainScreen", "  userCommand: '$userCommand'")
                    Log.d("NewMainScreen", "  selectedVideos: ${selectedVideos.size}")

                    if (editingState.mode == ProcessingMode.EDIT) {
                        viewModel.startEditing(userCommand)
                    } else {
                        viewModel.processVideos()
                    }
                },
                onShareClick = { viewModel.shareGeneric(context) },
                onSaveClick = {
                    viewModel.saveToGallery(context)
                    coroutineScope.launch {
                        // Показываем сообщение "Сохранено"
                        // TODO: Добавить Snackbar
                    }
                },
                onEditClick = {
                    // Проверяем, есть ли выбранные видео перед переходом в режим редактирования
                    if (selectedVideos.isEmpty()) {
                        Log.e("NewMainScreen", "Cannot edit: no videos selected")
                        // Можно показать Toast или Snackbar
                        Toast.makeText(context, "Videos not found. Please select videos to edit.", Toast.LENGTH_LONG).show()
                    } else {
                        // Переходим в режим редактирования
                        speechBubbleMessages = emptyList()
                        viewModel.setEditMode(true)
                    }
                },
                onNewVideoClick = {
                    speechBubbleMessages = emptyList()
                    viewModel.createNewVideo()
                },
                onCancelEditClick = {
                    // Выход из режима редактирования
                    viewModel.setEditMode(false)
                    viewModel.resetProcessing()
                },
                onOpenVideoEditor = {
                    viewModel.openVideoEditor()
                },
                onBoundsChanged = { target, bounds ->
                    targetBounds[target] = bounds
                }
            )
        }
    }

    // Диалоги
    if (showMaxVideosDialog) {
        MaxVideosDialog(
            onDismiss = { showMaxVideosDialog = false }
        )
    }
    
    // Tutorial overlay
    if (tutorialState.isShowing) {
        viewModel.getCurrentTutorialStep()?.let { step ->
            TutorialOverlay(
                step = step,
                targetBounds = targetBounds,
                onDismiss = {
                    viewModel.nextTutorialStep()
                },
                currentStepIndex = tutorialState.currentStep,
                onSkip = {
                    viewModel.skipTutorial()
                }
            )
        }
    }
    
    // Освобождаем неиспользуемые плееры при выходе
    DisposableEffect(Unit) {
        onDispose {
            VideoPlayerPool.releaseUnusedPlayers()
        }
    }
}

@Composable
private fun TopBar(
    user: User?,
    onProfileClick: () -> Unit,
    onBoundsChanged: ((TutorialTarget, IntRect) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть
            Column {
                Text(
                    text = "ClipCraft",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (user != null) {
                    Text(
                        text = user.displayName ?: user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.main_credits_format, user.creditsRemaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Кнопка профиля
            if (onBoundsChanged != null) {
                TutorialTargetBounds(
                    target = TutorialTarget.PROFILE_BUTTON,
                    onBoundsChanged = { bounds -> onBoundsChanged(TutorialTarget.PROFILE_BUTTON, bounds) }
                ) {
                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.nav_profile),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Профиль",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomCommandPanel(
    processingState: ProcessingState,
    selectedVideosCount: Int,
    userCommand: String,
    isEditMode: Boolean,
    showVideoControls: Boolean,
    processingMessages: List<String>,
    onCommandChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onProcessClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: () -> Unit,
    onNewVideoClick: () -> Unit,
    onCancelEditClick: () -> Unit = {},
    onOpenVideoEditor: () -> Unit = {},
    onBoundsChanged: ((TutorialTarget, IntRect) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AnimatedContent(
            targetState = when {
                processingState is ProcessingState.Processing -> "processing"
                showVideoControls -> "controls"
                else -> "input"
            },
            transitionSpec = {
                slideInVertically { it } + fadeIn() togetherWith
                        slideOutVertically { -it } + fadeOut()
            }
        ) { state ->
            when (state) {
                "processing" -> {
                    // Показываем компактный progress bar
                    val currentStep = rememberProcessingStep(processingMessages)
                    ProcessingProgressBar(
                        currentStep = currentStep,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                "controls" -> {
                    // Кнопки управления готовым видео
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Основные кнопки действий
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Кнопка видеоредактора
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = onOpenVideoEditor,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Movie,
                                        contentDescription = stringResource(R.string.nav_video_editor),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    stringResource(R.string.main_editor_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Сохранить
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = onSaveClick,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_save))
                                }
                                Text(
                                    stringResource(R.string.action_save),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Поделиться
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (onBoundsChanged != null) {
                                    TutorialTargetBounds(
                                        target = TutorialTarget.SHARE_BUTTON,
                                        onBoundsChanged = { bounds -> onBoundsChanged(TutorialTarget.SHARE_BUTTON, bounds) }
                                    ) {
                                        IconButton(
                                            onClick = onShareClick,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = onShareClick,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                                    }
                                }
                                Text(
                                    stringResource(R.string.action_share),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Дополнительные кнопки
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Новое видео
                            if (onBoundsChanged != null) {
                                TutorialTargetBounds(
                                    target = TutorialTarget.NEW_VIDEO_BUTTON,
                                    onBoundsChanged = { bounds -> onBoundsChanged(TutorialTarget.NEW_VIDEO_BUTTON, bounds) }
                                ) {
                                    OutlinedButton(
                                        onClick = onNewVideoClick,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_new), fontSize = 14.sp)
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onNewVideoClick,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.action_new), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                "input" -> {
                    // Поле ввода команды
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Определяем состояние кнопки заранее
                        val isButtonEnabled = when {
                            isEditMode && userCommand.isNotBlank() -> {
                                Log.d("BottomCommandPanel", "Button enabled: Edit mode with command")
                                true
                            }
                            processingState is ProcessingState.Idle &&
                                    selectedVideosCount > 0 &&
                                    userCommand.isNotBlank() -> {
                                Log.d("BottomCommandPanel", "Button enabled: Normal mode with videos and command")
                                true
                            }
                            else -> {
                                Log.d("BottomCommandPanel", "Button disabled")
                                false
                            }
                        }
                        
                        // Кнопка отмены в режиме редактирования
                        if (isEditMode) {
                            IconButton(
                                onClick = onCancelEditClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.main_cancel_editing),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Поле ввода с авторасширением
                        val maxInputHeight = 150.dp // Максимальная высота поля ввода
                        val minInputHeight = 56.dp  // Минимальная высота
                        
                        // Обертка для текстового поля с учетом кнопки
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (onBoundsChanged != null) {
                                TutorialTargetBounds(
                                    target = TutorialTarget.INPUT_FIELD,
                                    onBoundsChanged = { bounds -> onBoundsChanged(TutorialTarget.INPUT_FIELD, bounds) }
                                ) {
                                    OutlinedTextField(
                                        value = userCommand,
                                        onValueChange = onCommandChange,
                                        placeholder = {
                                            Text(
                                                if (isEditMode)
                                                    "расскажи текстом или голосом что нужно изменить"
                                                else
                                                    stringResource(R.string.main_command_placeholder),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = minInputHeight, max = maxInputHeight),
                                    singleLine = false,
                                    maxLines = 5,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    trailingIcon = {
                                        // Микрофон всегда в trailingIcon
                                        TutorialTargetBounds(
                                            target = TutorialTarget.VOICE_BUTTON,
                                            onBoundsChanged = { bounds -> onBoundsChanged(TutorialTarget.VOICE_BUTTON, bounds) }
                                        ) {
                                            IconButton(
                                                onClick = onVoiceClick,
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Mic,
                                                    contentDescription = stringResource(R.string.main_voice_input),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                                }
                            } else {
                                OutlinedTextField(
                                    value = userCommand,
                                    onValueChange = onCommandChange,
                                    placeholder = {
                                        Text(
                                            if (isEditMode)
                                                "расскажи текстом или голосом что нужно изменить"
                                            else
                                                "Например: динамичный клип с мотоциклом",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = minInputHeight, max = maxInputHeight),
                                singleLine = false,
                                maxLines = 5,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    // Микрофон всегда в trailingIcon
                                    IconButton(
                                        onClick = onVoiceClick,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = stringResource(R.string.main_voice_input),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            )
                            }
                        }
                        
                        // Кнопка отправки всегда видна когда есть текст
                        Log.d("NewMainScreen", "Process button check: userCommand='$userCommand', isNotBlank=${userCommand.isNotBlank()}")
                        if (userCommand.isNotBlank()) {
                            if (onBoundsChanged != null) {
                                TutorialTargetBounds(
                                    target = TutorialTarget.PROCESS_BUTTON,
                                    onBoundsChanged = { bounds -> onBoundsChanged(TutorialTarget.PROCESS_BUTTON, bounds) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isButtonEnabled) 
                                                    MaterialTheme.colorScheme.primary 
                                                else 
                                                    MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable(enabled = isButtonEnabled) { onProcessClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = stringResource(R.string.main_start_processing),
                                            tint = if (isButtonEnabled) 
                                                MaterialTheme.colorScheme.onPrimary 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isButtonEnabled) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable(enabled = isButtonEnabled) { onProcessClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Начать обработку",
                                        tint = if (isButtonEnabled) 
                                            MaterialTheme.colorScheme.onPrimary 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Логирование для отладки
                        LaunchedEffect(isEditMode, userCommand, processingState, selectedVideosCount) {
                            Log.d("BottomCommandPanel", "Button state debug:")
                            Log.d("BottomCommandPanel", "  isEditMode: $isEditMode")
                            Log.d("BottomCommandPanel", "  userCommand: '${userCommand}'")
                            Log.d("BottomCommandPanel", "  processingState: ${processingState.javaClass.simpleName}")
                            Log.d("BottomCommandPanel", "  selectedVideosCount: $selectedVideosCount")
                            Log.d("BottomCommandPanel", "  isButtonEnabled: $isButtonEnabled")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Доступ к галерее",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Для выбора видео необходим доступ к галерее",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.action_grant_access))
        }
    }
}

@Composable
private fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onShareTo: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_share)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ShareOption("TikTok", Icons.Default.VideoLibrary) { onShareTo("tiktok") }
                ShareOption("Instagram", Icons.Default.PhotoCamera) { onShareTo("instagram") }
                ShareOption("Telegram", Icons.Default.Send) { onShareTo("telegram") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ShareOption(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall
        )
    }
}