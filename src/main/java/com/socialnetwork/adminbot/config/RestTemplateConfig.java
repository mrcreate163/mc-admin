package com.socialnetwork.adminbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Настраиваем таймауты
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory();
        factory.setConnectionRequestTimeout(5);  // 5 секунд
        factory.setReadTimeout(10000);    // 10 секунд
        restTemplate.setRequestFactory(factory);

        log.info("RestTemplate configured for internal service communication");
        return restTemplate;
    }
}
