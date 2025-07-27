# Руководство по безопасности для альфа-тестирования ClipCraft

## Что уже сделано для защиты приложения:

### 1. **Обфускация кода (ProGuard/R8)**
- Включена минификация и обфускация для alpha и release сборок
- Удаляются все отладочные логи
- Код становится нечитаемым после декомпиляции

### 2. **Проверка подписи приложения**
- Добавлен класс `AppSecurity` для проверки сертификата
- Приложение проверяет свою подпись при запуске
- Обнаружение запуска на эмуляторе

### 3. **Безопасное хранение данных**
- Создан `SecureStorage` с использованием EncryptedSharedPreferences
- Критические данные шифруются через Android Keystore

### 4. **Защита API**
- URL серверов вынесены в отдельный файл Config.kt
- Firebase конфигурация защищена сертификатом приложения

## Что нужно сделать перед отправкой тестерам:

### 1. **Создайте ключ для подписи альфа-версии**
```bash
keytool -genkey -v -keystore alpha-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias alpha-key
```

### 2. **Настройте подпись в gradle**
Добавьте в `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("alpha") {
            storeFile = file("../alpha-release-key.jks")
            storePassword = "your-store-password"
            keyAlias = "alpha-key"
            keyPassword = "your-key-password"
        }
    }
    
    buildTypes {
        getByName("alpha") {
            signingConfig = signingConfigs.getByName("alpha")
        }
    }
}
```

### 3. **Получите SHA256 хэш сертификата**
1. Соберите и запустите alpha версию один раз
2. В логах найдите строку: `CURRENT CERTIFICATE SHA256: ...`
3. Скопируйте этот хэш

### 4. **Обновите AppSecurity.kt**
Замените `YOUR_ALPHA_CERTIFICATE_SHA256_HERE` на полученный хэш

### 5. **Настройте Firebase**
- Добавьте SHA1 и SHA256 отпечатки вашего alpha сертификата в Firebase Console
- Скачайте обновленный google-services.json

### 6. **Проверьте безопасность API**
- Убедитесь, что ваши серверные API требуют аутентификации
- Рассмотрите добавление API ключей с ограничениями

## Сборка альфа-версии:

```bash
# Windows
build_alpha.bat

# Linux/Mac
./gradlew clean assembleAlpha
```

## Дополнительные меры безопасности:

### 1. **Ограничьте круг тестеров**
- Отправляйте APK только доверенным людям
- Ведите список тестеров

### 2. **Добавьте срок действия**
```kotlin
// В MainActivity или Application
private fun checkExpiration() {
    val expirationDate = Calendar.getInstance().apply {
        set(2024, Calendar.MARCH, 1) // Срок до 1 марта 2024
    }
    
    if (Calendar.getInstance().after(expirationDate)) {
        Toast.makeText(this, "Альфа-версия истекла", Toast.LENGTH_LONG).show()
        finish()
    }
}
```

### 3. **Логирование использования**
- Добавьте Firebase Analytics для отслеживания использования
- Логируйте критические действия

### 4. **Водяные знаки**
Для альфа-версии можно добавить водяной знак:
```kotlin
// В основном экране
if (BuildConfig.BUILD_TYPE == "alpha") {
    Text(
        "ALPHA BUILD - ${BuildConfig.VERSION_NAME}",
        modifier = Modifier.align(Alignment.TopEnd),
        color = Color.Red.copy(alpha = 0.5f)
    )
}
```

## После альфа-тестирования:

1. **Смените все ключи и сертификаты** для production версии
2. **Обновите URL серверов** если использовали тестовые
3. **Удалите отладочную информацию** из AppSecurity
4. **Включите строгие проверки безопасности**

## Контрольный список безопасности:

- [ ] Создан отдельный ключ для альфа-подписи
- [ ] Обфускация включена и работает
- [ ] SHA256 сертификата добавлен в AppSecurity
- [ ] Firebase настроен с новыми отпечатками
- [ ] API серверы защищены аутентификацией
- [ ] Нет хардкода паролей или секретных ключей
- [ ] Логи отключены в альфа-сборке
- [ ] APK протестирован на реальном устройстве

## Важные файлы:
- `/app/src/main/java/com/example/clipcraft/security/AppSecurity.kt` - проверка подписи
- `/app/src/main/java/com/example/clipcraft/security/SecureStorage.kt` - безопасное хранилище
- `/app/src/main/java/com/example/clipcraft/config/Config.kt` - конфигурация API
- `/app/proguard-rules.pro` - правила обфускации
- `/build_alpha.bat` - скрипт сборки