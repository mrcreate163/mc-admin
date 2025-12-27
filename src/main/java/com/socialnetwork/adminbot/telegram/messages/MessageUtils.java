package com.socialnetwork.adminbot.telegram.messages;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилиты для работы с сообщениями бота.
 */
public class MessageUtils {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Форматирует дату для отображения в сообщениях.
     */
    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return BotMessage.STATUS_UNKNOWN.raw();
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Форматирует дату и время для отображения в сообщениях.
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return BotMessage.STATUS_UNKNOWN.raw();
        }
        return dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * Безопасно возвращает строковое значение или "N/A".
     */
    public static String safeString(String value) {
        return value != null && !value.isBlank()
                ? BotMessage.escapeHtml(value)
                : BotMessage.STATUS_UNKNOWN.raw();
    }

    /**
     * Форматирует boolean статус в эмодзи.
     */
    public static String formatStatus(Boolean value) {
        if (value == null) {
            return BotMessage.STATUS_UNKNOWN.raw();
        }
        return value ? BotMessage.STATUS_YES.raw() : BotMessage.STATUS_NO.raw();
    }

    /**
     * Форматирует онлайн статус.
     */
    public static String formatOnlineStatus(Boolean isOnline) {
        if (isOnline == null) {
            return BotMessage.STATUS_UNKNOWN.raw();
        }
        return isOnline ? BotMessage.STATUS_ONLINE.raw() : BotMessage.STATUS_OFFLINE.raw();
    }

    /**
     * Форматирует блокировку пользователя.
     */
    public static String formatBlockedStatus(Boolean isBlocked) {
        if (isBlocked == null) {
            return BotMessage.STATUS_UNKNOWN.raw();
        }
        return isBlocked ? BotMessage.STATUS_BLOCKED.raw() : BotMessage.STATUS_ACTIVE.raw();
    }
}
