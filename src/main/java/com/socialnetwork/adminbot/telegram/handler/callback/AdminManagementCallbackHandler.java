package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.telegram.handler.AddAdminCommandHandler;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Обработчик callback-запросов для управления администраторами.
 * Обрабатывает: add_admin:*
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminManagementCallbackHandler extends BaseCallbackHandler {

    private final AddAdminCommandHandler addAdminCommandHandler;

    @Override
    public boolean canHandle(String callbackData) {
        return callbackData.startsWith("add_admin:");
    }

    @Override
    public EditMessageText handle(CallbackQuery callbackQuery, Long chatId, Integer messageId, Long adminId) {
        String data = callbackQuery.getData();

        try {
            return handleAddAdminCallback(data, chatId, messageId, adminId);
        } catch (Exception e) {
            log.error("Error handling admin management callback: {}", e.getMessage(), e);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Обработка callback'ов для команды /addadmin.
     *
     * Callback data формат: "add_admin:action:param"
     * Примеры:
     * - "add_admin:role:MODERATOR" - выбор роли MODERATOR
     * - "add_admin:role:ADMIN" - выбор роли ADMIN
     * - "add_admin:cancel" - отмена создания приглашения
     */
    private EditMessageText handleAddAdminCallback(
            String data,
            Long chatId,
            Integer messageId,
            Long adminId
    ) {
        // Парсинг callback data: "add_admin:action:param"
        String[] parts = data.split(":");

        if (parts.length < 2) {
            log.warn("Invalid add_admin callback format: {}", data);
            return createErrorMessage(chatId, messageId,
                    BotMessage.ERROR_INVALID_FORMAT.raw());
        }

        String action = parts[1];

        // Отмена
        if ("cancel".equals(action)) {
            String cancelMessage = addAdminCommandHandler.cancelAddAdmin(adminId);

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(cancelMessage);
            message.setParseMode("HTML");

            return message;
        }

        // Выбор роли
        if ("role".equals(action) && parts.length == 3) {
            String roleName = parts[2];

            try {
                AdminRole selectedRole = AdminRole.valueOf(roleName);
                String responseText = addAdminCommandHandler.handleRoleSelection(adminId, selectedRole);

                // Создаем сообщение со ссылкой (без клавиатуры, т.к. ссылка одноразовая)
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId.toString());
                message.setMessageId(messageId);
                message.setText(responseText);
                message.setParseMode("HTML");
                message.setReplyMarkup(null); // Убираем клавиатуру после генерации ссылки

                return message;

            } catch (IllegalArgumentException e) {
                log.error("Invalid role name: {}", roleName, e);
                return createErrorMessage(chatId, messageId,
                        BotMessage.ERROR_INVALID_FORMAT.raw());
            }
        }

        // Неизвестное действие
        log.warn("Unknown add_admin action: {}", action);
        return createErrorMessage(chatId, messageId,
                BotMessage.ERROR_UNKNOWN_ACTION.raw());
    }
}
