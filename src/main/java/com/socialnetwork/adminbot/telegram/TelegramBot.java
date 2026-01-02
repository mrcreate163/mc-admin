package com.socialnetwork.adminbot.telegram;

import com.socialnetwork.adminbot.config.TelegramBotConfig;
import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.service.AdminService;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.telegram.handler.*;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final TelegramBotConfig botConfig;
    private final AdminService adminService;
    private final ConversationStateService conversationStateService;
    private final StartCommandHandler startCommandHandler;
    private final UserCommandHandler userCommandHandler;
    private final StatsCommandHandler statsCommandHandler;
    private final BanCommandHandler banCommandHandler;
    private final SearchCommandHandler searchCommandHandler;
    private final AddAdminCommandHandler addAdminCommandHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final TextMessageHandler textMessageHandler;
    private final List<Long> adminWhitelist;

    public TelegramBot(
            TelegramBotConfig botConfig,
            AdminService adminService,
            ConversationStateService conversationStateService,
            StartCommandHandler startCommandHandler,
            UserCommandHandler userCommandHandler,
            StatsCommandHandler statsCommandHandler,
            BanCommandHandler banCommandHandler,
            SearchCommandHandler searchCommandHandler,
            AddAdminCommandHandler addAdminCommandHandler,
            CallbackQueryHandler callbackQueryHandler,
            TextMessageHandler textMessageHandler,
            @Value("${admin.whitelist}") String adminWhitelistStr
    ) {
        super(botConfig.getToken());
        this.botConfig = botConfig;
        this.adminService = adminService;
        this.conversationStateService = conversationStateService;
        this.startCommandHandler = startCommandHandler;
        this.userCommandHandler = userCommandHandler;
        this.statsCommandHandler = statsCommandHandler;
        this.banCommandHandler = banCommandHandler;
        this.searchCommandHandler = searchCommandHandler;
        this.addAdminCommandHandler = addAdminCommandHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        this.textMessageHandler = textMessageHandler;

        // Parse admin whitelist from configuration
        this.adminWhitelist = Arrays.stream(adminWhitelistStr.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .toList();

        log.info("TelegramBot initialized with {} whitelisted admins", adminWhitelist.size());
    }

    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Error processing update: {}", e.getMessage(), e);
        }
    }

    private void handleMessage(Message message) {
        Long userId = message.getFrom().getId();
        String text = message.getText();

        // ⭐ КРИТИЧНО: Проверяем deep link ПЕРЕД авторизацией
        if (text != null && text.startsWith("/start invite_")) {
            log.info("Deep link detected for user: {}, bypassing authorization check", userId);
            try {
                SendMessage response = startCommandHandler.handle(message);
                execute(response);
            } catch (TelegramApiException e) {
                log.error("Error sending message: {}", e.getMessage(), e);
            }
            return; // ⬅️ Выходим ДО проверки авторизации
        }

        // Проверка авторизации для всех остальных команд
        if (!isAuthorized(userId)) {
            sendUnauthorizedMessage(message.getChatId());
            return;
        }

        log.info("Processing message: {} from user: {}", text, userId);
        try {
            SendMessage response;

            // Проверяем, является ли сообщение командой
            if (text.startsWith("/")) {
                response = handleCommand(message, userId);
            } else {
                // Обрабатываем как текстовое сообщение (для stateful диалогов)
                response = textMessageHandler.handle(message, userId);
            }

            execute(response);
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка команд с учётом State Machine
     */
    private SendMessage handleCommand(Message message, Long userId) {
        String text = message.getText();

        // Глобальные команды - сбрасывают состояние
        if (text.startsWith("/start")) {
            conversationStateService.resetToIdle(userId);
            return startCommandHandler.handle(message);
        }

        if (text.startsWith("/cancel")) {
            conversationStateService.resetToIdle(userId);
            return createMessage(message.getChatId(),
                    "✅ Текущее действие отменено. Вы вернулись в главное меню.");
        }

        // Проверяем, находится ли пользователь в активном диалоге
        BotState currentState = conversationStateService.getCurrentState(userId);
        if (currentState != BotState.IDLE) {
            // Пользователь в диалоге, но вводит новую команду
            return createMessage(message.getChatId(),
                    "⚠️ У вас есть незавершённое действие. " +
                            "Используйте /cancel для отмены или продолжите текущее действие.");
        }

        // Роутинг команд (для IDLE состояния)
        if (text.startsWith("/user")) {
            return userCommandHandler.handle(message, userId);
        } else if (text.startsWith("/stats")) {
            return statsCommandHandler.handle(message, userId);
        } else if (text.startsWith("/ban")) {
            return banCommandHandler.handle(message, userId);
        } else if (text.startsWith("/unban")) {
            return banCommandHandler.handleUnban(message, userId);
        } else if (text.startsWith("/search")) {
            return searchCommandHandler.handle(message, userId);
        } else if (text.startsWith("/addadmin")) {
            return addAdminCommandHandler.handle(message, userId);
        } else {
            return createMessage(message.getChatId(),
                    BotMessage.ERROR_UNKNOWN_COMMAND.raw());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getFrom().getId();

        // Check if user is admin
        if (!isAuthorized(userId)) {
            try {
                execute(callbackQueryHandler.createAnswer(
                        callbackQuery.getId(),
                        BotMessage.CALLBACK_UNAUTHORIZED.raw()
                ));
            } catch (TelegramApiException e) {
                log.error("Error sending callback answer: {}", e.getMessage(), e);
            }
            return;
        }

        log.info("Processing callback: {} from user: {}", callbackQuery.getData(), userId);
        try {
            EditMessageText response = callbackQueryHandler.handle(callbackQuery, userId);
            execute(response);
            execute(callbackQueryHandler.createAnswer(callbackQuery.getId(),
                    BotMessage.CALLBACK_SUCCESS.raw()));
        } catch (TelegramApiException e) {
            log.error("Error handling callback query: {}", e.getMessage(), e);
        }
    }

    private boolean isAuthorized(Long userId) {
        // Check whitelist first
        if (adminWhitelist.contains(userId)) {
            return true;
        }

        // Then check database
        return adminService.isAdmin(userId);
    }

    private void sendUnauthorizedMessage(Long chatId) {
        SendMessage message = new SendMessage(
                chatId.toString(),
                BotMessage.ERROR_UNAUTHORIZED.raw()
        );
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending unauthorized message: {}", e.getMessage(), e);
        }
    }

    private SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }
}
