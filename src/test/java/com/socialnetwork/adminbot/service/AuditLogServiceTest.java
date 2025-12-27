package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.entity.AuditLog;
import com.socialnetwork.adminbot.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService Unit Tests")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private static final Long ADMIN_ID = 123456789L;
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final String ACTION_TYPE = "BLOCK_USER";

    private AuditLog savedAuditLog;

    @BeforeEach
    void setUp() {
        savedAuditLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .adminId(ADMIN_ID)
                .actionType(ACTION_TYPE)
                .targetUserId(TARGET_USER_ID)
                .details(new HashMap<>())
                .build();
    }

    @Test
    @DisplayName("logAction with targetUserId and reason - should save audit log")
    void logAction_WithTargetUserIdAndReason_ShouldSaveAuditLog() {
        // Given
        String reason = "Spam violation";
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        // When
        auditLogService.logAction(ACTION_TYPE, ADMIN_ID, TARGET_USER_ID, reason);

        // Then
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertThat(capturedLog.getAdminId()).isEqualTo(ADMIN_ID);
        assertThat(capturedLog.getActionType()).isEqualTo(ACTION_TYPE);
        assertThat(capturedLog.getTargetUserId()).isEqualTo(TARGET_USER_ID);
        assertThat(capturedLog.getDetails()).containsEntry("reason", reason);
    }

    @Test
    @DisplayName("logAction with targetUserId but no reason - should save audit log without reason")
    void logAction_WithTargetUserIdNoReason_ShouldSaveAuditLogWithoutReason() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        // When
        auditLogService.logAction(ACTION_TYPE, ADMIN_ID, TARGET_USER_ID, null);

        // Then
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertThat(capturedLog.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("logAction with details map - should save audit log with details")
    void logAction_WithDetailsMap_ShouldSaveAuditLogWithDetails() {
        // Given
        Map<String, Object> details = Map.of("source", "callback", "timestamp", "2024-01-01");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        // When
        auditLogService.logAction(ACTION_TYPE, ADMIN_ID, details);

        // Then
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertThat(capturedLog.getAdminId()).isEqualTo(ADMIN_ID);
        assertThat(capturedLog.getActionType()).isEqualTo(ACTION_TYPE);
        assertThat(capturedLog.getTargetUserId()).isNull();
        assertThat(capturedLog.getDetails()).containsEntry("source", "callback");
    }

    @Test
    @DisplayName("logAction with null details map - should save audit log with empty details")
    void logAction_WithNullDetailsMap_ShouldSaveAuditLogWithEmptyDetails() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        // When
        auditLogService.logAction(ACTION_TYPE, ADMIN_ID, (Map<String, Object>) null);

        // Then
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertThat(capturedLog.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("logAction simple - should save audit log with empty details")
    void logAction_Simple_ShouldSaveAuditLogWithEmptyDetails() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        // When
        auditLogService.logAction(ACTION_TYPE, ADMIN_ID);

        // Then
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertThat(capturedLog.getAdminId()).isEqualTo(ADMIN_ID);
        assertThat(capturedLog.getActionType()).isEqualTo(ACTION_TYPE);
        assertThat(capturedLog.getDetails()).isEmpty();
    }
}
