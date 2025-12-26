package com.socialnetwork.adminbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
@Data
public class TelegramBotConfig {
    private String token;
    private String username;
}
