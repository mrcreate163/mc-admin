package com.socialnetwork.adminbot.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.socialnetwork.adminbot.entity.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Модель токена-приглашения для добавления нового администратора
 * Хранится в Redis с TTL 24 часа
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InviteToken {

    /**
     * Уникальный токен приглашения (UUID)
     */
    private String token;

    /**
     * Telegram username целевого пользователя (формат: @username)
     */
    private String targetUsername;

    /**
     * Роль, которую получит пользователь после активации
     */
    private AdminRole role;

    /**
     * Telegram ID SUPER_ADMIN, который создал приглашение
     */
    private Long createdBy;

    /**
     * Время создания токена
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Время истечения токена (createdAt + 24 часа)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    /**
     * Проверить, истёк ли токен
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Получить оставшееся время действия в часах
     */
    public long getHoursUntilExpiration() {
        if (isExpired()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toHours();
    }
}
