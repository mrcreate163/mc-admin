package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.handler.SearchCommandHandler;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.UUID;

/**
 * Обработчик callback-запросов для функций поиска.
 * Обрабатывает: search_page:*, search_view:*, search_ban:*, search_unban:*, search_new, search_cancel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCallbackHandler extends BaseCallbackHandler {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ConversationStateService conversationStateService;
    private final SearchCommandHandler searchCommandHandler;

    @Override
    public boolean canHandle(String callbackData) {
        return callbackData.startsWith("search_page:") ||
               callbackData.startsWith("search_view:") ||
               callbackData.startsWith("search_ban:") ||
               callbackData.startsWith("search_unban:") ||
               callbackData.equals("search_new") ||
               callbackData.equals("search_cancel");
    }

    @Override
    public EditMessageText handle(CallbackQuery callbackQuery, Long chatId, Integer messageId, Long adminId) {
        String data = callbackQuery.getData();

        try {
            if (data.startsWith("search_page:")) {
                return handleSearchPageNavigation(data, chatId, messageId, adminId);
            } else if (data.startsWith("search_view:")) {
                return handleSearchViewUser(data, chatId, messageId, adminId);
            } else if (data.startsWith("search_ban:")) {
                return handleSearchBanUser(data, chatId, messageId, adminId);
            } else if (data.startsWith("search_unban:")) {
                return handleSearchUnbanUser(data, chatId, messageId, adminId);
            } else if (data.equals("search_new")) {
                return handleSearchNew(chatId, messageId, adminId);
            } else if (data.equals("search_cancel")) {
                return handleSearchCancel(chatId, messageId, adminId);
            }
        } catch (Exception e) {
            log.error("Error handling search callback: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }

        return null;
    }

    /**
     * Обработка пагинации результатов поиска.
     */
    private EditMessageText handleSearchPageNavigation(
            String data,
            Long chatId,
            Integer messageId,
            Long adminId
    ) {
        try {
            int newPage = Integer.parseInt(data.substring("search_page:".length()));
            SendMessage result = searchCommandHandler.handlePageNavigation(chatId, adminId, newPage);

            return createMessage(chatId, messageId, result.getText(), (InlineKeyboardMarkup) result.getReplyMarkup());
        } catch (NumberFormatException e) {
            log.error("Invalid page number in callback: {}", data);
            return createErrorMessage(chatId, messageId, "⚠️ Некорректный номер страницы");
        }
    }

    /**
     * Просмотр детальной информации о пользователе из результатов поиска.
     */
    private EditMessageText handleSearchViewUser(
            String data,
            Long chatId,
            Integer messageId,
            Long adminId
    ) {
        try {
            UUID userId = UUID.fromString(data.substring("search_view:".length()).trim());
            AccountDto user = userService.getUserById(userId);

            // Логируем действие
            auditLogService.logAction("VIEW_USER", adminId, userId, "from_search");

            // Формируем детальную информацию
            String text = formatUserDetails(user);

            return createMessage(
                    chatId,
                    messageId,
                    text,
                    KeyboardBuilder.buildUserActionsKeyboard(userId, user.getIsBlocked()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_view callback: {}, error: {}", data, e.getMessage());
            return createErrorMessage(chatId, messageId, "⚠️ Неверный ID пользователя");
        }
    }

    /**
     * Бан пользователя из результатов поиска (запуск flow бана).
     */
    private EditMessageText handleSearchBanUser(
            String data,
            Long chatId,
            Integer messageId,
            Long adminId
    ) {
        try {
            UUID userId = UUID.fromString(data.substring("search_ban:".length()));

            // Проверяем, что админ в правильном состоянии
            BotState currentState = conversationStateService.getCurrentState(adminId);
            if (currentState != BotState.SHOWING_SEARCH_RESULTS) {
                return createErrorMessage(chatId, messageId,
                        "⚠️ Неверное состояние. Используйте /search для нового поиска.");
            }

            // Получаем информацию о пользователе
            AccountDto user = userService.getUserById(userId);

            // Проверяем, не заблокирован ли уже
            if (Boolean.TRUE.equals(user.getIsBlocked())) {
                return createErrorMessage(chatId, messageId,
                        "⚠️ Пользователь уже заблокирован.");
            }

            // Создаём состояние для flow бана
            ConversationState newState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();

            newState.addData(StateDataKey.BAN_TARGET_USER_ID, userId.toString());
            newState.addData(StateDataKey.BAN_TARGET_EMAIL, user.getEmail());

            conversationStateService.setState(adminId, newState);

            log.info("User {} started ban flow from search for target user {}", adminId, userId);

            // Показываем клавиатуру с причинами бана
            String text = formatUserDetails(user);

            return createMessage(chatId, messageId, text, KeyboardBuilder.buildBanReasonsKeyboard());

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_ban callback: {}, error: {}", data, e.getMessage());
            return createErrorMessage(chatId, messageId, "⚠️ Неверный ID пользователя");
        } catch (Exception e) {
            log.error("Error starting ban from search: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Разбан пользователя из результатов поиска (мгновенное действие).
     */
    private EditMessageText handleSearchUnbanUser(
            String data,
            Long chatId,
            Integer messageId,
            Long adminId
    ) {
        try {
            UUID userId = UUID.fromString(data.substring("search_unban:".length()));

            // Получаем информацию о пользователе
            AccountDto user = userService.getUserById(userId);

            // Проверяем, заблокирован ли (используем Boolean.TRUE.equals для null-safety)
            if (!Boolean.TRUE.equals(user.getIsBlocked())) {
                return createErrorMessage(chatId, messageId,
                        "⚠️ Пользователь не заблокирован.");
            }

            // Разблокируем
            userService.unblockUser(userId, adminId);

            log.info("User {} unblocked user {} from search", adminId, userId);

            String text = String.join("\n\n",
                    BotMessage.UNBAN_CALLBACK_SUCCESS_UNNAMED.raw(),
                    BotMessage.USER_INFO_NAME_2.format(escapeHtml(user.getFirstName())),
                    BotMessage.USER_INFO_EMAIL_2.format(escapeHtml(user.getEmail())),
                    BotMessage.USER_INFO_ID.format(userId)
            );

            // Сбрасываем состояние поиска
            conversationStateService.resetToIdle(adminId);

            return createMessage(chatId,messageId,text);

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_unban callback: {}", data);
            return createErrorMessage(chatId, messageId, "⚠️ Неверный ID пользователя");
        }
    }

    /**
     * Начать новый поиск.
     */
    private EditMessageText handleSearchNew(Long chatId, Integer messageId, Long adminId) {
        // Создаём состояние ожидания поискового запроса
        ConversationState newState = ConversationState.builder()
                .state(BotState.AWAITING_SEARCH_QUERY)
                .build();

        conversationStateService.setState(adminId, newState);

        log.info("User {} started new search", adminId);

        return createMessage(chatId,messageId, BotMessage.SEARCH_PROMPT.raw());
    }

    /**
     * Отмена поиска.
     */
    private EditMessageText handleSearchCancel(Long chatId, Integer messageId, Long adminId) {
        SendMessage result = searchCommandHandler.cancelSearch(chatId, adminId);

        return createMessage(chatId, messageId, result.getText());
    }
}
