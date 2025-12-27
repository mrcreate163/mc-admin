package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    public SendMessage handle(Message message) {
        // Получаем имя пользователя и безопасно экранируем
        String firstName = message.getFrom().getFirstName();
        String safeName = firstName != null
                ? BotMessage.escapeHtml(firstName)
                : "Админ";

        // Собираем приветственное сообщение из констант BotMessage
        String welcomeText = String.join("\n\n",
                BotMessage.WELCOME_TITLE.raw(),
                BotMessage.WELCOME_GREETING.format(safeName),
                BotMessage.WELCOME_COMMANDS.raw(),
                String.join("\n",
                        BotMessage.CMD_START.raw(),
                        BotMessage.CMD_USER.raw(),
                        BotMessage.CMD_BAN.raw(),
                        BotMessage.CMD_UNBAN.raw(),
                        BotMessage.CMD_STATS.raw()
                ),
                BotMessage.WELCOME_FOOTER.raw()
        );

        SendMessage response = new SendMessage(
                message.getChatId().toString(),
                welcomeText
        );
        response.setParseMode("HTML");
        response.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());

        return response;
    }
}
