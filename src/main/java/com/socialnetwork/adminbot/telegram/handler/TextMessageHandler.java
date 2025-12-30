package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

/**
 * Обработчик текстовых сообщений (не команд)
 * Роутит сообщения в зависимости от текущего состояния диалога
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextMessageHandler {

    private final ConversationStateService conversationStateService;
    private final BanCommandHandler banCommandHandler;
    private final SearchCommandHandler searchCommandHandler;

    /**
     * Обработать текстовое сообщение в зависимости от состояния
     */
    public SendMessage handle(Message message, Long adminId) {
        ConversationState state = conversationStateService.getState(adminId);
        BotState currentState = state.getState();

        Long chatId = message.getChatId();
        String text = message.getText();

        log.debug("Handling text message: state={}, text='{}', user={}",
                currentState, text, adminId);

        // Роутинг по состояниям
        switch (currentState) {
            case IDLE:
                return handleIdleState(message);

            case AWAITING_BAN_REASON:
                return banCommandHandler.handleConversationStep(message, adminId, state);

            case AWAITING_SEARCH_QUERY:
                return searchCommandHandler.processSearchQuery(message.getChatId(), adminId, text);

            case AWAITING_ADMIN_TELEGRAM_ID:
                // return adminManagementHandler.handleTelegramId(message, adminId, state);
                return createTemporaryMessage(message.getChatId(),
                        "Admin management handler coming soon in v2.0");

            case SHOWING_SEARCH_RESULTS:
                return handleSearchResultsState(chatId, text, adminId);

            default:
                log.warn("Unhandled state: {} for user {}", currentState, adminId);
                return createMessage(message.getChatId(),
                        BotMessage.ERROR_UNKNOWN_STATE.raw());
        }
    }

    /**
     * Обработка сообщения в состоянии IDLE
     * Пользователь не в диалоге, отправляем справку
     */
    private SendMessage handleIdleState(Message message) {
        return createMessage(message.getChatId(),
                BotMessage.ERROR_UNKNOWN_COMMAND.raw());
    }

    /**
     * Обработка текста в состоянии SHOWING_SEARCH_RESULTS
     */
    private SendMessage handleSearchResultsState(Long chatId, String text, Long adminId) {
        log.debug("Text message during search results: '{}'", text);

        // Если пользователь вводит новый поисковый запрос
        if (text.length() >= 3) {
            return searchCommandHandler.processSearchQuery(chatId, adminId, text);
        }

        return createMessage(chatId, BotMessage.NAVIGATION_HINT.raw());
    }

    private SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }

    private SendMessage createTemporaryMessage(Long chatId, String text) {
        return createMessage(chatId, "⚠️ " + text);
    }
}
