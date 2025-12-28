package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import com.socialnetwork.adminbot.telegram.messages.MessageUtils;
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

            // Логируем действие администратора
            auditLogService.logAction(
                    "VIEW_USER_INFO",
                    adminId,
                    Map.of("userId", userId.toString())
            );

            // Строим сообщение с информацией
            return buildUserInfoMessage(message.getChatId(), account);

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

    /**
     * Формирует сообщение с информацией о пользователе
     */
    private SendMessage buildUserInfoMessage(Long chatId, AccountDto account) {
        // Безопасно форматируем все поля
        String firstName = MessageUtils.safeString(account.getFirstName());
        String lastName = MessageUtils.safeString(account.getLastName());
        String email = MessageUtils.safeString(account.getEmail());
        String city = MessageUtils.safeString(account.getCity());
        String country = MessageUtils.safeString(account.getCountry());
        String registered = account.getRegDate() != null
                ? MessageUtils.formatDate(account.getRegDate())
                : BotMessage.STATUS_UNKNOWN.raw();
        String blockedStatus = MessageUtils.formatStatus(account.getIsBlocked());
        String onlineStatus = MessageUtils.formatOnlineStatus(account.getIsOnline());

        // Собираем текст сообщения
        String text = String.join("\n",
                BotMessage.USER_INFO_TITLE.raw(),
                "",
                BotMessage.USER_INFO_NAME.format(firstName, lastName),
                BotMessage.USER_INFO_ID.format(account.getId()),
                BotMessage.USER_INFO_EMAIL.format(email),
                BotMessage.USER_INFO_CITY.format(city),
                BotMessage.USER_INFO_COUNTRY.format(country),
                BotMessage.USER_INFO_REGISTERED.format(registered),
                BotMessage.USER_INFO_BLOCKED.format(blockedStatus),
                BotMessage.USER_INFO_ONLINE.format(onlineStatus)
        );

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("HTML");

        // Добавляем клавиатуру с действиями (блокировка/разблокировка)
        message.setReplyMarkup(
                KeyboardBuilder.buildUserActionsKeyboard(
                        account.getId(),
                        Boolean.TRUE.equals(account.getIsBlocked())
                )
        );

        return message;
    }
}
