package com.socialnetwork.adminbot.telegram.handler;


import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.entity.AdminRole;
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
    private final AddAdminCommandHandler addAdminCommandHandler;

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
            } else if (data.startsWith("add_admin:")) { // ⬅️ ДОБАВЛЕНО
                return handleAddAdminCallback(data, chatId, messageId, adminId);
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
            log.error("Invalid user ID in callback: {}, error: {}, path: {}", data, e.getMessage(), e.getStackTrace());
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
            String text = formatUserDetails(user);

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildBanReasonsKeyboard());

            return message;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in search_ban callback: {}, error: {}, path: {}", data, e.getMessage(), e.getStackTrace());
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
     * Обработка callback'ов для команды /addadmin
     *
     * Callback data формат: "add_admin:action:param"
     * Примеры:
     * - "add_admin:role:MODERATOR" - выбор роли MODERATOR
     * - "add_admin:role:ADMIN" - выбор роли ADMIN
     * - "add_admin:cancel" - отмена создания приглашения
     */
    private EditMessageText handleAddAdminCallback(
            String data,
            Long chatId,
            Integer messageId,
            Long adminId
    ) {
        // Парсинг callback data: "add_admin:action:param"
        String[] parts = data.split(":");

        if (parts.length < 2) {
            log.warn("Invalid add_admin callback format: {}", data);
            return createErrorMessage(chatId, messageId,
                    BotMessage.ERROR_INVALID_FORMAT.raw());
        }

        String action = parts[1];

        // Отмена
        if ("cancel".equals(action)) {
            String cancelMessage = addAdminCommandHandler.cancelAddAdmin(adminId);

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(cancelMessage);
            message.setParseMode("HTML");

            return message;
        }

        // Выбор роли
        if ("role".equals(action) && parts.length == 3) {
            String roleName = parts[2];

            try {
                AdminRole selectedRole = AdminRole.valueOf(roleName);
                String responseText = addAdminCommandHandler.handleRoleSelection(adminId, selectedRole);

                // Создаем сообщение со ссылкой (без клавиатуры, т.к. ссылка одноразовая)
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId.toString());
                message.setMessageId(messageId);
                message.setText(responseText);
                message.setParseMode("HTML");
                message.setReplyMarkup(null); // Убираем клавиатуру после генерации ссылки

                return message;

            } catch (IllegalArgumentException e) {
                log.error("Invalid role name: {}", roleName, e);
                return createErrorMessage(chatId, messageId,
                        BotMessage.ERROR_INVALID_FORMAT.raw());
            }
        }

        // Неизвестное действие
        log.warn("Unknown add_admin action: {}", action);
        return createErrorMessage(chatId, messageId,
                BotMessage.ERROR_UNKNOWN_ACTION.raw());
    }

    /**
     * Форматирование детальной информации о пользователе
     * ВАЖНО: НЕ используем BotMessage.format() для пользовательских данных,
     * т.к. они могут содержать символ % который вызовет IllegalFormatException
     */
    private String formatUserDetails(AccountDto user) {
        // Безопасное экранирование всех пользовательских данных
        String safeFirstName = escapeHtml(user.getFirstName() != null ? user.getFirstName() : "N/A");
        String safeLastName = escapeHtml(user.getLastName() != null ? user.getLastName() : "N/A");
        String safeEmail = escapeHtml(user.getEmail() != null ? user.getEmail() : "N/A");
        String safePhone = escapeHtml(user.getPhone() != null ? user.getPhone() : "N/A");
        String safeCountry = escapeHtml(user.getCountry() != null ? user.getCountry() : "N/A");
        String safeCity = escapeHtml(user.getCity() != null ? user.getCity() : "N/A");
        String safeBirthDate = user.getBirthDate() != null ? user.getBirthDate().toString() : "N/A";
        String safeRegDate = user.getRegDate() != null ? user.getRegDate().toString() : "N/A";
        String safeLastOnline = user.getLastOnlineTime() != null ? user.getLastOnlineTime().toString() : "N/A";
        String safeAbout = escapeHtml(user.getAbout() != null ? user.getAbout() : "N/A");

        String onlineStatus = Boolean.TRUE.equals(user.getIsOnline()) ? "✅ Да" : "❌ Нет";
        String blockedStatus = Boolean.TRUE.equals(user.getIsBlocked()) ? "🔴 Да" : "🟢 Нет";

        // Формируем текст напрямую без String.format для пользовательских данных
        return "👤 <b>Информация о пользователе</b>\n\n" +
                "🆔 ID: <code>" + user.getId() + "</code>\n" +
                "📧 Email: <code>" + safeEmail + "</code>\n" +
                "👤 Имя: " + safeFirstName + " " + safeLastName + "\n" +
                "📱 Телефон: " + safePhone + "\n" +
                "🌍 Страна: " + safeCountry + "\n" +
                "🏙️ Город: " + safeCity + "\n" +
                "📅 Дата регистрации: " + safeRegDate + "\n" +
                "🎂 Дата рождения: " + safeBirthDate + "\n" +
                "⏰ Последняя активность: " + safeLastOnline + "\n" +
                "🟢 Онлайн: " + onlineStatus + "\n" +
                "🔒 Заблокирован: " + blockedStatus + "\n" +
                "📝 О себе: " + safeAbout;
    }


    /**
     * Экранирование HTML для Telegram
     * Заменяет специальные HTML символы на их entity-коды
     */
    private String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
