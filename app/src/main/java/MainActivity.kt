package com.example.clipcraft

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.clipcraft.ui.theme.ClipCraftTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

// Определение интерфейса API для Retrofit
interface Api {
    @Multipart // Указывает, что это multipart-запрос
    @POST("edit") // Указывает, что это POST-запрос на эндпоинт "/edit"
    fun edit(
        @Part("command") cmd: RequestBody, // Часть запроса для команды
        @Part files: List<MultipartBody.Part> // Теперь это список файлов!
    ): Call<EditResp> // Возвращает объект Call для выполнения запроса
}

// Data class для ответа от сервера
data class EditResp(val video_b64: String) // Содержит видео в формате Base64

class MainActivity : ComponentActivity() {

    // Состояние для хранения пользовательского запроса (текстового или голосового)
    private var userCommand by mutableStateOf("")
    // Состояние для отображения/скрытия индикатора загрузки
    private var isProcessing by mutableStateOf(false)

    // Инициализация контракта для выбора МНОЖЕСТВА медиафайлов
    // maxItems = 5 ограничивает количество видео, которые пользователь может выбрать.
    private val pickMultipleMedia = registerForActivityResult(PickMultipleVisualMedia(maxItems = 5)) { uris ->
        // Проверяем, были ли выбраны медиафайлы
        if (uris.isNotEmpty()) {
            Log.d("ClipCraft", "Selected URIs: $uris") // Логируем выбранные URI
            // Если есть выбранные URI и пользовательский запрос не пуст, отправляем на сервер
            if (userCommand.isNotBlank()) {
                sendToServer(uris, userCommand)
            } else {
                // Если запрос пуст, показываем Toast и не отправляем
                Toast.makeText(this, "Пожалуйста, введите или произнесите ваш запрос.", Toast.LENGTH_LONG).show()
            }
        } else {
            // Если выбор отменен или не выбрано ни одного файла
            Toast.makeText(this, "Выбор медиа отменен или не выбрано ни одного файла", Toast.LENGTH_SHORT).show()
            Log.d("ClipCraft", "No media selected or selection cancelled")
        }
    }

