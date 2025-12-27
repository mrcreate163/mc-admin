package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
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
    private final AuditLogService auditLogService;

    /**
     * Обработка команды блокировки пользователя
     */
    public SendMessage handleBan(Message message, Long adminTelegramId) {
        String[] parts = message.getText().split(" ");

        // Проверка наличия аргумента
        if (parts.length < 2) {
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.BAN_USAGE.raw()
            );
        }

        try {
            // Парсим UUID
            UUID userId = UUID.fromString(parts[1]);

            // Блокируем пользователя
            userService.blockUser(userId, adminTelegramId, "Manual ban");

            // Логируем действие
            auditLogService.logAction("BLOCK_USER", adminTelegramId, userId, Map.of("reason", "Manual ban").toString());

            // Возвращаем успешное сообщение
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.BAN_SUCCESS.format(userId)
            );

        } catch (IllegalArgumentException e) {
            // Ошибка формата UUID
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_INVALID_USER_ID.raw()
            );
        } catch (Exception e) {
            // Общая ошибка
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_GENERIC.format(e.getMessage())
            );
        }
    }

    /**
     * Обработка команды разблокировки пользователя
     */
    public SendMessage handleUnban(Message message, Long adminTelegramId) {
        String[] parts = message.getText().split(" ");

        // Проверка наличия аргумента
        if (parts.length < 2) {
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.UNBAN_USAGE.raw()
            );
        }

        try {
            // Парсим UUID
            UUID userId = UUID.fromString(parts[1]);

            // Разблокируем пользователя
            userService.unblockUser(userId, adminTelegramId);

            // Логируем действие
            auditLogService.logAction("UNBLOCK_USER", adminTelegramId, userId, Map.of("reason", "Manual unban").toString());

            // Возвращаем успешное сообщение
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.UNBAN_SUCCESS.format(userId)
            );

        } catch (IllegalArgumentException e) {
            // Ошибка формата UUID
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_INVALID_USER_ID.raw()
            );
        } catch (Exception e) {
            // Общая ошибка
            return new SendMessage(
                    message.getChatId().toString(),
                    BotMessage.ERROR_GENERIC.format(e.getMessage())
            );
        }
    }
}
