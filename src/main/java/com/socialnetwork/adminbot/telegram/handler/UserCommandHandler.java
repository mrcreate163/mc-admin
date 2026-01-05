package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import com.socialnetwork.adminbot.telegram.messages.MessageUtils;
import com.socialnetwork.adminbot.telegram.messages.TelegramMessageFactory;
import com.socialnetwork.adminbot.telegram.messages.UserInfoFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCommandHandler {

    private final UserService userService;
    private final AuditLogService auditLogService;

    public SendMessage handle(Message message, Long adminId) {
        String[] parts = message.getText().split(" ");

        // Проверка наличия аргумента
        if (parts.length < 2) {
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_USER_COMMAND_USAGE.raw()
            );
        }

        try {
            // Парсим UUID
            UUID userId = UUID.fromString(parts[1]);

            // Получаем данные пользователя
            AccountDto account = userService.getUserById(userId);
            log.debug("Status isBlocked = {}", account.getIsBlocked());

            // Логируем действие администратора
            auditLogService.logAction(
                    "VIEW_USER_INFO",
                    adminId,
                    Map.of("userId", userId.toString())
            );

            // Строим сообщение с информацией
            return TelegramMessageFactory.createHtmlMessage(
                    message.getChatId(), UserInfoFormatter.formatFullUserInfo(account));

        } catch (IllegalArgumentException e) {
            // Ошибка формата UUID
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_INVALID_USER_ID.raw()
            );
        } catch (Exception e) {
            // Общая ошибка (например, пользователь не найден)
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_GENERIC.format(e.getMessage())
            );
        }
    }
}
