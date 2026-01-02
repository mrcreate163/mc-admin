package com.socialnetwork.adminbot.service;


import com.socialnetwork.adminbot.dto.PendingInvitation;
import com.socialnetwork.adminbot.entity.AdminRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления приглашениями новых администраторов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {
    private final RedisTemplate<String, PendingInvitation> pendingInvitationRedisTemplate;

    private static final String INVITE_KEY_PREFIX = "telegram:invite:";
    private static final Duration INVITE_TTL = Duration.ofHours(24);

    /**
     * Создать новое приглашение для роли
     *
     * @param invitedBy Telegram ID приглашающего SUPER_ADMIN
     * @param role роль для нового админа
     * @return токен приглашения
     */
    public String createInvitation(Long invitedBy, AdminRole role) {
        return createInvitation(invitedBy, role, null);
    }

    /**
     * Создать новое приглашение с заметкой
     *
     * @param invitedBy Telegram ID приглашающего SUPER_ADMIN
     * @param role роль для нового админа
     * @param note дополнительная информация
     * @return токен приглашения
     */
    public String createInvitation(Long invitedBy, AdminRole role, String note) {
        // Генерируем уникальный токен
        String token = generateInviteToken();

        PendingInvitation invitation = PendingInvitation.builder()
                .inviteToken(token)
                .invitedBy(invitedBy)
                .role(role)
                .createdAt(LocalDateTime.now())
                .note(note)
                .build();

        // Сохраняем в Redis с TTL 24 часа
        String key = buildKey(token);
        pendingInvitationRedisTemplate.opsForValue().set(key, invitation, INVITE_TTL);

        log.info("Created invitation: token={}, invitedBy={}, role={}",
                token, invitedBy, role);
        return token;
    }

    /**
     * Получить приглашение по токену
     *
     * @param token токен приглашения
     * @return Optional с приглашением или empty если не найдено/истекло
     */
    public Optional<PendingInvitation> getInvitation(String token) {
        String key = buildKey(token);
        PendingInvitation invitation = pendingInvitationRedisTemplate.opsForValue().get(key);

        if (invitation == null) {
            log.debug("Invitation not found or expired: token={}", token);
            return Optional.empty();
        }

        log.debug("Retrieved invitation: token={}, role={}", token, invitation.getRole());
        return Optional.of(invitation);
    }

    /**
     * Использовать приглашение (удалить из Redis после активации)
     *
     * @param token токен приглашения
     * @return Optional с приглашением или empty если не найдено
     */
    public Optional<PendingInvitation> consumeInvitation(String token) {
        Optional<PendingInvitation> invitation = getInvitation(token);

        if (invitation.isPresent()) {
            String key = buildKey(token);
            pendingInvitationRedisTemplate.delete(key);
            log.info("Invitation consumed: token={}", token);
        }

        return invitation;
    }

    /**
     * Проверить, валиден ли токен приглашения
     *
     * @param token токен приглашения
     * @return true если приглашение существует и не истекло
     */
    public boolean isValidInvitation(String token) {
        return getInvitation(token).isPresent();
    }

    /**
     * Генерировать уникальный токен приглашения
     * Формат: 8 символов UUID (без дефисов)
     */
    private String generateInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Построить Redis ключ для токена
     */
    private String buildKey(String token) {
        return INVITE_KEY_PREFIX + token;
    }
}
