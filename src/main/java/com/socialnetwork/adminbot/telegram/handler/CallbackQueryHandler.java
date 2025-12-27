package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.StatisticsService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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

    public EditMessageText handle(CallbackQuery callbackQuery, Long adminTelegramId) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            // Маршрутизация по типу callback data
            if (data.startsWith("block:")) {
                return handleBlock(data, chatId, messageId, adminTelegramId);
            } else if (data.startsWith("unblock:")) {
                return handleUnblock(data, chatId, messageId, adminTelegramId);
            } else if (data.startsWith("stats:")) {
                return handleUserStats(data, chatId, messageId);
            } else if (data.equals("show_stats")) {
                return handleShowStats(chatId, messageId, adminTelegramId);
            } else if (data.equals("main_menu")) {
                return handleMainMenu(chatId, messageId);
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
    private EditMessageText handleBlock(String data, Long chatId, Integer messageId, Long adminTelegramId) {
        UUID userId = UUID.fromString(data.substring("block:".length()));

        userService.blockUser(userId, adminTelegramId, "Blocked via callback");
        auditLogService.logAction("BLOCK_USER", adminTelegramId, userId, Map.of("source", "callback").toString());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.BAN_CALLBACK_SUCCESS.format(userId));
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Обработка разблокировки пользователя через callback
     */
    private EditMessageText handleUnblock(String data, Long chatId, Integer messageId, Long adminTelegramId) {
        UUID userId = UUID.fromString(data.substring("unblock:".length()));

        userService.unblockUser(userId, adminTelegramId);
        auditLogService.logAction("UNBLOCK_USER", adminTelegramId, userId, Map.of("source", "callback").toString());

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
    private EditMessageText handleShowStats(Long chatId, Integer messageId, Long adminTelegramId) {
        StatisticsDto stats = statisticsService.getStatistics();
        auditLogService.logAction("VIEW_STATS", adminTelegramId, Map.of("source", "callback"));

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
