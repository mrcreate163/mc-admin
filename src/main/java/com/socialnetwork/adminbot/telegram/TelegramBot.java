package com.socialnetwork.adminbot.telegram;

import com.socialnetwork.adminbot.config.TelegramBotConfig;
import com.socialnetwork.adminbot.service.AdminService;
import com.socialnetwork.adminbot.telegram.handler.*;
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
    private final StartCommandHandler startCommandHandler;
    private final UserCommandHandler userCommandHandler;
    private final StatsCommandHandler statsCommandHandler;
    private final BanCommandHandler banCommandHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final List<Long> adminWhitelist;

    public TelegramBot(
            TelegramBotConfig botConfig,
            AdminService adminService,
            StartCommandHandler startCommandHandler,
            UserCommandHandler userCommandHandler,
            StatsCommandHandler statsCommandHandler,
            BanCommandHandler banCommandHandler,
            CallbackQueryHandler callbackQueryHandler,
            @Value("${admin.whitelist}") String adminWhitelistStr
    ) {
        super(botConfig.getToken());
        this.botConfig = botConfig;
        this.adminService = adminService;
        this.startCommandHandler = startCommandHandler;
        this.userCommandHandler = userCommandHandler;
        this.statsCommandHandler = statsCommandHandler;
        this.banCommandHandler = banCommandHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        
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

        // Check if user is admin
        if (!isAuthorized(userId)) {
            sendUnauthorizedMessage(message.getChatId());
            return;
        }

        log.info("Processing command: {} from user: {}", text, userId);

        try {
            SendMessage response;
            
            if (text.startsWith("/start")) {
                response = startCommandHandler.handle(message);
            } else if (text.startsWith("/user")) {
                response = userCommandHandler.handle(message, userId);
            } else if (text.startsWith("/stats")) {
                response = statsCommandHandler.handle(message, userId);
            } else if (text.startsWith("/ban")) {
                response = banCommandHandler.handleBan(message, userId);
            } else if (text.startsWith("/unban")) {
                response = banCommandHandler.handleUnban(message, userId);
            } else {
                response = new SendMessage(
                        message.getChatId().toString(),
                        "❌ Unknown command. Use /start to see available commands."
                );
            }

            execute(response);
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getFrom().getId();

        // Check if user is admin
        if (!isAuthorized(userId)) {
            try {
                execute(callbackQueryHandler.createAnswer(
                        callbackQuery.getId(),
                        "❌ Unauthorized"
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
            execute(callbackQueryHandler.createAnswer(callbackQuery.getId(), "✅ Done"));
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
                "❌ Unauthorized. This bot is only available for administrators."
        );
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending unauthorized message: {}", e.getMessage(), e);
        }
    }
}
