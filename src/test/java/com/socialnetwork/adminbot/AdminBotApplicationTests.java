package com.socialnetwork.adminbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "telegram.bot.token=test-token",
        "telegram.bot.username=test-bot",
        "admin.whitelist=123456789",
        "spring.kafka.bootstrap-servers=",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "services.account.url=http://localhost:8081/internal/account"
})
class AdminBotApplicationTests {

    // Mock Redis connection factory since Redis is not available in tests
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    // Mock TelegramBotsApi to avoid actual bot registration
    @MockitoBean
    private TelegramBotsApi telegramBotsApi;

    @Test
    void contextLoads() {
        // Context loading test - verifies all beans can be created
    }

}
