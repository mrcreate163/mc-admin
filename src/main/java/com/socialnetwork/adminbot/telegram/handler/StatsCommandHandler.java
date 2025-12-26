package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.AuditService;
import com.socialnetwork.adminbot.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StatsCommandHandler {

    private final StatisticsService statisticsService;
    private final AuditService auditService;

    public SendMessage handle(Message message, Long adminTelegramId) {
        try {
            StatisticsDto stats = statisticsService.getStatistics();

            auditService.log(adminTelegramId, "VIEW_STATS", Map.of());

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

            SendMessage response = new SendMessage(message.getChatId().toString(), text);
            response.setParseMode("Markdown");
            return response;

        } catch (Exception e) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Error fetching statistics: " + e.getMessage()
            );
        }
    }
}