    // Для голосового ввода: контракт для запуска системного распознавания речи
    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        // Проверяем результат активности распознавания речи
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Извлекаем распознанный текст
            val spokenText: String? = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
                results[0] // Берем первый (наиболее вероятный) результат
            }
            spokenText?.let {
                // Обновляем состояние userCommand распознанным текстом
                userCommand = it
                Toast.makeText(this, "Распознано: \"$it\"", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Если распознавание не удалось или было отменено
            Toast.makeText(this, "Не удалось распознать речь или отменено.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Оборачиваем содержимое в вашу пользовательскую тему ClipCraftTheme.
            // Убедитесь, что ClipCraftTheme, Color.kt и Typography.kt
            // правильно определены в папке ui.theme.
            ClipCraftTheme {
                // Surface - это контейнер Material Design, который обычно используется как фон экрана.
                Surface(
                    modifier = Modifier.fillMaxSize(), // Заставляет Surface занимать всю доступную площадь экрана
                    color = MaterialTheme.colorScheme.background // Устанавливает фоновый цвет из вашей темы
                ) {
                    // Column используется для вертикального расположения элементов UI.
                    Column(
                        modifier = Modifier
                            .fillMaxSize() // Занимает всю доступную площадь
                            .padding(16.dp), // Добавляет отступы со всех сторон
                        horizontalAlignment = Alignment.CenterHorizontally, // Выравнивает элементы по горизонтали по центру
                        verticalArrangement = Arrangement.Center // Выравнивает элементы по вертикали по центру
                    ) {
                        // Поле для текстового ввода пользовательского запроса
                        TextField(
                            value = userCommand, // Текущее значение из состояния
                            onValueChange = { userCommand = it }, // Обновление состояния при изменении текста
                            label = { Text("Ваш запрос для монтажа (текст)") }, // Подсказка для пользователя
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), // Занимает всю ширину, отступ снизу
                            singleLine = true // Ограничивает ввод одной строкой
                        )

                        // Кнопка для активации голосового ввода
                        Button(
                            onClick = { startSpeechToText() }, // При нажатии запускаем голосовой ввод
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp) // Занимает всю ширину, отступ снизу
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Голосовой ввод") // Иконка микрофона
                            Text(" Голосовой ввод") // Текст на кнопке
                        }

                        // Кнопка для выбора медиафайлов и отправки на сервер
                        Button(
                            onClick = {
                                // Проверяем, что пользовательский запрос не пуст перед выбором медиа
                                if (userCommand.isNotBlank()) {
                                    // Запускаем выбор медиафайлов (изображений и видео)
                                    pickMultipleMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                                } else {
                                    // Если запрос пуст, показываем Toast
                                    Toast.makeText(this@MainActivity, "Пожалуйста, введите или произнесите ваш запрос.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth() // Занимает всю ширину
                        ) {
                            Text("Выбрать медиа и отправить") // Текст на кнопке
                        }

                        // Индикатор загрузки (отображается, когда isProcessing = true)
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp)) // Круговой индикатор
                            Text("Обработка видео на сервере...", modifier = Modifier.padding(top = 8.dp)) // Текст состояния
                        }
                    }
                }
            }
        }
    }

    // Функция для запуска системного распознавания речи
    private fun startSpeechToText() {
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Используем свободно-форменную модель языка
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Устанавливаем русский язык для распознавания речи
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toString())
            // Подсказка для пользователя
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Произнесите ваш запрос для монтажа")
        }
        try {
            // Запускаем активность распознавания речи
            speechRecognizerLauncher.launch(speechRecognizerIntent)
        } catch (e: Exception) {
            // Обработка ошибок, если устройство не поддерживает голосовой ввод
            Toast.makeText(this, "Ваше устройство не поддерживает голосовой ввод.", Toast.LENGTH_LONG).show()
            Log.e("ClipCraft", "Speech recognition error: ${e.message}")
        }
    }

    // Функция для отправки выбранных медиафайлов и команды на сервер
    private fun sendToServer(uris: List<Uri>, command: String) {
        // Проверки на пустые данные
        if (uris.isEmpty()) {
            Toast.makeText(this, "Не выбрано ни одного медиафайла", Toast.LENGTH_LONG).show()
            return
        }
        if (command.isBlank()) {
            Toast.makeText(this, "Запрос для монтажа не может быть пустым.", Toast.LENGTH_LONG).show()
            return
        }

        isProcessing = true // Начинаем показ индикатора загрузки

        val multipartFiles = mutableListOf<MultipartBody.Part>()
        val tempFiles = mutableListOf<File>() // Список для хранения временных файлов для последующего удаления

        // Итерируем по каждому выбранному Uri, создаем временный файл и MultipartBody.Part
        for (uri in uris) {
            val file = uriToTempFile(uri)
            if (file == null) {
                Toast.makeText(this, "Не удалось создать временный файл для одного из видео", Toast.LENGTH_LONG).show()
                tempFiles.forEach { it.delete() } // Удаляем уже созданные временные файлы
                isProcessing = false // Скрываем индикатор
                return
            }
            tempFiles.add(file) // Добавляем временный файл в список

            val requestFile = file.asRequestBody("video/mp4".toMediaType()) // Создаем RequestBody из файла
            // Создаем MultipartBody.Part с именем поля "files" и именем файла
            multipartFiles.add(MultipartBody.Part.createFormData("files", file.name, requestFile))
        }

        // Создаем RequestBody для команды (пользовательского запроса)
        val cmd = command.toRequestBody("text/plain".toMediaType())

        // Инициализируем Retrofit клиент
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.0.8:8000/") // Базовый URL вашего сервера (убедитесь, что он правильный)
            .addConverterFactory(GsonConverterFactory.create()) // Добавляем конвертер JSON (Gson)
            .build()

        // Создаем экземпляр API интерфейса
        val api = retrofit.create(Api::class.java)

        // Выполняем асинхронный запрос к серверу
        api.edit(cmd, multipartFiles).enqueue(object : Callback<EditResp> {
            // Обработка успешного ответа от сервера
            override fun onResponse(c: Call<EditResp>, r: Response<EditResp>) {
                isProcessing = false // Скрываем индикатор после получения ответа
                if (r.isSuccessful) { // Проверяем успешность HTTP-ответа (коды 2xx)
                    val responseBody = r.body()
                    if (responseBody != null) { // Проверяем, что тело ответа не null
                        try {
                            // Декодируем видео из Base64 строки
                            val bytes = Base64.decode(responseBody.video_b64, Base64.DEFAULT)
                            // Сохраняем видео в галерею устройства
                            val savedUri = saveToGallery(bytes)
                            if (savedUri != null) {
                                Toast.makeText(this@MainActivity, "Сохранено: $savedUri", Toast.LENGTH_LONG).show()
                                Log.d("ClipCraft", "Video saved to: $savedUri")
                            } else {
                                Toast.makeText(this@MainActivity, "Не удалось сохранить видео", Toast.LENGTH_LONG).show()
                                Log.e("ClipCraft", "Failed to save video to gallery.")
                            }
                        } catch (e: IllegalArgumentException) {
                            Toast.makeText(this@MainActivity, "Ошибка декодирования Base64: ${e.message}", Toast.LENGTH_LONG).show()
                            Log.e("ClipCraft", "Base64 decoding error: ${e.message}")
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: Пустой ответ от сервера", Toast.LENGTH_LONG).show()
                        Log.e("ClipCraft", "Server returned empty body.")
                    }
                } else {
                    // Обработка неуспешного ответа (например, 404, 500 и т.д.)
                    val errorBody = r.errorBody()?.string() // Получаем тело ошибки, если есть
                    Toast.makeText(this@MainActivity, "Ошибка сервера: ${r.code()} - $errorBody", Toast.LENGTH_LONG).show()
                    Log.e("ClipCraft", "Server error: ${r.code()} - $errorBody")
                }
                tempFiles.forEach { it.delete() } // Удаляем все временные файлы после завершения запроса
            }

            // Обработка ошибки сети или других сбоев (например, нет интернета, таймаут)
            override fun onFailure(c: Call<EditResp>, t: Throwable) {
                isProcessing = false // Скрываем индикатор при ошибке
                Toast.makeText(this@MainActivity, "Ошибка сети: ${t.message}", Toast.LENGTH_LONG).show()
                Log.e("ClipCraft", "Network error: ${t.message}", t) // Логируем ошибку сети
                tempFiles.forEach { it.delete() } // Удаляем все временные файлы в случае ошибки
            }
        })
    }

    // Вспомогательная функция для преобразования Uri в временный файл
    private fun uriToTempFile(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri) // Открываем поток для чтения из Uri
            // Создаем временный файл в кэше приложения
            val tempFile = File(cacheDir, "temp_media_${System.currentTimeMillis()}.mp4")
            inputStream?.use { input -> // Используем use для автоматического закрытия потока
                FileOutputStream(tempFile).use { output -> // Используем use для автоматического закрытия потока
                    input.copyTo(output) // Копируем данные из входного потока в выходной
                }
            }
            tempFile // Возвращаем созданный временный файл
        } catch (e: Exception) {
            Log.e("ClipCraft", "Error converting URI to temp file: ${e.message}", e) // Логируем ошибку
            null // Возвращаем null в случае ошибки
        }
    }

    // Вспомогательная функция для сохранения байтов видео в галерею
    private fun saveToGallery(bytes: ByteArray): Uri? {
        val fileName = "ClipCraft_Video_${System.currentTimeMillis()}.mp4" // Генерируем уникальное имя файла
        val mimeType = "video/mp4" // Указываем MIME-тип видео

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName) // Имя файла для отображения
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType) // MIME-тип файла
            // Для Android Q (API 29) и выше, используем MediaStore.VOLUME_EXTERNAL_PRIMARY
            // Для более старых версий, используем Environment.DIRECTORY_MOVIES
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Указываем относительный путь для Android Q+
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + File.separator + "ClipCraft")
            } else {
                // Для старых версий создаем директорию вручную и указываем полный путь
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ClipCraft")
                if (!directory.exists()) {
                    directory.mkdirs() // Создаем директории, если они не существуют
                }
                put(MediaStore.MediaColumns.DATA, File(directory, fileName).absolutePath) // Полный путь к файлу
            }
        }

        var uri: Uri? = null
        try {
            val resolver = contentResolver
            // Вставляем новую запись в MediaStore и получаем Uri для файла
            uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                // Открываем OutputStream для записи данных в полученный Uri
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(bytes) // Записываем байты видео
                    outputStream.flush() // Принудительно записываем все буферизованные данные
                }
                Log.d("ClipCraft", "Video saved to gallery at $uri") // Логируем успешное сохранение
            }
            return uri // Возвращаем Uri сохраненного файла
        } catch (e: Exception) {
            Log.e("ClipCraft", "Error saving video to gallery: ${e.message}", e) // Логируем ошибку
            // Если произошла ошибка и Uri был создан, пытаемся его удалить, чтобы избежать "мертвых" записей
            if (uri != null) {
                contentResolver.delete(uri, null, null)
            }
            return null // Возвращаем null в случае ошибки
        }
    }
}
