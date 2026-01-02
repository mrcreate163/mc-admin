package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.service.AdminService;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.InviteService;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;

/**
 * Handler для команды /start
 *
 * Поддерживает два режима:
 * 1. Обычный запуск: /start - показывает приветствие и команды
 * 2. Deep link: /start invite_XXXXX - активация администратора по приглашению
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    private final AdminService adminService;
    private final InviteService inviteService;
    private final AuditLogService auditLogService;

    /**
     * Обработка команды /start
     *
     * @param message Telegram сообщение
     * @return ответное сообщение
     */
    public SendMessage handle(Message message) {
        Long telegramId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String messageText = message.getText();

        log.info("Processing /start command from user: {}", telegramId);

        // Проверяем, есть ли deep link параметр
        String[] parts = messageText.split(" ", 2);

        if (parts.length > 1 && parts[1].startsWith("invite_")) {
            // Обработка регистрации по пригласительной ссылке
            String inviteToken = parts[1].substring("invite_".length());
            return handleInviteRegistration(message, telegramId, chatId, inviteToken);
        }

        // Обычное приветствие для существующих администраторов
        return handleRegularStart(message, telegramId, chatId);
    }

    /**
     * Обработка обычного старта (без deep link)
     * Показывает приветствие и доступные команды
     */
    private SendMessage handleRegularStart(Message message, Long telegramId, Long chatId) {
        // Проверяем, зарегистрирован ли администратор
        boolean isAdmin = adminService.isAdmin(telegramId);

        if (isAdmin) {
            // Администратор существует - показываем приветствие с ролью
            Admin admin = adminService.findByTelegramId(telegramId);

            auditLogService.logAction("BOT_START", telegramId,
                    Map.of("source", "regular_start"));

            String firstName = message.getFrom().getFirstName();
            String safeName = firstName != null
                    ? BotMessage.escapeHtml(firstName)
                    : "Администратор";

            String welcomeText = String.join("\n\n",
                    BotMessage.WELCOME_ADMIN.format(safeName),
                    BotMessage.YOUR_ROLE.format(admin.getRole().name()),
                    BotMessage.AVAILABLE_COMMANDS.raw()
            );

            SendMessage response = createMessage(chatId, welcomeText);
            response.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());

            return response;

        } else {
            // Пользователь не зарегистрирован - показываем сообщение об ошибке
            log.warn("Unauthorized user tried to access bot: {}", telegramId);

            String unauthorizedText = String.join("\n\n",
                    BotMessage.WELCOME_UNAUTHORIZED.raw(),
                    BotMessage.CONTACT_SUPER_ADMIN.raw()
            );

            return createMessage(chatId, unauthorizedText);
        }
    }

    /**
     * Обработка регистрации по пригласительной ссылке
     *
     * @param message исходное сообщение
     * @param telegramId Telegram User ID
     * @param chatId Telegram Chat ID
     * @param inviteToken токен из ссылки (без префикса invite_)
     * @return сообщение с результатом активации
     */
    private SendMessage handleInviteRegistration(
            Message message,
            Long telegramId,
            Long chatId,
            String inviteToken
    ) {
        log.info("Processing invite registration: token={}, telegramId={}",
                inviteToken, telegramId);

        try {
            // Проверяем, не активирован ли уже этот пользователь
            if (adminService.isAdmin(telegramId)) {
                log.warn("Already registered admin tried to use invite link: {}", telegramId);
                return createMessage(chatId, BotMessage.ERROR_ALREADY_ADMIN.raw());
            }

            // Получаем данные пользователя из Telegram
            String username = message.getFrom().getUserName();
            String firstName = message.getFrom().getFirstName();

            if (firstName == null || firstName.isBlank()) {
                firstName = "Admin"; // Fallback если имя не указано
            }

            // Активируем администратора через InviteService
            Admin activatedAdmin = inviteService.activateInvitation(
                    inviteToken,
                    telegramId,
                    username,
                    firstName
            );

            log.info("Successfully activated admin via invite: id={}, role={}",
                    activatedAdmin.getTelegramUserId(), activatedAdmin.getRole());

            // Формируем приветственное сообщение
            String successText = String.join("\n\n",
                    BotMessage.REGISTRATION_SUCCESS.raw(),
                    BotMessage.REGISTRATION_YOUR_ROLE.format(
                            activatedAdmin.getRole().name(),
                            activatedAdmin.getTelegramUserId()
                    ),
                    "",
                    BotMessage.AVAILABLE_COMMANDS.raw()
            );

            SendMessage response = createMessage(chatId, successText);
            response.setReplyMarkup(KeyboardBuilder.buildMainMenuKeyboard());

            return response;

        } catch (IllegalArgumentException e) {
            // Ошибка валидации токена (невалидный, истёк, или уже использован)
            log.warn("Invalid invite token: {}, error: {}", inviteToken, e.getMessage());

            return createMessage(chatId,
                    "❌ <b>Ошибка активации</b>\n\n" + e.getMessage());

        } catch (Exception e) {
            // Общая ошибка
            log.error("Error during invite registration: token={}, error={}",
                    inviteToken, e.getMessage(), e);

            return createMessage(chatId, BotMessage.ERROR_REGISTRATION_FAILED.raw());
        }
    }

    /**
     * Создать SendMessage с HTML форматированием
     */
    private SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }
}
