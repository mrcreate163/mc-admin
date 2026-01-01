package com.socialnetwork.adminbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {

    /**
     * Telegram User ID используем как primary Key
     */
    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    /**
     * Telegram username (Формат: @username)
     */
    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AdminRole role;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Telegram ID SUPER_ADMIN, который пригласил данного админа,
     * NULL для SUPER_ADMIN(добавление через whitelist)
     */
    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
