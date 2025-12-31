package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.*;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private final UserService userService;
    private final StatisticsService statisticsService;
    private final AuditLogService auditLogService;
    private final ConversationStateService conversationStateService;
    private final StateTransitionService stateTransitionService;
    private final BanCommandHandler banCommandHandler;
    private final SearchCommandHandler searchCommandHandler;

    public EditMessageText handle(CallbackQuery callbackQuery, Long adminId) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            // Маршрутизация по типу callback data
            if (data.startsWith("block:")) {
                return handleBlock(data, chatId, messageId, adminId);
            } else if (data.startsWith("unblock:")) {
                return handleUnblock(data, chatId, messageId, adminId);
            } else if (data.startsWith("stats:")) {
                return handleUserStats(data, chatId, messageId);
            } else if (data.equals("show_stats")) {
                return handleShowStats(chatId, messageId, adminId);
            } else if (data.equals("main_menu")) {
                return handleMainMenu(chatId, messageId);
            } else if (data.startsWith("ban_reason:")) {
                return handleBanReasonSelection(data, chatId, messageId, adminId);
            } else if (data.equals("ban_confirm")) {
                return handleBanConfirm(chatId, messageId, adminId);
            } else if (data.equals("ban_cancel")) {
                return handleBanCancel(chatId, messageId, adminId);
            } else if (data.startsWith("search_page:")) {
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
            } else if (data.equals("noop")) {
                return null; // Игнорируем нажатие на неактивные кнопки
            } else {
                return createErrorMessage(
                        chatId,
                        messageId,
                        BotMessage.ERROR_UNKNOWN_ACTION.raw()
                );
            }
        } catch (Exception e) {
            log.error("Error handling callback: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Обработка блокировки пользователя через callback
     * ОБНОВЛЕНО: теперь использует State Machine flow
     */
    private EditMessageText handleBlock(String data, Long chatId, Integer messageId, Long adminId) {
        try {
            UUID userId = UUID.fromString(data.substring("block:".length()));

            // Проверяем, что пользователь в IDLE состоянии
            BotState currentState = conversationStateService.getCurrentState(adminId);
            if (currentState != BotState.IDLE) {
                return createErrorMessage(chatId, messageId, BotMessage.UNCOMPLETED_ACTION.raw());
            }

            // Получаем информацию о пользователе
            String email = userService.getUserById(userId).getEmail();

            // Создаём состояние для flow бана
            ConversationState newState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();

            newState.addData(StateDataKey.BAN_TARGET_USER_ID, userId.toString());
            newState.addData(StateDataKey.BAN_TARGET_EMAIL, email);

            conversationStateService.setState(adminId, newState);

            log.info("User {} started ban conversation via callback for target user {}", adminId, userId);

            // Показываем клавиатуру с причинами бана
            String text = String.join("\n\n",
                    BotMessage.USER_INFO_EMAIL.format(email),
                    BotMessage.USER_INFO_ID.format(userId),
                    BotMessage.CHOOSE_REASON.raw()
            );

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildBanReasonsKeyboard());

            return message;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in callback: {}", data);
            return createErrorMessage(chatId, messageId, "⚠️ Неверный формат ID пользователя");
        } catch (Exception e) {
            log.error("Error handling block callback: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Обработка выбора причины бана из клавиатуры
     */
    private EditMessageText handleBanReasonSelection(String data, Long chatId, Integer messageId, Long adminId) {
        String reason = data.substring("ban_reason:".length());

        // Маппинг callback data -> человекочитаемая причина
        String readableReason = switch (reason) {
            case "spam" -> "Спам";
            case "harassment" -> "Harassment";
            case "bot" -> "Bot/Fake аккаунт";
            case "violation" -> "Нарушение правил сообщества";
            default -> reason;
        };

        ConversationState state = conversationStateService.getState(adminId);

        if (state.getState() != BotState.AWAITING_BAN_REASON) {
            return createErrorMessage(chatId, messageId,
                    "⚠️ Ошибка: неверное состояние для выбора причины.");
        }

        try {
            // Сохраняем причину в Redis перед переходом к подтверждению
            conversationStateService.updateStateData(adminId, StateDataKey.BAN_REASON, readableReason);
            stateTransitionService.transitionTo(adminId, BotState.CONFIRMING_BAN);

            String targetUserIdStr = state.getData(StateDataKey.BAN_TARGET_USER_ID, String.class);
            String targetUserEmail = state.getData(StateDataKey.BAN_TARGET_EMAIL, String.class);

            String confirmationText = String.join("\n\n",
                    BotMessage.ACCEPT_TO_BLOCK.raw(),
                    BotMessage.USER_INFO_EMAIL.format(targetUserEmail),
                    BotMessage.USER_INFO_ID.format(targetUserIdStr),
                    BotMessage.BAN_REASON.format(escapeHtml(readableReason)),
                    BotMessage.ACCEPT_TO_BLOCK_2.raw()
            );

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(confirmationText);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildConfirmationKeyboard("ban"));

            return message;

        } catch (Exception e) {
            log.error("Error processing ban reason selection: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Подтверждение бана
     */
    private EditMessageText handleBanConfirm(Long chatId, Integer messageId, Long adminId) {
        SendMessage result = banCommandHandler.executeBan(chatId, adminId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(result.getText());
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Отмена бана
     */
    private EditMessageText handleBanCancel(Long chatId, Integer messageId, Long adminId) {
        SendMessage result = banCommandHandler.cancelBan(chatId, adminId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(result.getText());
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Обработка разблокировки пользователя через callback
     */
    private EditMessageText handleUnblock(String data, Long chatId, Integer messageId, Long adminId) {
        UUID userId = UUID.fromString(data.substring("unblock:".length()));
        userService.unblockUser(userId, adminId);

        auditLogService.logAction("UNBLOCK_USER", adminId, userId,
                Map.of("source", "callback").toString());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.UNBAN_CALLBACK_SUCCESS.format(userId));
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Показать статистику конкретного пользователя (заглушка для v2.0)
     */
    private EditMessageText handleUserStats(String data, Long chatId, Integer messageId) {
        UUID userId = UUID.fromString(data.substring("stats:".length()));

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(String.join("\n\n",
                BotMessage.STATS_USER_TITLE.format(userId),
                BotMessage.STATS_USER_COMING_SOON.raw()
        ));
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Показать общую статистику платформы
     */
    private EditMessageText handleShowStats(Long chatId, Integer messageId, Long adminId) {
        StatisticsDto stats = statisticsService.getStatistics();
        auditLogService.logAction("VIEW_STATS", adminId, Map.of("source", "callback"));

        String text = String.join("\n",
                BotMessage.STATS_TITLE.raw(),
                "",
                BotMessage.STATS_TOTAL_USERS.format(stats.getTotalUsers()),
                BotMessage.STATS_NEW_TODAY.format(stats.getNewUsersToday()),
                BotMessage.STATS_ACTIVE_USERS.format(stats.getActiveUsers()),
                BotMessage.STATS_BLOCKED_USERS.format(stats.getBlockedUsers()),
                BotMessage.STATS_TOTAL_ADMINS.format(stats.getTotalAdmins())
        );

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode("HTML");
        message.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());

        return message;
    }

    /**
     * Вернуться в главное меню
     */
    private EditMessageText handleMainMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(String.join("\n\n",
                BotMessage.MAIN_MENU_TITLE.raw(),
                BotMessage.MAIN_MENU_SUBTITLE.raw()
        ));
        message.setParseMode("HTML");
        message.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());

        return message;
    }

    /**
     * Создать сообщение об ошибке
     */
    private EditMessageText createErrorMessage(Long chatId, Integer messageId, String error) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.ERROR_GENERIC.format(error));
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Создать ответ на callback query (всплывающее уведомление)
     */
    public AnswerCallbackQuery createAnswer(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);

        return answer;
    }

    /**
     * Обработка пагинации результатов поиска
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

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(result.getText());
            message.setParseMode("HTML");
            message.setReplyMarkup((InlineKeyboardMarkup) result.getReplyMarkup());

            return message;
        } catch (NumberFormatException e) {
            log.error("Invalid page number in callback: {}", data);
            return createErrorMessage(chatId, messageId, "⚠️ Некорректный номер страницы");
        }
    }

    /**
     * Просмотр детальной информации о пользователе из результатов поиска
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

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildUserActionsKeyboard(userId, user.getIsBlocked()));

            return message;
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_view callback: {}, error: {}", data, e.getMessage());
            return createErrorMessage(chatId, messageId, "⚠️ Неверный ID пользователя");
        } catch (Exception e) {
            log.error("Error viewing user from search: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Бан пользователя из результатов поиска (запуск flow бана)
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
            String text = String.join("\n\n",
                    BotMessage.BAN_HEADER_2.raw(),
                    BotMessage.USER_INFO_NAME_2.format(escapeHtml(user.getFirstName())),
                    BotMessage.USER_INFO_EMAIL_2.format(escapeHtml(user.getEmail())),
                    BotMessage.USER_INFO_ID.format(userId),
                    BotMessage.CHOOSE_REASON.raw()
            );

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildBanReasonsKeyboard());

            return message;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_ban callback: {}", data);
            return createErrorMessage(chatId, messageId, "⚠️ Неверный ID пользователя");
        } catch (Exception e) {
            log.error("Error starting ban from search: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Разбан пользователя из результатов поиска (мгновенное действие)
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

            // Проверяем, заблокирован ли
            if (!user.getIsBlocked()) {
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

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("HTML");

            return message;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_unban callback: {}", data);
            return createErrorMessage(chatId, messageId, "⚠️ Неверный ID пользователя");
        } catch (Exception e) {
            log.error("Error unblocking user from search: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Начать новый поиск
     */
    private EditMessageText handleSearchNew(Long chatId, Integer messageId, Long adminId) {
        // Создаём состояние ожидания поискового запроса
        ConversationState newState = ConversationState.builder()
                .state(BotState.AWAITING_SEARCH_QUERY)
                .build();

        conversationStateService.setState(adminId, newState);

        log.info("User {} started new search", adminId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.SEARCH_PROMPT.raw());
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Отмена поиска
     */
    private EditMessageText handleSearchCancel(Long chatId, Integer messageId, Long adminId) {
        SendMessage result = searchCommandHandler.cancelSearch(chatId, adminId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(result.getText());
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Форматирование детальной информации о пользователе
     */
    private String formatUserDetails(AccountDto user) {
        return String.join("\n\n",
                BotMessage.USER_INFO_TITLE.raw(),
                BotMessage.USER_INFO_ID.format(user.getId()),
                BotMessage.USER_INFO_EMAIL_2.format(escapeHtml(user.getEmail())),
                BotMessage.USER_INFO_NAME_2.format(escapeHtml(user.getFirstName())),
                BotMessage.USER_INFO_PHONE.format(
                        user.getPhone() != null ? user.getPhone() : BotMessage.STATUS_UNKNOWN.raw()),
                BotMessage.USER_INFO_COUNTRY.format(
                        user.getCountry() != null ? user.getCountry() : BotMessage.STATUS_UNKNOWN.raw()),
                BotMessage.USER_INFO_BIRTH_DATE.format(
                        user.getBirthDate() != null ? user.getBirthDate() : BotMessage.STATUS_UNKNOWN.raw()),
                BotMessage.USER_INFO_REGISTERED.format(
                        user.getRegDate() != null ? user.getRegDate() : BotMessage.STATUS_UNKNOWN.raw()),
                BotMessage.USER_INFO_LAST_ONLINE.format(
                        user.getLastOnlineTime() != null ? user.getLastOnlineTime() : BotMessage.STATUS_UNKNOWN.raw()),
                BotMessage.USER_INFO_ONLINE.format(
                        Boolean.TRUE.equals(user.getIsOnline()) ? BotMessage.STATUS_ONLINE.raw() : BotMessage.STATUS_OFFLINE.raw()),
                BotMessage.USER_INFO_BLOCKED.format(
                        Boolean.TRUE.equals(user.getIsBlocked()) ? BotMessage.STATUS_BLOCKED.raw() : BotMessage.STATUS_ACTIVE.raw()),
                BotMessage.USER_INFO_ABOUT.format(
                        user.getAbout() != null ? user.getAbout() : BotMessage.STATUS_UNKNOWN.raw())
        );
    }

    /**
     * Экранирование HTML для Telegram
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
