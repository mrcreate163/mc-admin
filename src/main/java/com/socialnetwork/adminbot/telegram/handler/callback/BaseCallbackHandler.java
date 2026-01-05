package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import com.socialnetwork.adminbot.telegram.messages.TelegramMessageFactory;
import com.socialnetwork.adminbot.telegram.messages.UserInfoFormatter;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

/**
 * Базовый абстрактный класс для обработчиков callback-запросов.
 * Содержит общие утилитные методы для работы с сообщениями.
 */
public abstract class BaseCallbackHandler implements CallbackHandler {

    /**
     * Создаёт сообщение об ошибке.
     * Делегирует вызов к TelegramMessageFactory для устранения дублирования.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения
     * @param error     текст ошибки
     * @return EditMessageText с ошибкой
     */
    protected EditMessageText createErrorMessage(Long chatId, Integer messageId, String error) {
        return TelegramMessageFactory.createErrorEditMessage(chatId, messageId, error);
    }

    /**
     * Создаёт простое сообщение с текстом.
     * Делегирует вызов к TelegramMessageFactory для устранения дублирования.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения
     * @param text      текст сообщения
     * @return EditMessageText с текстом
     */
    protected EditMessageText createMessage(Long chatId, Integer messageId, String text) {
        return TelegramMessageFactory.createHtmlEditMessage(chatId, messageId, text);
    }

    /**
     * Экранирование HTML для Telegram.
     * Делегирует вызов к централизованному методу BotMessage.escapeHtml().
     *
     * @param text текст для экранирования
     * @return экранированный текст
     */
    protected String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return BotMessage.escapeHtml(text);
    }

    /**
     * Форматирование детальной информации о пользователе.
     * Делегирует вызов к UserInfoFormatter для устранения дублирования.
     *
     * @param user объект с данными пользователя
     * @return отформатированная строка с информацией о пользователе
     */
    protected String formatUserDetails(AccountDto user) {
        return UserInfoFormatter.formatFullUserInfo(user);
    }
}
