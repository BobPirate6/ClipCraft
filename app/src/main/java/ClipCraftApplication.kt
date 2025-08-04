package com.example.clipcraft

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.content.Context
import com.example.clipcraft.utils.LocaleHelper
// import android.util.Log
// import com.example.clipcraft.security.AppSecurity

@HiltAndroidApp
class ClipCraftApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base, "en"))
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // TODO: Включить проверки безопасности перед альфа-тестированием
        // Закомментировано для локальной разработки
        /*
        // Проверяем безопасность приложения (только для release и alpha сборок)
        if (!com.example.clipcraft.BuildConfig.DEBUG) {
            // Печатаем текущий хэш сертификата (уберите в продакшене)
            AppSecurity.printCurrentCertificateHash(this)
            
            // Проверяем целостность приложения
            if (!AppSecurity.verifyAppIntegrity(this)) {
                Log.e("ClipCraftApplication", "App integrity verification failed!")
                // В продакшене можно закрыть приложение:
                // android.os.Process.killProcess(android.os.Process.myPid())
            }
            
            // Предупреждаем, если запущено на эмуляторе
            if (AppSecurity.isRunningOnEmulator()) {
                Log.w("ClipCraftApplication", "App is running on emulator!")
            }
        }
        */
        
        // WorkManager будет инициализирован автоматически через Configuration.Provider
    }

    // Реализуем Configuration.Provider - в Kotlin это свойство, а не функция
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}