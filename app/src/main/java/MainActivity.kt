package com.example.clipcraft

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.window.Dialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clipcraft.models.*
import com.example.clipcraft.ui.MainViewModel
import com.example.clipcraft.ui.screens.NewMainScreen
import com.example.clipcraft.ui.screens.ProfileScreen
import com.example.clipcraft.ui.screens.IntroScreen
import com.example.clipcraft.screens.VideoEditorScreen
import com.example.clipcraft.models.ProcessingState
import com.example.clipcraft.ui.theme.ClipCraftTheme
import com.example.clipcraft.components.FeedbackDialog
import com.example.clipcraft.components.NoCreditsDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.clipcraft.security.SecurityConfig

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Проверка целостности приложения (только в релизных сборках)
        if (!BuildConfig.DEBUG) {
            // Проверка на root (опционально, можно показать предупреждение)
            if (SecurityConfig.isDeviceRooted()) {
                Log.w("MainActivity", "Device appears to be rooted")
                // Можно показать предупреждение пользователю, но не блокировать работу
            }
            
            // TODO: Добавьте проверку подписи после создания релизного сертификата
            // if (!SecurityConfig.verifyAppSignature(this)) {
            //     Log.e("MainActivity", "App signature verification failed")
            //     Toast.makeText(this, "Приложение было модифицировано", Toast.LENGTH_LONG).show()
            //     finish()
            //     return
            // }
        }
        
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
    val currentScreen by viewModel.currentScreen.collectAsState()
    val showFeedbackDialog by viewModel.showFeedbackDialog.collectAsState()
    val showNoCreditsDialog by viewModel.showNoCreditsDialog.collectAsState()
    val context = LocalContext.current

    // Google Sign In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "Google sign in result received, resultCode: ${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d("MainActivity", "Google account retrieved successfully, email: ${account.email}")
            account.idToken?.let { 
                Log.d("MainActivity", "ID token present, initiating sign in")
                viewModel.signInWithGoogle(it) 
            } ?: Log.e("MainActivity", "ID token is null")
        } catch (e: ApiException) {
            Log.e("MainActivity", "Google sign in failed with code: ${e.statusCode}", e)
            Toast.makeText(context, "Ошибка входа через Google: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    fun startGoogleSignIn() {
        Log.d("MainActivity", "Starting Google sign in")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d("MainActivity", "Launching Google sign in intent")
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    // Логирование изменения экрана
    LaunchedEffect(currentScreen) {
        Log.d("videoeditorclipcraft", "MainActivity: Screen changed to $currentScreen")
    }
    
    // Проверяем кредиты при запуске
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            viewModel.checkCreditsAndShowDialog()
        }
    }
    
    // Показываем диалоги
    if (showFeedbackDialog) {
        FeedbackDialog(
            title = "Поделитесь впечатлениями",
            message = "Вы создали уже 3 видео! Как вам наше приложение? Ваш отзыв поможет нам стать лучше.",
            onDismiss = { viewModel.dismissFeedbackDialog() },
            onFillForm = { viewModel.dismissFeedbackDialog() }
        )
    }
    
    if (showNoCreditsDialog) {
        NoCreditsDialog(
            onDismiss = { viewModel.dismissNoCreditsDialog() },
            onFillForm = { viewModel.dismissNoCreditsDialog() }
        )
    }
    
    when (currentScreen) {
        MainViewModel.Screen.Intro -> {
            IntroScreen(
                authState = authState,
                onStartClick = { viewModel.navigateTo(MainViewModel.Screen.Main) },
                onGoogleSignIn = { startGoogleSignIn() },
                onEmailSignIn = { email, password -> viewModel.signInWithEmail(email, password) },
                onEmailSignUp = { email, password -> viewModel.createAccount(email, password) },
                onPasswordReset = { email -> viewModel.sendPasswordResetEmail(email) }
            )
        }
        MainViewModel.Screen.Main -> {
            when (authState) {
                is AuthState.Authenticated -> NewMainScreen(viewModel = viewModel)
                else -> {
                    // Если пользователь не авторизован, возвращаем на Intro
                    LaunchedEffect(Unit) {
                        viewModel.navigateTo(MainViewModel.Screen.Intro)
                    }
                }
            }
        }
        MainViewModel.Screen.Profile -> {
            when (authState) {
                is AuthState.Authenticated -> {
                    val user by viewModel.currentUser.collectAsState()
                    val history by viewModel.editHistory.collectAsState()
                    val tutorialState by viewModel.tutorialState.collectAsState()
                    ProfileScreen(
                        user = user,
                        editHistory = history,
                        tutorialEnabled = tutorialState.isEnabled,
                        onNavigateBack = { viewModel.navigateTo(MainViewModel.Screen.Main) },
                        onUpdateName = { newName -> viewModel.updateUserName(newName) },
                        onLoadHistoryItem = { historyItem ->
                            viewModel.loadHistoryItemForEdit(historyItem)
                            viewModel.navigateTo(MainViewModel.Screen.Main)
                        },
                        onDeleteAccount = { viewModel.deleteAccount() },
                        onSignOut = { viewModel.signOut() },
                        onTutorialEnabledChange = { enabled -> viewModel.setTutorialEnabled(enabled) },
                        onStartTutorial = {
                            viewModel.startTutorial()
                            viewModel.navigateTo(MainViewModel.Screen.Main)
                        }
                    )
                }
                else -> {
                    // Если пользователь не авторизован, возвращаем на Intro
                    LaunchedEffect(Unit) {
                        viewModel.navigateTo(MainViewModel.Screen.Intro)
                    }
                }
            }
        }
        MainViewModel.Screen.VideoEditor -> {
            when (authState) {
                is AuthState.Authenticated -> {
                    val processingState by viewModel.processingState.collectAsState()
                    Log.d("videoeditorclipcraft", "VideoEditor screen: processingState = ${processingState.javaClass.simpleName}")
                    when (val state = processingState) {
                        is ProcessingState.Success -> {
                            Log.d("videoeditorclipcraft", "VideoEditor: Success state with editPlan=${state.editPlan != null}")
                            if (state.editPlan != null && state.videoAnalyses != null) {
                                val selectedVideos by viewModel.selectedVideos.collectAsState()
                                VideoEditorScreen(
                                    editPlan = state.editPlan,
                                    videoAnalyses = state.videoAnalyses,
                                    selectedVideos = selectedVideos,
                                    onSave = { tempVideoPath, updatedEditPlan ->
                                        // Заменяем текущее видео отредактированным
                                        viewModel.replaceCurrentVideoWithEdited(tempVideoPath, updatedEditPlan)
                                        viewModel.navigateTo(MainViewModel.Screen.Main)
                                    },
                                    onShare = { path ->
                                        viewModel.shareGeneric(context)
                                    },
                                    onEditWithVoice = {
                                        viewModel.setEditMode(true)
                                        // Проверяем, есть ли сохраненная команда
                                        val sharedPrefs = context.getSharedPreferences("clipcraft_prefs", Context.MODE_PRIVATE)
                                        val pendingCommand = sharedPrefs.getString("pending_voice_command", null)
                                        if (!pendingCommand.isNullOrEmpty()) {
                                            viewModel.updateCommand(pendingCommand)
                                            sharedPrefs.edit().remove("pending_voice_command").apply()
                                        }
                                        viewModel.navigateTo(MainViewModel.Screen.Main)
                                    },
                                    onCreateNew = {
                                        Log.d("videoeditorclipcraft", "VideoEditor: onCreateNew clicked")
                                        viewModel.createNewVideo()
                                        viewModel.navigateTo(MainViewModel.Screen.Main)
                                    },
                                    onExit = {
                                        viewModel.navigateTo(MainViewModel.Screen.Main)
                                    },
                                    mainViewModel = viewModel
                                )
                            } else {
                                // Если нет плана редактирования, возвращаемся на главный экран
                                Log.e("videoeditorclipcraft", "VideoEditor: No edit plan or analyses, returning to Main")
                                LaunchedEffect(Unit) {
                                    viewModel.navigateTo(MainViewModel.Screen.Main)
                                }
                            }
                        }
                        else -> {
                            // Если видео не готово, возвращаемся на главный экран
                            Log.e("videoeditorclipcraft", "VideoEditor: ProcessingState is not Success (${processingState.javaClass.simpleName}), returning to Main")
                            LaunchedEffect(Unit) {
                                viewModel.navigateTo(MainViewModel.Screen.Main)
                            }
                        }
                    }
                }
                else -> {
                    // Если пользователь не авторизован, возвращаем на Intro
                    LaunchedEffect(Unit) {
                        viewModel.navigateTo(MainViewModel.Screen.Intro)
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Загрузка...")
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ошибка", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Попробовать снова")
            }
        }
    }
}