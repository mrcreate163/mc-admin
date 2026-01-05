package com.socialnetwork.adminbot.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Сервис для управления Rate Limiting через Bucket4j.
 * Использует Token Bucket алгоритм с хранением в Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    /**
     * Проверяет, разрешён ли запрос для данного ключа (например, IP-адреса).
     *
     * @param key уникальный идентификатор (IP, user ID, etc.)
     * @param configSupplier конфигурация bucket'а (лимиты)
     * @return true если запрос разрешён, false если превышен лимит
     */
    public boolean tryConsume(String key, Supplier<BucketConfiguration> configSupplier) {
        var bucket = proxyManager.builder().build(key, configSupplier);
        boolean consumed = bucket.tryConsume(1);

        if (!consumed) {
            log.warn("Rate limit exceeded for key: {}", key);
        }

        return consumed;
    }

    /**
     * Конфигурация для endpoint'а регистрации.
     * Лимит: 3 запроса в час.
     */
    public static BucketConfiguration registrationLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)  // максимум 3 запроса
                        .refillGreedy(3, Duration.ofHours(1))  // восстанавливается 3 токена каждый час
                        .build())
                .build();
    }

    /**
     * Конфигурация для endpoint'а валидации токенов.
     * Лимит: 10 запросов в минуту.
     */
    public static BucketConfiguration validationLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)  // максимум 10 запросов
                        .refillGreedy(10, Duration.ofMinutes(1))  // восстанавливается 10 токенов каждую минуту
                        .build())
                .build();
    }

    /**
     * Конфигурация для строгого лимита (критичные операции).
     * Лимит: 5 запросов в 5 минут.
     */
    public static BucketConfiguration strictLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillGreedy(5, Duration.ofMinutes(5))
                        .build())
                .build();
    }

    /**
     * Получает количество оставшихся доступных токенов.
     * Полезно для информирования пользователя.
     */
    public long getAvailableTokens(String key, Supplier<BucketConfiguration> configSupplier) {
        var bucket = proxyManager.builder().build(key, configSupplier);
        return bucket.getAvailableTokens();
    }
}
