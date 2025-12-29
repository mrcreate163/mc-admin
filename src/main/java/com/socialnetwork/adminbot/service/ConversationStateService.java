package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис для управления состояниями диалогов в Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStateService {

    private final RedisTemplate<String, ConversationState> conversationStateRedisTemplate;

    /**
     * Префикс для ключей состояний в Redis
     */
    private static final String STATE_KEY_PREFIX = "telegram:state:";

    /**
     * TTL для состояний по умолчанию (30 минут)
     */
    private static final Duration DEFAULT_STATE_TTL = Duration.ofMinutes(30);

    /**
     * Получить текущее состояние пользователя
     * Если состояние не найдено, возвращает IDLE
     */
    public ConversationState getState(Long telegramUserId) {
        String key = buildKey(telegramUserId);
        ConversationState state = conversationStateRedisTemplate.opsForValue().get(key);

        if (state == null) {
            log.debug("No state found for user {}. Returning IDLE", telegramUserId);
            return ConversationState.idle();
        }

        log.debug("Retrieved state for user {}: {}", telegramUserId, state.getState());
        return state;
    }

    /**
     * Получить текущее состояние как Optional
     */
    public Optional<ConversationState> getStateOptional(Long telegramUserId) {
        String key = buildKey(telegramUserId);
        return Optional.ofNullable(conversationStateRedisTemplate.opsForValue().get(key));
    }

    /**
     * Установить новое состояние для пользователя
     * Автоматически устанавливает TTL и обновляет timestamp
     */
    public void setState(Long telegramUserId, ConversationState state) {
        setState(telegramUserId, state, DEFAULT_STATE_TTL);
    }

    /**
     * Установить новое состояние с кастомным TTL
     */
    public void setState(Long telegramUserId, ConversationState state, Duration ttl) {
        String key = buildKey(telegramUserId);

        // Обновляем timestamp
        state.setUpdatedAt(LocalDateTime.now());
        if (state.getCreatedAt() == null) {
            state.setCreatedAt(LocalDateTime.now());
        }

        conversationStateRedisTemplate.opsForValue().set(key, state, ttl);

        log.debug("Set state for user {}: {} (TTL: {})",
                telegramUserId, state.getState(), ttl);
    }

    /**
     * Перевести пользователя в новое состояние
     * Сохраняет текущие данные контекста
     */
    public void transitionTo(Long telegramUserId, BotState newState) {
        ConversationState currentState = getState(telegramUserId);
        currentState.setState(newState);
        currentState.incrementVersion();
        setState(telegramUserId, currentState);

        log.info("User {} transitioned to state: {}", telegramUserId, newState);
    }

    /**
     * Перевести пользователя в новое состояние, очистив данные
     */
    public void transitionToWithClear(Long telegramUserId, BotState newState) {
        ConversationState newStateObj = ConversationState.builder()
                .state(newState)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        setState(telegramUserId, newStateObj);

        log.info("User {} transitioned to state: {} (data cleared)", telegramUserId, newState);
    }

    /**
     * Обновить данные в текущем состоянии
     */
    public void updateStateData(Long telegramUserId, String key, Object value) {
        ConversationState state = getState(telegramUserId);
        state.addData(key, value);
        state.incrementVersion();
        setState(telegramUserId, state);

        log.debug("Updated state data for user {}: {}={}", telegramUserId, key, value);
    }

    /**
     * Сбросить состояние пользователя в IDLE
     */
    public void resetToIdle(Long telegramUserId) {
        transitionToWithClear(telegramUserId, BotState.IDLE);
        log.info("User {} state reset to IDLE", telegramUserId);
    }

    /**
     * Удалить состояние пользователя из Redis
     */
    public void clearState(Long telegramUserId) {
        String key = buildKey(telegramUserId);
        conversationStateRedisTemplate.delete(key);
        log.debug("Cleared state for user {}", telegramUserId);
    }

    /**
     * Проверить, находится ли пользователь в определённом состоянии
     */
    public boolean isInState(Long telegramUserId, BotState expectedState) {
        ConversationState state = getState(telegramUserId);
        return state.getState() == expectedState;
    }

    /**
     * Проверить, находится ли пользователь в состоянии IDLE
     */
    public boolean isIdle(Long telegramUserId) {
        return isInState(telegramUserId, BotState.IDLE);
    }

    /**
     * Получить текущее состояние (enum)
     */
    public BotState getCurrentState(Long telegramUserId) {
        return getState(telegramUserId).getState();
    }

    /**
     * Построить ключ для Redis
     */
    private String buildKey(Long telegramUserId) {
        return STATE_KEY_PREFIX + telegramUserId;
    }
}
