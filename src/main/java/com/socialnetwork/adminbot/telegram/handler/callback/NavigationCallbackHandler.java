package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.StatisticsService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Map;
import java.util.UUID;

/**
 * Обработчик callback-запросов для навигации и статистики.
 * Обрабатывает: show_stats, main_menu, stats:*, noop
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NavigationCallbackHandler extends BaseCallbackHandler {

    private final StatisticsService statisticsService;
    private final AuditLogService auditLogService;

    @Override
    public boolean canHandle(String callbackData) {
        return callbackData.equals("show_stats") ||
               callbackData.equals("main_menu") ||
               callbackData.startsWith("stats:") ||
               callbackData.equals("noop");
    }

    @Override
    public EditMessageText handle(CallbackQuery callbackQuery, Long chatId, Integer messageId, Long adminId) {
        String data = callbackQuery.getData();

        try {
            if (data.equals("show_stats")) {
                return handleShowStats(chatId, messageId, adminId);
            } else if (data.equals("main_menu")) {
                return handleMainMenu(chatId, messageId);
            } else if (data.startsWith("stats:")) {
                return handleUserStats(data, chatId, messageId);
            } else if (data.equals("noop")) {
                return null; // Игнорируем нажатие на неактивные кнопки
            }
        } catch (Exception e) {
            log.error("Error handling navigation callback: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }

        return null;
    }

    /**
     * Показать общую статистику платформы.
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

        return createMessage(chatId, messageId, text, KeyboardBuilder.buildMainMenuKeyboard());
    }

    /**
     * Вернуться в главное меню.
     */
    private EditMessageText handleMainMenu(Long chatId, Integer messageId) {
        return createMessage(chatId,
                messageId,
                String.join("\n\n",
                        BotMessage.MAIN_MENU_TITLE.raw(),
                        BotMessage.MAIN_MENU_SUBTITLE.raw()),
                        KeyboardBuilder.buildMainMenuKeyboard());
    }

    /**
     * Показать статистику конкретного пользователя (заглушка для v2.0).
     */
    private EditMessageText handleUserStats(String data, Long chatId, Integer messageId) {
        UUID userId = UUID.fromString(data.substring("stats:".length()));

        return createMessage(chatId,
                messageId,
                String.join("\n\n",
                        BotMessage.STATS_USER_TITLE.format(userId),
                        BotMessage.STATS_USER_COMING_SOON.raw()
                ));
    }
}
