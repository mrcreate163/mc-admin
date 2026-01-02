package com.socialnetwork.adminbot.dto;


import com.socialnetwork.adminbot.entity.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO для хранения pending приглашений в Redis
 * Используется для генерации одноразовых ссылок-приглашений
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInvitation implements Serializable {
    /**
     * Уникальный токен приглашения
     */
    private String inviteToken;

    /**
     * Telegram ID приглашающего SUPER_ADMIN
     */
    private Long invitedBy;

    /**
     * Роль, которая будет назначена новому админу
     */
    private AdminRole role;

    /**
     * Временная метка создания приглашения
     */
    private LocalDateTime createdAt;

    /**
     * Дополнительная информация (опционально)
     */
    private String note;
}
