package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.service.AuditService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserCommandHandler {

    private final UserService userService;
    private final AuditService auditService;

    public SendMessage handle(Message message, Long adminTelegramId) {
        String[] parts = message.getText().split(" ");
        if (parts.length < 2) {
            return new SendMessage(
                    message.getChatId().toString(),
                    "❌ Usage: /user <user_id>"
            );
        }

        try {
            UUID userId = UUID.fromString(parts[1]);
            AccountDto account = userService.getUserById(userId);

            auditService.log(adminTelegramId, "VIEW_USER", userId, Map.of());

            return buildUserInfoMessage(message.getChatId(), account);
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

    private SendMessage buildUserInfoMessage(Long chatId, AccountDto account) {
        String text = String.format(
                "👤 *User Information*\n\n" +
                "Name: %s %s\n" +
                "ID: `%s`\n" +
                "Email: %s\n" +
                "City: %s\n" +
                "Country: %s\n" +
                "Registered: %s\n" +
                "Blocked: %s\n" +
                "Online: %s",
                account.getFirstName() != null ? account.getFirstName() : "N/A",
                account.getLastName() != null ? account.getLastName() : "",
                account.getId(),
                account.getEmail() != null ? account.getEmail() : "N/A",
                account.getCity() != null ? account.getCity() : "N/A",
                account.getCountry() != null ? account.getCountry() : "N/A",
                account.getRegDate() != null ? account.getRegDate().toString() : "N/A",
                Boolean.TRUE.equals(account.getIsBlocked()) ? "Yes ❌" : "No ✅",
                Boolean.TRUE.equals(account.getIsOnline()) ? "Yes 🟢" : "No ⚫"
        );

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(
                KeyboardBuilder.buildUserActionsKeyboard(
                        account.getId(),
                        Boolean.TRUE.equals(account.getIsBlocked())
                )
        );
        return message;
    }
}
