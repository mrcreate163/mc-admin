package com.socialnetwork.adminbot.telegram.handler.callback;

import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Интерфейс для обработчиков callback-запросов.
 * Каждый обработчик отвечает за определённую группу callback actions.
 */
public interface CallbackHandler {

    /**
     * Проверяет, может ли этот обработчик обработать данный callback.
     *
     * @param callbackData данные из callback query
     * @return true, если обработчик поддерживает этот callback
     */
    boolean canHandle(String callbackData);

    /**
     * Обрабатывает callback запрос.
     *
     * @param callbackQuery callback query от Telegram
     * @param chatId        ID чата
     * @param messageId     ID сообщения для редактирования
     * @param adminId       ID администратора
     * @return результат обработки или null если обработка не требует ответа
     */
    EditMessageText handle(CallbackQuery callbackQuery, Long chatId, Integer messageId, Long adminId);
}
