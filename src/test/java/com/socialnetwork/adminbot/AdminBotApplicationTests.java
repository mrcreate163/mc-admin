package com.socialnetwork.adminbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "telegram.bot.token=test-token",
        "telegram.bot.username=test-bot",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class AdminBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
