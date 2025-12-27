package com.socialnetwork.adminbot.config;

import com.socialnetwork.adminbot.telegram.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramBotRegistration {

    private final TelegramBotConfig botConfig;

    /**
     * Регистрирует бота в Telegram API и запускает Polling
     */
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBot telegramBot) throws TelegramApiException {
        log.info("Registering Telegram bot: {}", botConfig.getUsername());
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(telegramBot);
        log.info("✅Telegram bot {} registered successfully and started polling", botConfig.getUsername());
        return botsApi;
    }
}
