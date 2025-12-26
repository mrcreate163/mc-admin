package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.AuditService;
import com.socialnetwork.adminbot.service.StatisticsService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
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
    private final AuditService auditService;

    public EditMessageText handle(CallbackQuery callbackQuery, Long adminTelegramId) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
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
            } else {
                return createErrorMessage(chatId, messageId, "Unknown action");
            }
        } catch (Exception e) {
            log.error("Error handling callback: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    private EditMessageText handleBlock(String data, Long chatId, Integer messageId, Long adminTelegramId) {
        UUID userId = UUID.fromString(data.substring("block:".length()));
        userService.blockUser(userId);
        auditService.log(adminTelegramId, "BLOCK_USER", userId, Map.of("source", "callback"));

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("✅ User " + userId + " has been blocked.");
        return message;
    }

    private EditMessageText handleUnblock(String data, Long chatId, Integer messageId, Long adminTelegramId) {
        UUID userId = UUID.fromString(data.substring("unblock:".length()));
        userService.unblockUser(userId);
        auditService.log(adminTelegramId, "UNBLOCK_USER", userId, Map.of("source", "callback"));

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("✅ User " + userId + " has been unblocked.");
        return message;
    }

    private EditMessageText handleUserStats(String data, Long chatId, Integer messageId) {
        UUID userId = UUID.fromString(data.substring("stats:".length()));
        
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("📊 User statistics for " + userId + "\n\n(Feature coming in v2.0)");
        return message;
    }

    private EditMessageText handleShowStats(Long chatId, Integer messageId, Long adminTelegramId) {
        StatisticsDto stats = statisticsService.getStatistics();
        auditService.log(adminTelegramId, "VIEW_STATS", Map.of("source", "callback"));

        String text = String.format(
                "📊 *Platform Statistics*\n\n" +
                "Total Users: %d\n" +
                "New Users Today: %d\n" +
                "Active Users: %d\n" +
                "Blocked Users: %d\n" +
                "Total Admins: %d",
                stats.getTotalUsers(),
                stats.getNewUsersToday(),
                stats.getActiveUsers(),
                stats.getBlockedUsers(),
                stats.getTotalAdmins()
        );

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());
        return message;
    }

    private EditMessageText handleMainMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("🏠 *Main Menu*\n\nSelect an action:");
        message.setParseMode("Markdown");
        message.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());
        return message;
    }

    private EditMessageText createErrorMessage(Long chatId, Integer messageId, String error) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("❌ Error: " + error);
        return message;
    }

    public AnswerCallbackQuery createAnswer(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        return answer;
    }
}
