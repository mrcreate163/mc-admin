package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.StatisticsService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StatsCommandHandler {

    private final StatisticsService statisticsService;
    private final AuditLogService auditLogService;

    public SendMessage handle(Message message, Long adminTelegramId) {
        try {
            // Получаем статистику
            StatisticsDto stats = statisticsService.getStatistics();

            // Логируем просмотр статистики
            auditLogService.log(adminTelegramId, "VIEW_STATS", Map.of());

            // Формируем текст со статистикой
            String text = String.join("\n",
                    BotMessage.STATS_TITLE.raw(),
                    "",
                    BotMessage.STATS_TOTAL_USERS.format(stats.getTotalUsers()),
                    BotMessage.STATS_NEW_TODAY.format(stats.getNewUsersToday()),
                    BotMessage.STATS_ACTIVE_USERS.format(stats.getActiveUsers()),
                    BotMessage.STATS_BLOCKED_USERS.format(stats.getBlockedUsers()),
                    BotMessage.STATS_TOTAL_ADMINS.format(stats.getTotalAdmins())
            );

            SendMessage response = new SendMessage(
                    message.getChatId().toString(),
                    text
            );
            response.setParseMode("HTML");

            return response;

        } catch (Exception e) {
            // Ошибка получения статистики
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_STATS_FETCH.format(e.getMessage())
            );
        }
    }
}
