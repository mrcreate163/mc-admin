package com.socialnetwork.adminbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity для хранения пригласительных ссылок администраторов
 *
 * Workflow:
 * 1. SUPER_ADMIN создаёт приглашение через /addadmin
 * 2. Генерируется уникальный токен и ссылка
 * 3. Новый админ переходит по ссылке и активируется
 * 4. Приглашение помечается как использованное
 */
@Entity
@Table(name = "admin_invitations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInvitation {

    /**
     * Уникальный идентификатор приглашения
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    /**
     * Уникальный токен для deep link
     * Формат: "abc123xyz456" (без префикса "invite_")
     * Используется в ссылке: https://t.me/BotName?start=invite_TOKEN
     */
    @Column(name = "invite_token", nullable = false, unique = true, length = 64)
    private String inviteToken;

    /**
     * Роль, которая будет назначена новому администратору
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private AdminRole role;

    /**
     * Telegram User ID SUPER_ADMIN, создавшего приглашение
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * Срок действия приглашения
     * По умолчанию: текущее время + 24 часа
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Флаг использования приглашения
     * true = приглашение активировано и больше не может быть использовано
     */
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    /**
     * Telegram User ID администратора, активировавшего приглашение
     * null до момента активации
     */
    @Column(name = "activated_admin_id")
    private Long activatedAdminId;

    /**
     * Временная метка создания приглашения
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Временная метка использования приглашения
     * null до момента активации
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * Lifecycle callback: устанавливает createdAt при создании
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Проверить, валидно ли приглашение для активации
     *
     * @return true если приглашение не использовано и не истекло
     */
    public boolean isValid() {
        return !isUsed && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Пометить приглашение как использованное
     *
     * @param activatedAdminId Telegram ID активированного администратора
     */
    public void markAsUsed(Long activatedAdminId) {
        this.isUsed = true;
        this.activatedAdminId = activatedAdminId;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * Получить количество часов до истечения
     *
     * @return количество часов (может быть отрицательным если истекло)
     */
    public long getHoursUntilExpiry() {
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toHours();
    }
}
