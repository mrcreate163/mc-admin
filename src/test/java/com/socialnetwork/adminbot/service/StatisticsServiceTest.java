package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.dto.PageAccountDto;
import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsService Unit Tests")
class StatisticsServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    private PageAccountDto pageAccountDto;

    @BeforeEach
    void setUp() {
        pageAccountDto = PageAccountDto.builder()
                .totalElements(1500L)
                .totalPages(150)
                .size(10)
                .number(0)
                .content(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("getStatistics - should return statistics with total users")
    void getStatistics_ShouldReturnStatisticsWithTotalUsers() {
        // Given
        when(userService.getUsersPage(0, 1)).thenReturn(pageAccountDto);
        when(auditLogRepository.countByActionTypeAndCreatedAtAfter(eq("USER_REGISTERED"), any(LocalDateTime.class)))
                .thenReturn(50L);

        // When
        StatisticsDto result = statisticsService.getStatistics();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalUsers()).isEqualTo(1500L);
        assertThat(result.getNewUsersToday()).isEqualTo(50L);
        // MVP version - these are placeholder values
        assertThat(result.getActiveUsers()).isEqualTo(0L);
        assertThat(result.getBlockedUsers()).isEqualTo(0L);
        assertThat(result.getTotalAdmins()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getStatistics - should return zeros when service fails")
    void getStatistics_WhenServiceFails_ShouldReturnZeros() {
        // Given
        when(userService.getUsersPage(0, 1)).thenThrow(new RuntimeException("Service unavailable"));

        // When
        StatisticsDto result = statisticsService.getStatistics();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalUsers()).isEqualTo(0L);
        assertThat(result.getNewUsersToday()).isEqualTo(0L);
        assertThat(result.getActiveUsers()).isEqualTo(0L);
        assertThat(result.getBlockedUsers()).isEqualTo(0L);
        assertThat(result.getTotalAdmins()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getStatistics - should handle null totalElements")
    void getStatistics_WhenTotalElementsNull_ShouldReturnZeroTotalUsers() {
        // Given
        pageAccountDto.setTotalElements(null);
        when(userService.getUsersPage(0, 1)).thenReturn(pageAccountDto);
        when(auditLogRepository.countByActionTypeAndCreatedAtAfter(eq("USER_REGISTERED"), any(LocalDateTime.class)))
                .thenReturn(0L);

        // When
        StatisticsDto result = statisticsService.getStatistics();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalUsers()).isEqualTo(0L);
    }
}
