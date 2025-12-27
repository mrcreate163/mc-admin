package com.socialnetwork.adminbot.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTO Unit Tests")
class DtoTest {

    @Test
    @DisplayName("AccountDto - should create with builder")
    void accountDto_ShouldCreateWithBuilder() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDateTime regDate = LocalDateTime.now();
        LocalDate birthDate = LocalDate.of(1990, 1, 15);

        // When
        AccountDto account = AccountDto.builder()
                .id(id)
                .email("test@example.com")
                .phone("+79001234567")
                .photo("https://example.com/photo.jpg")
                .about("Test user")
                .city("Moscow")
                .country("Russia")
                .firstName("John")
                .lastName("Doe")
                .regDate(regDate)
                .birthDate(birthDate)
                .isOnline(true)
                .isBlocked(false)
                .isDeleted(false)
                .build();

        // Then
        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getEmail()).isEqualTo("test@example.com");
        assertThat(account.getPhone()).isEqualTo("+79001234567");
        assertThat(account.getCity()).isEqualTo("Moscow");
        assertThat(account.getCountry()).isEqualTo("Russia");
        assertThat(account.getFirstName()).isEqualTo("John");
        assertThat(account.getLastName()).isEqualTo("Doe");
        assertThat(account.getRegDate()).isEqualTo(regDate);
        assertThat(account.getBirthDate()).isEqualTo(birthDate);
        assertThat(account.getIsOnline()).isTrue();
        assertThat(account.getIsBlocked()).isFalse();
        assertThat(account.getIsDeleted()).isFalse();
    }

    @Test
    @DisplayName("StatisticsDto - should create with builder")
    void statisticsDto_ShouldCreateWithBuilder() {
        // When
        StatisticsDto stats = StatisticsDto.builder()
                .totalUsers(1500L)
                .activeUsers(1200L)
                .blockedUsers(50L)
                .newUsersToday(25L)
                .totalAdmins(5L)
                .build();

        // Then
        assertThat(stats.getTotalUsers()).isEqualTo(1500L);
        assertThat(stats.getActiveUsers()).isEqualTo(1200L);
        assertThat(stats.getBlockedUsers()).isEqualTo(50L);
        assertThat(stats.getNewUsersToday()).isEqualTo(25L);
        assertThat(stats.getTotalAdmins()).isEqualTo(5L);
    }

    @Test
    @DisplayName("PageAccountDto - should create with builder")
    void pageAccountDto_ShouldCreateWithBuilder() {
        // When
        PageAccountDto page = PageAccountDto.builder()
                .totalElements(100L)
                .totalPages(10)
                .size(10)
                .number(0)
                .content(java.util.List.of())
                .build();

        // Then
        assertThat(page.getTotalElements()).isEqualTo(100L);
        assertThat(page.getTotalPages()).isEqualTo(10);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getNumber()).isEqualTo(0);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("AdminDto - should create with builder")
    void adminDto_ShouldCreateWithBuilder() {
        // Given
        UUID id = UUID.randomUUID();

        // When
        AdminDto admin = AdminDto.builder()
                .id(id)
                .telegramUserId(123456789L)
                .username("admin")
                .firstName("Admin")
                .role(com.socialnetwork.adminbot.entity.AdminRole.ADMIN)
                .isActive(true)
                .build();

        // Then
        assertThat(admin.getId()).isEqualTo(id);
        assertThat(admin.getTelegramUserId()).isEqualTo(123456789L);
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getFirstName()).isEqualTo("Admin");
        assertThat(admin.getRole()).isEqualTo(com.socialnetwork.adminbot.entity.AdminRole.ADMIN);
        assertThat(admin.getIsActive()).isTrue();
    }
}
