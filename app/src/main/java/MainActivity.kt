package com.example.clipcraft

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clipcraft.models.AuthState
import com.example.clipcraft.models.ProcessingState
import com.example.clipcraft.models.User
import com.example.clipcraft.models.SubscriptionType
import com.example.clipcraft.ui.MainViewModel
import com.example.clipcraft.ui.theme.ClipCraftTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ClipCraftTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClipCraftApp()
                }
            }
        }
    }
}

@Composable
fun ClipCraftApp() {
    val viewModel: MainViewModel = hiltViewModel()
    val authState by viewModel.authState.collectAsState()

    when (val state = authState) {
        is AuthState.Loading -> {
            LoadingScreen()
        }

        is AuthState.Unauthenticated -> {
            AuthScreen(
                onGoogleSignIn = { idToken ->
                    viewModel.signInWithGoogle(idToken)
                },
                onEmailSignIn = { email, password ->
                    viewModel.signInWithEmail(email, password)
                },
                onCreateAccount = { email, password ->
                    viewModel.createAccount(email, password)
                }
            )
        }

        is AuthState.Authenticated -> {
            MainScreen(viewModel = viewModel)
        }

        is AuthState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { viewModel.signOut() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onGoogleSignIn: (String) -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let(onGoogleSignIn)
        } catch (e: ApiException) {
            Toast.makeText(context, "Ошибка входа через Google", Toast.LENGTH_SHORT).show()
        }
    }

    fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("446991119797-076rbbvmnskdu5mfmg36va0gm32e2kov.apps.googleusercontent.com") // Замените на ваш реальный Web Client ID
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "ClipCraft",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Создавайте потрясающие reels с ИИ",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = { startGoogleSignIn() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
                Text("Войти через Google")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "или",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSignUp) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Подтвердите пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        if (isSignUp) {
                            if (password == confirmPassword) {
                                onCreateAccount(email, password)
                            }
                        } else {
                            onEmailSignIn(email, password)
                        }
                    },
                    enabled = email.isNotBlank() && password.isNotBlank() &&
                            (!isSignUp || password == confirmPassword),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSignUp) "Создать аккаунт" else "Войти")
                }

                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        confirmPassword = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSignUp) "Уже есть аккаунт? Войти"
                        else "Нет аккаунта? Регистрация"
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val processingState by viewModel.processingState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val selectedVideos by viewModel.selectedVideos.collectAsState()
    val userCommand by viewModel.userCommand.collectAsState()
    val useLocalProcessing by viewModel.useLocalProcessing.collectAsState()

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val voiceResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val voiceCommand = voiceResults?.firstOrNull()
        if (voiceCommand != null) {
            viewModel.handleVoiceResult(voiceCommand)
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val paths = uris.mapNotNull { uri ->
            viewModel.uriToTempFile(context, uri)?.absolutePath
        }
        viewModel.addVideos(paths)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        TopBar(
            user = currentUser,
            onSignOut = viewModel::signOut,
            useLocalProcessing = useLocalProcessing,
            onToggleMode = viewModel::toggleProcessingMode
        )

        CommandInputSection(
            command = userCommand,
            onCommandChange = viewModel::updateCommand,
            onVoiceClick = { viewModel.startVoiceRecognition(voiceLauncher) },
            enabled = processingState is ProcessingState.Idle
        )

        MediaSelectionSection(
            selectedVideos = selectedVideos,
            onAddMedia = { mediaLauncher.launch("video/*") },
            onRemoveVideo = viewModel::removeVideo,
            enabled = processingState is ProcessingState.Idle
        )

        Button(
            onClick = { viewModel.processVideos(context) },
            enabled = selectedVideos.isNotEmpty() &&
                    userCommand.isNotBlank() &&
                    processingState is ProcessingState.Idle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Создать Reels")
        }

        ProcessingStatusSection(
            state = processingState,
            onReset = viewModel::resetProcessing
        )

        // Исправление smart cast проблемы
        val currentState = processingState
        if (currentState is ProcessingState.Success) {
            ResultSection(
                videoPath = currentState.result,
                onSaveToGallery = { viewModel.saveToGallery(context) },
                onShareToInstagram = { viewModel.shareToInstagram(context) },
                onShareToTikTok = { viewModel.shareToTikTok(context) },
                onShareGeneric = { viewModel.shareGeneric(context) }
            )
        }
    }
}

@Composable
fun TopBar(
    user: User?,
    onSignOut: () -> Unit,
    useLocalProcessing: Boolean,
    onToggleMode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ClipCraft",
                    style = MaterialTheme.typography.headlineSmall
                )
                user?.let { userInfo ->
                    Text(
                        text = userInfo.email,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = useLocalProcessing,
                        onCheckedChange = { onToggleMode() }
                    )
                    Text(
                        text = if (useLocalProcessing) "Локально" else "Сервер",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                IconButton(onClick = onSignOut) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Выйти")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandInputSection(
    command: String,
    onCommandChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Что хотите создать?",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                placeholder = { Text("Например: динамичный клип с мотоциклом") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onVoiceClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Голосом")
            }
        }
    }
}

@Composable
fun MediaSelectionSection(
    selectedVideos: List<String>,
    onAddMedia: () -> Unit,
    onRemoveVideo: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Видео (${selectedVideos.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = onAddMedia,
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }

            if (selectedVideos.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(selectedVideos) { videoPath ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = videoPath.substringAfterLast("/"),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            IconButton(
                                onClick = { onRemoveVideo(videoPath) },
                                enabled = enabled
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingStatusSection(
    state: ProcessingState,
    onReset: () -> Unit
) {
    when (state) {
        is ProcessingState.Idle -> { /* Ничего не показываем */ }

        is ProcessingState.Processing -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Обработка видео...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        is ProcessingState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ошибка: ${state.message}",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = onReset) {
                        Text("Попробовать снова")
                    }
                }
            }
        }

        is ProcessingState.Success -> { /* Обрабатывается в основном экране */ }
    }
}

@Composable
fun ResultSection(
    videoPath: String,
    onSaveToGallery: () -> Unit,
    onShareToInstagram: () -> Unit,
    onShareToTikTok: () -> Unit,
    onShareGeneric: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Видео готово! 🎉",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveToGallery,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сохранить")
                }

                Button(
                    onClick = onShareGeneric,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Поделиться")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareToInstagram,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📷 Instagram")
                }

                OutlinedButton(
                    onClick = onShareToTikTok,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎵 TikTok")
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Загрузка...")
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ошибка",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRetry) {
                Text("Попробовать снова")
            }
        }
    }
}