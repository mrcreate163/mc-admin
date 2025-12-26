package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.entity.AuditLog;
import com.socialnetwork.adminbot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(Long adminId, String actionType, UUID targetUserId, Map<String, Object> details) {
        AuditLog auditLog = AuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .details(details)
                .build();

        auditLogRepository.save(auditLog);
        log.info("Audit log created: adminId={}, action={}, targetUser={}", 
                adminId, actionType, targetUserId);
    }

    @Transactional
    public void log(Long adminId, String actionType, Map<String, Object> details) {
        log(adminId, actionType, null, details);
    }
}
