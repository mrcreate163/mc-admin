package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.telegram.handler.callback.CallbackHandler;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;

/**
 * Маршрутизатор callback-запросов.
 * Делегирует обработку специализированным обработчикам на основе типа callback data.
 *
 * Рефакторинг: класс разбит на несколько специализированных обработчиков
 * для соблюдения принципа единственной ответственности (SRP):
 * - UserBlockCallbackHandler: блокировка/разблокировка пользователей
 * - SearchCallbackHandler: функции поиска
 * - AdminManagementCallbackHandler: управление администраторами
 * - NavigationCallbackHandler: навигация и статистика
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private final List<CallbackHandler> handlers;

    /**
     * Обрабатывает callback query, делегируя соответствующему обработчику.
     *
     * @param callbackQuery callback query от Telegram
     * @param adminId       ID администратора
     * @return результат обработки или сообщение об ошибке
     */
    public EditMessageText handle(CallbackQuery callbackQuery, Long adminId) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            // Находим подходящий обработчик
            for (CallbackHandler handler : handlers) {
                if (handler.canHandle(data)) {
                    return handler.handle(callbackQuery, chatId, messageId, adminId);
                }
            }

            // Если обработчик не найден
            return createErrorMessage(chatId, messageId, BotMessage.ERROR_UNKNOWN_ACTION.raw());

        } catch (Exception e) {
            log.error("Error handling callback: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Создаёт ответ на callback query (всплывающее уведомление).
     *
     * @param callbackQueryId ID callback query
     * @param text            текст уведомления
     * @return AnswerCallbackQuery
     */
    public AnswerCallbackQuery createAnswer(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        return answer;
    }

    /**
     * Создаёт сообщение об ошибке.
     */
    private EditMessageText createErrorMessage(Long chatId, Integer messageId, String error) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.ERROR_GENERIC.format(error));
        message.setParseMode("HTML");
        return message;
    }
}
