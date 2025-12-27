package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.exception.UserNotFoundException;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandHandler Unit Tests")
class UserCommandHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserCommandHandler userCommandHandler;

    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;
    private static final UUID USER_ID = UUID.randomUUID();
    private AccountDto testAccount;

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);
        when(mockMessage.getChatId()).thenReturn(CHAT_ID);

        testAccount = AccountDto.builder()
                .id(USER_ID)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .city("Moscow")
                .country("Russia")
                .regDate(LocalDateTime.now())
                .isOnline(true)
                .isBlocked(false)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("handle - should return usage message when no user id provided")
    void handle_WhenNoUserIdProvided_ShouldReturnUsageMessage() {
        // Given
        when(mockMessage.getText()).thenReturn("/user");

        // When
        SendMessage result = userCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("/user");
        verify(userService, never()).getUserById(any());
    }

    @Test
    @DisplayName("handle - should return user info when user found")
    void handle_WhenUserFound_ShouldReturnUserInfo() {
        // Given
        when(mockMessage.getText()).thenReturn("/user " + USER_ID);
        when(userService.getUserById(USER_ID)).thenReturn(testAccount);

        // When
        SendMessage result = userCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("John");
        assertThat(result.getText()).contains("Doe");
        assertThat(result.getText()).contains("test@example.com");
        assertThat(result.getParseMode()).isEqualTo("HTML");
        assertThat(result.getReplyMarkup()).isNotNull();
        verify(auditLogService).logAction(eq("VIEW_USER_INFO"), eq(ADMIN_TELEGRAM_ID), anyMap());
    }

    @Test
    @DisplayName("handle - should return error when invalid UUID format")
    void handle_WhenInvalidUuidFormat_ShouldReturnError() {
        // Given
        when(mockMessage.getText()).thenReturn("/user invalid-uuid");

        // When
        SendMessage result = userCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Неверный формат ID");
        verify(userService, never()).getUserById(any());
    }

    @Test
    @DisplayName("handle - should return error when user not found")
    void handle_WhenUserNotFound_ShouldReturnError() {
        // Given
        when(mockMessage.getText()).thenReturn("/user " + USER_ID);
        when(userService.getUserById(USER_ID)).thenThrow(new UserNotFoundException("User not found: " + USER_ID));

        // When
        SendMessage result = userCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Ошибка");
        assertThat(result.getText()).contains("User not found");
    }

    @Test
    @DisplayName("handle - should display blocked status correctly")
    void handle_WhenUserBlocked_ShouldShowBlockedStatus() {
        // Given
        testAccount.setIsBlocked(true);
        when(mockMessage.getText()).thenReturn("/user " + USER_ID);
        when(userService.getUserById(USER_ID)).thenReturn(testAccount);

        // When
        SendMessage result = userCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        // Check that blocked status is shown - using constant from BotMessage
        assertThat(result.getText()).contains(BotMessage.STATUS_YES.raw());
    }
}
