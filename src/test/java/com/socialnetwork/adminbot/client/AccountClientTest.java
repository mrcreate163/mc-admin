package com.socialnetwork.adminbot.client;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
import com.socialnetwork.adminbot.exception.ServiceException;
import com.socialnetwork.adminbot.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountClient Unit Tests")
class AccountClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AccountClient accountClient;

    private AccountDto testAccount;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ACCOUNT_SERVICE_URL = "http://localhost:34135/api/v1/internal/account";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountClient, "accountServiceUrl", ACCOUNT_SERVICE_URL);
        
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
    @DisplayName("getAccountById - should return account when found")
    void getAccountById_WhenAccountExists_ShouldReturnAccount() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/" + USER_ID;
        when(restTemplate.getForEntity(eq(url), eq(AccountDto.class)))
                .thenReturn(new ResponseEntity<>(testAccount, HttpStatus.OK));

        // When
        AccountDto result = accountClient.getAccountById(USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(USER_ID);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("getAccountById - should throw UserNotFoundException when not found")
    void getAccountById_WhenAccountNotFound_ShouldThrowUserNotFoundException() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/" + USER_ID;
        when(restTemplate.getForEntity(eq(url), eq(AccountDto.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        // When & Then
        assertThatThrownBy(() -> accountClient.getAccountById(USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("getAccountById - should throw ServiceException on server error")
    void getAccountById_WhenServerError_ShouldThrowServiceException() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/" + USER_ID;
        when(restTemplate.getForEntity(eq(url), eq(AccountDto.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null));

        // When & Then
        assertThatThrownBy(() -> accountClient.getAccountById(USER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Account service error");
    }

    @Test
    @DisplayName("getAccountById - should throw ServiceException when service unavailable")
    void getAccountById_WhenServiceUnavailable_ShouldThrowServiceException() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/" + USER_ID;
        when(restTemplate.getForEntity(eq(url), eq(AccountDto.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // When & Then
        assertThatThrownBy(() -> accountClient.getAccountById(USER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Account service unavailable");
    }

    @Test
    @DisplayName("blockAccount - should call block endpoint successfully")
    void blockAccount_ShouldCallBlockEndpoint() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/block/" + USER_ID;
        doNothing().when(restTemplate).put(eq(url), isNull());

        // When
        accountClient.blockAccount(USER_ID);

        // Then
        verify(restTemplate).put(eq(url), isNull());
    }

    @Test
    @DisplayName("blockAccount - should throw UserNotFoundException when user not found")
    void blockAccount_WhenUserNotFound_ShouldThrowUserNotFoundException() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/block/" + USER_ID;
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                .when(restTemplate).put(eq(url), isNull());

        // When & Then
        assertThatThrownBy(() -> accountClient.blockAccount(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("unblockAccount - should call unblock endpoint successfully")
    void unblockAccount_ShouldCallUnblockEndpoint() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/block/" + USER_ID;
        doNothing().when(restTemplate).delete(eq(url));

        // When
        accountClient.unblockAccount(USER_ID);

        // Then
        verify(restTemplate).delete(eq(url));
    }

    @Test
    @DisplayName("unblockAccount - should throw UserNotFoundException when user not found")
    void unblockAccount_WhenUserNotFound_ShouldThrowUserNotFoundException() {
        // Given
        String url = ACCOUNT_SERVICE_URL + "/block/" + USER_ID;
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                .when(restTemplate).delete(eq(url));

        // When & Then
        assertThatThrownBy(() -> accountClient.unblockAccount(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getAccountsPage - should return paginated accounts")
    void getAccountsPage_ShouldReturnPaginatedAccounts() {
        // Given
        PageAccountDto pageAccountDto = PageAccountDto.builder()
                .totalElements(100L)
                .totalPages(10)
                .size(10)
                .number(0)
                .content(List.of(testAccount))
                .build();

        when(restTemplate.getForEntity(anyString(), eq(PageAccountDto.class)))
                .thenReturn(new ResponseEntity<>(pageAccountDto, HttpStatus.OK));

        // When
        PageAccountDto result = accountClient.getAccountsPage(0, 10, "regDate,desc");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(100L);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getAccountsPage - should throw ServiceException on server error")
    void getAccountsPage_WhenServerError_ShouldThrowServiceException() {
        // Given
        when(restTemplate.getForEntity(anyString(), eq(PageAccountDto.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null));

        // When & Then
        assertThatThrownBy(() -> accountClient.getAccountsPage(0, 10, "regDate,desc"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Account service error");
    }
}
