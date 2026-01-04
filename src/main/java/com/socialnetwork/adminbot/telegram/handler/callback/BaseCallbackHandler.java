package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

/**
 * Базовый абстрактный класс для обработчиков callback-запросов.
 * Содержит общие утилитные методы.
 */
public abstract class BaseCallbackHandler implements CallbackHandler {

    /**
     * Создаёт сообщение об ошибке.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения
     * @param error     текст ошибки
     * @return EditMessageText с ошибкой
     */
    protected EditMessageText createErrorMessage(Long chatId, Integer messageId, String error) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.ERROR_GENERIC.format(error));
        message.setParseMode("HTML");
        return message;
    }

    /**
     * Создаёт простое сообщение с текстом.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения
     * @param text      текст сообщения
     * @return EditMessageText с текстом
     */
    protected EditMessageText createMessage(Long chatId, Integer messageId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }

    /**
     * Экранирование HTML для Telegram.
     * Заменяет специальные HTML символы на их entity-коды.
     *
     * @param text текст для экранирования
     * @return экранированный текст
     */
    protected String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
