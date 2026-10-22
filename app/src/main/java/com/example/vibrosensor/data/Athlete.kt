package com.example.vibrosensor.data

/**
 * Модель данных спортсмена
 */
data class Athlete(
    val id: Int = 0,                    // Уникальный идентификатор
    val name: String,                    // Имя спортсмена
    val weightCategory: String,          // Весовая категория
    val color: String,                   // Цвет/угол ("red" или "blue")
    val deviceMac: String? = null,       // MAC-адрес Bluetooth модуля
    val deviceName: String? = null,      // Имя Bluetooth устройства
    val isActive: Boolean = true         // Участвует ли в текущем бою
) {
    /**
     * Возвращает цвет в виде ресурса
     */
    fun getColorRes(): Int {
        return when (color.lowercase()) {
            "red" -> android.R.color.holo_red_dark
            "blue" -> android.R.color.holo_blue_dark
            else -> android.R.color.black
        }
    }

    /**
     * Возвращает название цвета на русском
     */
    fun getColorName(): String {
        return when (color.lowercase()) {
            "red" -> "Красный"
            "blue" -> "Синий"
            else -> "Неизвестно"
        }
    }

    /**
     * Проверка, привязано ли устройство
     */
    fun hasDevice(): Boolean {
        return !deviceMac.isNullOrEmpty()
    }

    override fun toString(): String {
        return "$name ($weightCategory, ${getColorName()})"
    }
}

/**
 * Состояние матча (текущий бой)
 */
data class MatchState(
    val redAthlete: Athlete? = null,     // Спортсмен в красном углу
    val blueAthlete: Athlete? = null,    // Спортсмен в синем углу
    val isConnected: Boolean = false,    // Статус подключения к центру
    val lastCommand: String? = null,     // Последняя отправленная команда
    val commandTime: Long = 0L           // Время последней команды
) {
    /**
     * Проверка, что оба спортсмена назначены
     */
    fun hasBothAthletes(): Boolean {
        return redAthlete != null && blueAthlete != null
    }

    /**
     * Получить спортсмена по цвету
     */
    fun getAthleteByColor(color: String): Athlete? {
        return when (color.lowercase()) {
            "red" -> redAthlete
            "blue" -> blueAthlete
            else -> null
        }
    }
}

/**
 * Типы команд для отправки
 */
enum class CommandType {
    STOP,           // Остановка боя
    START,          // Начало боя
    PAUSE,          // Пауза
    RESUME,         // Возобновление
    VIBRATE_RED,    // Вибрация только красному (для теста)
    VIBRATE_BLUE,   // Вибрация только синему (для теста)
    VIBRATE_BOTH;   // Вибрация обоим

    /**
     * Получить текстовое представление
     */
    fun getDisplayName(): String {
        return when (this) {
            STOP -> "СТОП"
            START -> "СТАРТ"
            PAUSE -> "ПАУЗА"
            RESUME -> "ПРОДОЛЖИТЬ"
            VIBRATE_RED -> "Вибрация (Красный)"
            VIBRATE_BLUE -> "Вибрация (Синий)"
            VIBRATE_BOTH -> "Вибрация (Оба)"
        }
    }
}

/**
 * Статус подключения Bluetooth устройства
 */
enum class ConnectionStatus {
    CONNECTED,      // Подключено
    CONNECTING,     // Подключается
    DISCONNECTED,   // Отключено
    ERROR;          // Ошибка

    /**
     * Получить цвет для индикатора
     */
    fun getLedColor(): Int {
        return when (this) {
            CONNECTED -> android.R.color.holo_green_dark
            CONNECTING -> android.R.color.holo_orange_dark
            DISCONNECTED -> android.R.color.darker_gray
            ERROR -> android.R.color.holo_red_dark
        }
    }

    /**
     * Получить текстовое описание
     */
    fun getStatusText(): String {
        return when (this) {
            CONNECTED -> "Подключено"
            CONNECTING -> "Подключение..."
            DISCONNECTED -> "Отключено"
            ERROR -> "Ошибка"
        }
    }
}