package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    public SendMessage handle(Message message) {
        String welcomeText = String.format(
                "👋 *Welcome to Admin Bot!*\n\n" +
                "Hello, %s!\n\n" +
                "Available commands:\n" +
                "/start - Show this message\n" +
                "/user <user_id> - View user information\n" +
                "/ban <user_id> - Block user\n" +
                "/unban <user_id> - Unblock user\n" +
                "/stats - View platform statistics\n\n" +
                "Use the menu below for quick actions.",
                message.getFrom().getFirstName() != null ? message.getFrom().getFirstName() : "Admin"
        );

        SendMessage response = new SendMessage(message.getChatId().toString(), welcomeText);
        response.setParseMode("Markdown");
        response.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());
        return response;
    }
}
