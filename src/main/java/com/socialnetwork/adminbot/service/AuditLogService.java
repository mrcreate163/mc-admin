package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.entity.AuditLog;
import com.socialnetwork.adminbot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для логирования действий администраторов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Логировать действие администратора с целевым пользователем и дополнительными деталями
     *
     * @param actionType тип действия (например, "BLOCK_USER", "UNBLOCK_USER")
     * @param adminId Telegram ID администратора
     * @param targetUserId ID целевого пользователя (может быть null)
     * @param reason причина действия (может быть null)
     */
    @Transactional
    public void logAction(String actionType, Long adminId, UUID targetUserId, String reason) {
        Map<String, Object> details = new HashMap<>();
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason);
        }

        AuditLog auditLog = AuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .details(details)
                .build();

        auditLogRepository.save(auditLog);

        log.info("Audit log created: adminId={}, action={}, targetUser={}, details={}",
                adminId, actionType, targetUserId, details);
    }

    /**
     * Логировать действие администратора без целевого пользователя
     *
     * @param actionType тип действия
     * @param adminId Telegram ID администратора
     * @param details дополнительные детали
     */
    @Transactional
    public void logAction(String actionType, Long adminId, Map<String, Object> details) {
        AuditLog auditLog = AuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(null)
                .details(details != null ? details : new HashMap<>())
                .build();

        auditLogRepository.save(auditLog);

        log.info("Audit log created: adminId={}, action={}, details={}",
                adminId, actionType, details);
    }

    /**
     * Логировать простое действие без деталей
     *
     * @param actionType тип действия
     * @param adminId Telegram ID администратора
     */
    @Transactional
    public void logAction(String actionType, Long adminId) {
        logAction(actionType, adminId, new HashMap<>());
    }
}
