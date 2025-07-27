package com.example.clipcraft.config

object Config {
    // URL вашего Whisper сервера на fly.io
    const val WHISPER_API_BASE_URL = "https://loud-whisper.fly.dev"

    // URL вашего основного API сервера
    const val MAIN_API_BASE_URL = "https://clipcraft-holy-water-8099.fly.dev"

    // Максимальная длительность видео в секундах (5 минут)
    const val MAX_VIDEO_DURATION_SECONDS = 300

    // Максимальный размер видео файла в байтах (500 MB)
    const val MAX_VIDEO_SIZE_BYTES = 500L * 1024 * 1024

    // Максимальное количество видео
    const val MAX_VIDEO_COUNT = 20
}