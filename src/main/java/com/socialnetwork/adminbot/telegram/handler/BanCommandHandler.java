package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.service.AuditService;
import com.socialnetwork.adminbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BanCommandHandler {

    private final UserService userService;
    private final AuditService auditService;

    public SendMessage handleBan(Message message, Long adminTelegramId) {
        String[] parts = message.getText().split(" ");
        if (parts.length < 2) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Usage: /ban <user_id>"
            );
        }

        try {
            UUID userId = UUID.fromString(parts[1]);
            userService.blockUser(userId);

            auditService.log(adminTelegramId, "BLOCK_USER", userId, Map.of("reason", "Manual ban"));

            return new SendMessage(
                    message.getChatId().toString(),
                    "✅ User " + userId + " has been blocked successfully."
            );
        } catch (IllegalArgumentException e) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Invalid user ID format. Please provide a valid UUID."
            );
        } catch (Exception e) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Error: " + e.getMessage()
            );
        }
    }

    public SendMessage handleUnban(Message message, Long adminTelegramId) {
        String[] parts = message.getText().split(" ");
        if (parts.length < 2) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Usage: /unban <user_id>"
            );
        }

        try {
            UUID userId = UUID.fromString(parts[1]);
            userService.unblockUser(userId);

            auditService.log(adminTelegramId, "UNBLOCK_USER", userId, Map.of("reason", "Manual unban"));

            return new SendMessage(
                    message.getChatId().toString(),
                    "✅ User " + userId + " has been unblocked successfully."
            );
        } catch (IllegalArgumentException e) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Invalid user ID format. Please provide a valid UUID."
            );
        } catch (Exception e) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Error: " + e.getMessage()
            );
        }
    }
}
