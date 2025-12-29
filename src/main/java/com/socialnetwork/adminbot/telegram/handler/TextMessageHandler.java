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

    // Stateful handlers будут добавлены позже
    // private final SearchConversationHandler searchHandler;
    // private final BanConversationHandler banHandler;

    /**
     * Обработать текстовое сообщение в зависимости от состояния
     */
    public SendMessage handle(Message message, Long adminId) {
        ConversationState state = conversationStateService.getState(adminId);
        BotState currentState = state.getState();

        log.debug("Processing text message for user {} in state {}", adminId, currentState);

        // Роутинг по состояниям
        switch (currentState) {
            case IDLE:
                return handleIdleState(message);

            case AWAITING_SEARCH_QUERY:
                // return searchHandler.handleSearchQuery(message, adminId, state);
                return createTemporaryMessage(message.getChatId(),
                        "Search handler coming soon in v2.0");

            case AWAITING_BAN_REASON:
                 return banCommandHandler.handleConversationStep(message, adminId, state);

            case AWAITING_ADMIN_TELEGRAM_ID:
                // return adminManagementHandler.handleTelegramId(message, adminId, state);
                return createTemporaryMessage(message.getChatId(),
                        "Admin management handler coming soon in v2.0");

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
