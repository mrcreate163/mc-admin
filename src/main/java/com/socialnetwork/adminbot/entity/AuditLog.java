package com.socialnetwork.adminbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Telegram User ID администратора
     */
    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    /**
     * UUID целевого пользователя из микросервиса mc-account
     */
    @Column(name = "target_user_id")
    private UUID targetUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Опционально: добавить FK constraint для Version 2.0
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "admin_id", insertable = false, updatable = false)
    // private Admin admin;
}
