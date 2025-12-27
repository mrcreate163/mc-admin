package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.client.AccountClient;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private AccountClient accountClient;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    private AccountDto testAccount;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;

    @BeforeEach
    void setUp() {
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
    @DisplayName("getUserById - should return account when found")
    void getUserById_WhenUserExists_ShouldReturnAccount() {
        // Given
        when(accountClient.getAccountById(USER_ID)).thenReturn(testAccount);

        // When
        AccountDto result = userService.getUserById(USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(USER_ID);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(accountClient).getAccountById(USER_ID);
    }

    @Test
    @DisplayName("blockUser - should block user and log action")
    void blockUser_ShouldBlockUserAndLogAction() {
        // Given
        String reason = "Spam violation";
        doNothing().when(accountClient).blockAccount(USER_ID);

        // When
        userService.blockUser(USER_ID, ADMIN_TELEGRAM_ID, reason);

        // Then
        verify(accountClient).blockAccount(USER_ID);
        verify(auditLogService).logAction(eq("BLOCK_USER"), eq(ADMIN_TELEGRAM_ID), eq(USER_ID), eq(reason));
    }

    @Test
    @DisplayName("unblockUser - should unblock user and log action")
    void unblockUser_ShouldUnblockUserAndLogAction() {
        // Given
        doNothing().when(accountClient).unblockAccount(USER_ID);

        // When
        userService.unblockUser(USER_ID, ADMIN_TELEGRAM_ID);

        // Then
        verify(accountClient).unblockAccount(USER_ID);
        verify(auditLogService).logAction(eq("UNBLOCK_USER"), eq(ADMIN_TELEGRAM_ID), eq(USER_ID), isNull());
    }

    @Test
    @DisplayName("getUsersPage - should return paginated accounts")
    void getUsersPage_ShouldReturnPaginatedAccounts() {
        // Given
        PageAccountDto pageAccountDto = PageAccountDto.builder()
                .totalElements(100L)
                .totalPages(10)
                .size(10)
                .number(0)
                .content(List.of(testAccount))
                .build();

        when(accountClient.getAccountsPage(0, 10, "regDate,desc")).thenReturn(pageAccountDto);

        // When
        PageAccountDto result = userService.getUsersPage(0, 10);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(100L);
        assertThat(result.getTotalPages()).isEqualTo(10);
        assertThat(result.getContent()).hasSize(1);
        verify(accountClient).getAccountsPage(0, 10, "regDate,desc");
    }
}
