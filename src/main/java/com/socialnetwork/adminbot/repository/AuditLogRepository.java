package com.socialnetwork.adminbot.repository;

import com.socialnetwork.adminbot.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    List<AuditLog> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId);

    List<AuditLog> findByCreatedAtAfter(LocalDateTime dateTime);

    Long countByActionTypeAndCreatedAtAfter(String actionType, LocalDateTime dateTime);
}
