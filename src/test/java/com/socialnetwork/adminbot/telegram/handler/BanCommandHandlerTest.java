package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BanCommandHandler Unit Tests")
class BanCommandHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BanCommandHandler banCommandHandler;

    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);
        when(mockMessage.getChatId()).thenReturn(CHAT_ID);
    }

    // ========== BAN TESTS ==========

    @Test
    @DisplayName("handleBan - should return usage message when no user id provided")
    void handleBan_WhenNoUserIdProvided_ShouldReturnUsageMessage() {
        // Given
        when(mockMessage.getText()).thenReturn("/ban");

        // When
        SendMessage result = banCommandHandler.handleBan(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("/ban");
        verify(userService, never()).blockUser(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("handleBan - should block user successfully")
    void handleBan_WhenValidUserId_ShouldBlockUser() {
        // Given
        when(mockMessage.getText()).thenReturn("/ban " + USER_ID);
        doNothing().when(userService).blockUser(eq(USER_ID), eq(ADMIN_TELEGRAM_ID), anyString());

        // When
        SendMessage result = banCommandHandler.handleBan(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("успешно заблокирован");
        assertThat(result.getText()).contains(USER_ID.toString());
        verify(userService).blockUser(eq(USER_ID), eq(ADMIN_TELEGRAM_ID), eq("Manual ban"));
        verify(auditLogService).logAction(eq("BLOCK_USER"), eq(ADMIN_TELEGRAM_ID), eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("handleBan - should return error when invalid UUID format")
    void handleBan_WhenInvalidUuidFormat_ShouldReturnError() {
        // Given
        when(mockMessage.getText()).thenReturn("/ban invalid-uuid");

        // When
        SendMessage result = banCommandHandler.handleBan(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Неверный формат ID");
        verify(userService, never()).blockUser(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("handleBan - should return error on service exception")
    void handleBan_WhenServiceException_ShouldReturnError() {
        // Given
        when(mockMessage.getText()).thenReturn("/ban " + USER_ID);
        doThrow(new RuntimeException("Service error")).when(userService).blockUser(any(), anyLong(), anyString());

        // When
        SendMessage result = banCommandHandler.handleBan(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Ошибка");
        assertThat(result.getText()).contains("Service error");
    }

    // ========== UNBAN TESTS ==========

    @Test
    @DisplayName("handleUnban - should return usage message when no user id provided")
    void handleUnban_WhenNoUserIdProvided_ShouldReturnUsageMessage() {
        // Given
        when(mockMessage.getText()).thenReturn("/unban");

        // When
        SendMessage result = banCommandHandler.handleUnban(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("/unban");
        verify(userService, never()).unblockUser(any(), anyLong());
    }

    @Test
    @DisplayName("handleUnban - should unblock user successfully")
    void handleUnban_WhenValidUserId_ShouldUnblockUser() {
        // Given
        when(mockMessage.getText()).thenReturn("/unban " + USER_ID);
        doNothing().when(userService).unblockUser(eq(USER_ID), eq(ADMIN_TELEGRAM_ID));

        // When
        SendMessage result = banCommandHandler.handleUnban(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("успешно разблокирован");
        assertThat(result.getText()).contains(USER_ID.toString());
        verify(userService).unblockUser(eq(USER_ID), eq(ADMIN_TELEGRAM_ID));
        verify(auditLogService).logAction(eq("UNBLOCK_USER"), eq(ADMIN_TELEGRAM_ID), eq(USER_ID), anyString());
    }

    @Test
    @DisplayName("handleUnban - should return error when invalid UUID format")
    void handleUnban_WhenInvalidUuidFormat_ShouldReturnError() {
        // Given
        when(mockMessage.getText()).thenReturn("/unban invalid-uuid");

        // When
        SendMessage result = banCommandHandler.handleUnban(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Неверный формат ID");
        verify(userService, never()).unblockUser(any(), anyLong());
    }
}
