# Быстрая настройка Google Sign-In для всех устройств

## Шаг 1: Создайте release keystore (один раз)

Запустите `create_release_keystore.bat` и введите:
- Имя и фамилию
- Подразделение (например: Development)
- Организация (например: ClipCraft)
- Город
- Область/штат
- Код страны (RU для России)
- Пароль для keystore (запомните его!)
- Пароль для ключа (можно тот же)

## Шаг 2: Скопируйте SHA отпечатки

После создания keystore вы увидите:
```
SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
SHA256: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
```

Скопируйте оба!

## Шаг 3: Добавьте в Firebase Console

1. Откройте https://console.firebase.google.com
2. Выберите ваш проект
3. Нажмите на шестеренку → Project Settings
4. Найдите ваше Android приложение
5. В разделе "SHA certificate fingerprints" нажмите "Add fingerprint"
6. Вставьте SHA-1, нажмите Save
7. Добавьте еще один fingerprint - SHA-256

## Шаг 4: Скачайте обновленный google-services.json

1. На той же странице нажмите "Download google-services.json"
2. Замените файл в папке `app/`

## Шаг 5: Создайте keystore.properties

1. Скопируйте `keystore.properties.example` как `keystore.properties`
2. Укажите пароли, которые вводили при создании keystore

## Шаг 6: Соберите подписанный APK

```cmd
gradlew.bat assembleRelease
```

APK будет в `app\build\outputs\apk\release\app-release.apk`

## Готово! 

Теперь Google Sign-In будет работать на ВСЕХ устройствах, где установлен этот APK.

## Важно:
- Используйте ТОЛЬКО release APK для распространения
- Debug APK работает только на вашем компьютере
- Храните keystore в безопасном месте - без него не сможете обновлять приложение!