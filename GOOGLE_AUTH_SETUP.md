# Настройка Google Authentication для ClipCraft

## Уже выполнено в проекте:
✅ Добавлен google-services plugin в build.gradle
✅ Добавлена зависимость play-services-auth
✅ Файл google-services.json присутствует в папке app/
✅ Реализован код для Google Sign In

## Что нужно сделать:

### 1. Получить SHA-1 fingerprint

Выполните один из следующих способов:

#### Способ 1: Используя созданный скрипт
```bash
# Windows Command Prompt
get_sha1_fingerprint.bat

# или PowerShell
.\get_sha1_fingerprint.ps1
```

#### Способ 2: Через Android Studio
1. Откройте Android Studio
2. Справа найдите вкладку "Gradle" 
3. Раскройте: app → Tasks → android → signingReport
4. Дважды кликните на signingReport
5. В консоли появятся SHA-1 fingerprints

#### Способ 3: Через терминал
```bash
cd C:\Users\neznamov-a\AndroidStudioProjects\ClipCraft
.\gradlew signingReport
```

### 2. Добавить SHA-1 в Firebase Console

1. Перейдите в [Firebase Console](https://console.firebase.google.com)
2. Выберите ваш проект ClipCraft
3. Нажмите на иконку шестеренки → Project Settings
4. Выберите вкладку "General"
5. В разделе "Your apps" найдите ваше Android приложение
6. Нажмите "Add fingerprint"
7. Вставьте SHA-1 fingerprint (обычно выглядит как: `AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD`)
8. Нажмите "Save"

### 3. Включить Google Sign-In в Firebase

1. В Firebase Console перейдите в Authentication → Sign-in method
2. Найдите "Google" в списке провайдеров
3. Нажмите на него и включите (Enable)
4. Заполните:
   - Project public-facing name: ClipCraft
   - Project support email: ваш email
5. Нажмите "Save"

### 4. Проверить Web Client ID

Убедитесь, что в файле `app/src/main/res/values/strings.xml` есть строка:
```xml
<string name="default_web_client_id" translatable="false">YOUR_WEB_CLIENT_ID</string>
```

Если её нет, то:
1. Скачайте новый google-services.json из Firebase Console
2. Замените существующий файл в папке app/
3. Пересоберите проект (Build → Rebuild Project)

## Тестирование

После выполнения всех шагов:
1. Запустите приложение
2. На экране входа нажмите "Войти через Google"
3. Выберите Google аккаунт
4. Приложение должно успешно авторизоваться

## Возможные проблемы:

### Ошибка 12500
- Неправильный SHA-1 fingerprint
- Решение: проверьте, что добавили правильный SHA-1 в Firebase

### Ошибка 10
- Неправильная конфигурация
- Решение: скачайте новый google-services.json

### ID token is null
- Web Client ID не настроен
- Решение: проверьте strings.xml и пересоберите проект