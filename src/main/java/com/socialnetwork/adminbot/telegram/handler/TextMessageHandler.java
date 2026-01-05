package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import com.socialnetwork.adminbot.telegram.messages.TelegramMessageFactory;
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

        SendMessage response;

        // Роутинг по состояниям
        switch (currentState) {
            case IDLE:
                response = handleIdleState(message);
                break;

            case AWAITING_BAN_REASON:
                response = banCommandHandler.handleConversationStep(message, adminId, state);
                break;

            case AWAITING_SEARCH_QUERY:
                log.info("🔍 Routing to SearchCommandHandler.processSearchQuery");
                response = searchCommandHandler.processSearchQuery(message.getChatId(), adminId, text);
                break;

            case AWAITING_ADMIN_TELEGRAM_ID:
                response = TelegramMessageFactory.createHtmlMessage(message.getChatId(),
                        "⚠️ Admin management handler coming soon in v2.0");
                break;

            case AWAITING_ADMIN_USERNAME:
                // New invite-based admin flow - handled by AddAdminCommandHandler
                response = TelegramMessageFactory.createHtmlMessage(message.getChatId(),
                        "⚠️ Пожалуйста, используйте кнопки для выбора роли или отмены.");
                break;

            case AWAITING_ADMIN_ROLE:
                response = TelegramMessageFactory.createHtmlMessage(message.getChatId(),
                        "⚠️ Пожалуйста, используйте кнопки для выбора роли.");
                break;

            case CONFIRMING_ADMIN_INVITE_CREATION:
            case CONFIRMING_INVITE_ACCEPTANCE:
                response = TelegramMessageFactory.createHtmlMessage(message.getChatId(),
                        "⚠️ Пожалуйста, используйте кнопки для подтверждения или отмены.");
                break;

            case SHOWING_SEARCH_RESULTS:
                response = handleSearchResultsState(chatId, text, adminId);
                break;

            default:
                log.warn("Unhandled state: {} for user {}", currentState, adminId);
                response = TelegramMessageFactory.createErrorMessage(message.getChatId(),
                        BotMessage.ERROR_UNKNOWN_STATE.raw());
        }

        // 🔍 КРИТИЧЕСКОЕ ЛОГИРОВАНИЕ ПЕРЕД ВОЗВРАТОМ
        if (response != null) {
            log.info("📦 TextMessageHandler returning SendMessage:");
            log.info("  ├─ ChatId: {}", response.getChatId());
            log.info("  ├─ Text length: {} chars",
                    response.getText() != null ? response.getText().length() : 0);
            log.info("  ├─ Has keyboard: {}", response.getReplyMarkup() != null);
            log.info("  └─ Returning to caller (TelegramBot) for execution");
        } else {
            log.error("❌ TextMessageHandler returning NULL response!");
        }

        return response;
    }

    /**
     * Обработка сообщения в состоянии IDLE
     * Пользователь не в диалоге, отправляем справку
     */
    private SendMessage handleIdleState(Message message) {
        return TelegramMessageFactory.createHtmlMessage(message.getChatId(),
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

        return TelegramMessageFactory.createHtmlMessage(chatId, BotMessage.NAVIGATION_HINT.raw());
    }
}
