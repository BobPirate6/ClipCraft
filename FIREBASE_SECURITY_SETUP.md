# Настройка безопасности Firebase для ClipCraft

## 1. Ограничение API ключей в Firebase Console

### Шаг 1: Получение SHA-1 и SHA-256 отпечатков

**Для debug сертификата:**
```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# macOS/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

**Для release сертификата:**
```bash
keytool -list -v -keystore your-release-key.jks -alias your-key-alias
```

Вы увидите вывод вида:
```
Certificate fingerprints:
    SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
    SHA256: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
```

### Шаг 2: Добавление отпечатков в Firebase

1. Откройте [Firebase Console](https://console.firebase.google.com)
2. Выберите проект ClipCraft
3. Перейдите в **Project Settings** (значок шестеренки)
4. Вкладка **General** → найдите ваше Android приложение
5. В разделе **SHA certificate fingerprints** нажмите **Add fingerprint**
6. Добавьте SHA-1 и SHA-256 отпечатки для:
   - Debug сертификата (для разработки)
   - Release сертификата (для продакшн)
   - Сертификата Google Play App Signing (после публикации)

### Шаг 3: Ограничение API ключей

1. Откройте [Google Cloud Console](https://console.cloud.google.com)
2. Выберите проект Firebase
3. Перейдите в **APIs & Services** → **Credentials**
4. Найдите ключи API:
   - **Android key (auto created by Firebase)**
   - **Web API key**

#### Для Android key:

1. Нажмите на ключ
2. В разделе **Application restrictions** выберите **Android apps**
3. Нажмите **Add an item** и укажите:
   - Package name: `com.example.clipcraft` (для релиза)
   - Package name: `com.example.clipcraft.alpha` (для альфа)
   - SHA-1 fingerprint: добавьте все SHA-1 из шага 1
4. В разделе **API restrictions**:
   - Выберите **Restrict key**
   - Отметьте только необходимые API:
     - Firebase Authentication API
     - Firebase Realtime Database API
     - Cloud Firestore API
     - Firebase Cloud Messaging API

#### Для Web API key (используется для OAuth):

1. В разделе **Application restrictions** выберите **HTTP referrers**
2. Добавьте домены:
   - `https://clipcraft-holy-water-8099.fly.dev/*`
   - `https://loud-whisper.fly.dev/*`
3. API restrictions - те же, что и для Android

### Шаг 4: Обновление google-services.json

После настройки:
1. Скачайте обновленный `google-services.json`
2. Поместите в `app/` директорию
3. НЕ коммитьте этот файл в git!

## 2. Правила безопасности Firestore/Realtime Database

### Firestore Rules:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Пользователи могут читать/писать только свои данные
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // История обработок доступна только владельцу
    match /processing_history/{historyId} {
      allow read, write: if request.auth != null 
        && request.auth.uid == resource.data.userId;
    }
  }
}
```

### Realtime Database Rules:
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

## 3. Создание подписанного APK

### Шаг 1: Создание ключа (только один раз)
```bash
keytool -genkey -v -keystore clipcraft-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias clipcraft
```

### Шаг 2: Настройка подписи в build.gradle

Создайте файл `keystore.properties` в корне проекта:
```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=clipcraft
storeFile=../clipcraft-release.jks
```

Добавьте в `app/build.gradle.kts`:
```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... остальные настройки
        }
    }
}
```

### Шаг 3: Сборка APK
```bash
# Для альфа версии
./gradlew assembleAlpha

# Для релизной версии
./gradlew assembleRelease

# Или App Bundle для Google Play
./gradlew bundleRelease
```

## 4. Проверочный чек-лист

- [ ] SHA-1 и SHA-256 отпечатки добавлены в Firebase
- [ ] API ключи ограничены по package name и SHA-1
- [ ] google-services.json обновлен и НЕ в git
- [ ] Правила безопасности Firestore/Database настроены
- [ ] Release keystore создан и сохранен в безопасном месте
- [ ] keystore.properties добавлен в .gitignore
- [ ] ProGuard правила настроены
- [ ] Network security config использует только HTTPS для продакшн

## 5. Мониторинг безопасности

В Firebase Console регулярно проверяйте:
- **Authentication** → вкладка **Users** - подозрительные регистрации
- **Cloud Firestore** → вкладка **Usage** - аномальное использование
- **Project settings** → **Usage and billing** - превышение квот

## Важные замечания

1. **НИКОГДА** не коммитьте в git:
   - google-services.json
   - Файлы keystore (.jks, .keystore)
   - keystore.properties
   - Любые файлы с паролями/ключами

2. **Резервное копирование**:
   - Сохраните keystore в нескольких безопасных местах
   - Запишите пароли в менеджер паролей
   - Без keystore вы не сможете обновлять приложение!

3. **Google Play App Signing**:
   - Рекомендуется включить для дополнительной безопасности
   - Google будет хранить ваш ключ подписи
   - Вы сможете сбросить ключ в случае компрометации