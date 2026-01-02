package com.socialnetwork.adminbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.socialnetwork.adminbot.domain.InviteToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления токенами-приглашениями в Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String INVITE_TOKEN_PREFIX = "invite:token:";
    private static final String INVITE_USERNAME_PREFIX = "invite:username:";
    private static final Duration INVITE_TTL = Duration.ofHours(24);

    /**
     * Сохранить токен-приглашение в Redis
     */
    public void saveInvite(InviteToken invite) {
        try {
            String tokenKey = INVITE_TOKEN_PREFIX + invite.getToken();
            String usernameKey = INVITE_USERNAME_PREFIX + invite.getTargetUsername().toLowerCase();

            String json = objectMapper.writeValueAsString(invite);

            // Сохраняем токен с TTL 24 часа
            redisTemplate.opsForValue().set(tokenKey, json, INVITE_TTL);

            // Сохраняем маппинг username -> token (для проверки дубликатов)
            redisTemplate.opsForValue().set(usernameKey, invite.getToken(), INVITE_TTL);

            log.info("Invite saved: token={}, username={}, role={}, expiresAt={}",
                    invite.getToken(), invite.getTargetUsername(), invite.getRole(), invite.getExpiresAt());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize invite token", e);
            throw new RuntimeException("Failed to save invite", e);
        }
    }

    /**
     * Получить токен-приглашение по токену
     */
    public InviteToken getInviteByToken(String token) {
        try {
            String key = INVITE_TOKEN_PREFIX + token;
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                log.debug("Invite token not found: {}", token);
                return null;
            }

            InviteToken invite = objectMapper.readValue(json, InviteToken.class);

            // Проверяем истечение (дополнительная проверка, Redis TTL должен сам удалять)
            if (invite.isExpired()) {
                log.warn("Invite token expired: {}", token);
                deleteInvite(token);
                return null;
            }

            return invite;

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize invite token: {}", token, e);
            throw new RuntimeException("Failed to read invite", e);
        }
    }

    /**
     * Проверить, есть ли активное приглашение для username
     */
    public boolean hasActivePendingInvite(String username) {
        String key = INVITE_USERNAME_PREFIX + username.toLowerCase();
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey);
    }

    /**
     * Удалить токен-приглашение
     */
    public void deleteInvite(String token) {
        try {
            String tokenKey = INVITE_TOKEN_PREFIX + token;
            String json = redisTemplate.opsForValue().get(tokenKey);
            
            if (json != null) {
                InviteToken invite = objectMapper.readValue(json, InviteToken.class);
                String usernameKey = INVITE_USERNAME_PREFIX + invite.getTargetUsername().toLowerCase();
                
                redisTemplate.delete(tokenKey);
                redisTemplate.delete(usernameKey);
                
                log.info("Invite deleted: token={}, username={}", token, invite.getTargetUsername());
            } else {
                // Token already deleted, just try to clean up
                redisTemplate.delete(tokenKey);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize invite token during delete: {}", token, e);
            log.warn("Username mapping for token {} might remain orphaned", token);
            // Still try to delete the token key
            redisTemplate.delete(INVITE_TOKEN_PREFIX + token);
        }
    }

    /**
     * Получить все активные приглашения
     */
    public List<InviteToken> getAllActiveInvites() {
        Set<String> keys = redisTemplate.keys(INVITE_TOKEN_PREFIX + "*");

        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        return keys.stream()
                .map(key -> {
                    String json = redisTemplate.opsForValue().get(key);
                    try {
                        return objectMapper.readValue(json, InviteToken.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize invite: key={}", key, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(invite -> !invite.isExpired()) // дополнительная фильтрация
                .collect(Collectors.toList());
    }

    /**
     * Попытка залочить токен для активации (защита от race condition)
     */
    public boolean tryLockInvite(String token) {
        String lockKey = "invite:lock:" + token;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofMinutes(5));
        return Boolean.TRUE.equals(locked);
    }

    /**
     * Разлочить токен
     */
    public void unlockInvite(String token) {
        String lockKey = "invite:lock:" + token;
        redisTemplate.delete(lockKey);
    }
}
