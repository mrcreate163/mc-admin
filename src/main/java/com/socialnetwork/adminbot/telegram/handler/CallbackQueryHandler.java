package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
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
    private final StateTransitionService  stateTransitionService;
    private final BanCommandHandler banCommandHandler;

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
            }else if (data.startsWith("ban_reason:")) {
                return handleBanReasonSelection(data, chatId, messageId, adminId);
            } else if (data.equals("ban_confirm")) {
                return handleBanConfirm(chatId, messageId, adminId);
            } else if (data.equals("ban_cancel")) {
                return handleBanCancel(chatId, messageId, adminId);
            }
            else {
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
     */
    private EditMessageText handleBlock(String data, Long chatId, Integer messageId, Long adminId) {
        UUID userId = UUID.fromString(data.substring("block:".length()));

        userService.blockUser(userId, adminId, "Blocked via callback");
        auditLogService.logAction("BLOCK_USER", adminId, userId, Map.of("source", "callback").toString());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.BAN_CALLBACK_SUCCESS.format(userId));
        message.setParseMode("HTML");

        return message;
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
            return createErrorMessage(chatId, messageId, BotMessage.ERROR_STATE_FOR_REASON.raw());
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
                    BotMessage.BAN_REASON.format(readableReason),
                    BotMessage.ACCEPT_TO_BLOCK_2.raw());

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
        auditLogService.logAction("UNBLOCK_USER", adminId, userId, Map.of("source", "callback").toString());

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
}
