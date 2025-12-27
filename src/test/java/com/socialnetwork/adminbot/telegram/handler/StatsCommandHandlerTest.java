package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsCommandHandler Unit Tests")
class StatsCommandHandlerTest {

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private StatsCommandHandler statsCommandHandler;

    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);
        when(mockMessage.getChatId()).thenReturn(CHAT_ID);
    }

    @Test
    @DisplayName("handle - should return statistics successfully")
    void handle_ShouldReturnStatisticsSuccessfully() {
        // Given
        StatisticsDto stats = StatisticsDto.builder()
                .totalUsers(1500L)
                .activeUsers(1200L)
                .blockedUsers(50L)
                .newUsersToday(25L)
                .totalAdmins(5L)
                .build();
        when(statisticsService.getStatistics()).thenReturn(stats);

        // When
        SendMessage result = statsCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getChatId()).isEqualTo(CHAT_ID.toString());
        assertThat(result.getText()).contains("Статистика платформы");
        assertThat(result.getText()).contains("1500");
        assertThat(result.getText()).contains("1200");
        assertThat(result.getText()).contains("50");
        assertThat(result.getText()).contains("25");
        assertThat(result.getText()).contains("5");
        assertThat(result.getParseMode()).isEqualTo("HTML");
        verify(statisticsService).getStatistics();
        verify(auditLogService).logAction(eq("VIEW_STATS"), eq(ADMIN_TELEGRAM_ID), anyMap());
    }

    @Test
    @DisplayName("handle - should return error message when service fails")
    void handle_WhenServiceFails_ShouldReturnErrorMessage() {
        // Given
        when(statisticsService.getStatistics()).thenThrow(new RuntimeException("Service unavailable"));

        // When
        SendMessage result = statsCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Ошибка получения статистики");
        assertThat(result.getText()).contains("Service unavailable");
    }

    @Test
    @DisplayName("handle - should log statistics view action")
    void handle_ShouldLogStatisticsViewAction() {
        // Given
        StatisticsDto stats = StatisticsDto.builder()
                .totalUsers(0L)
                .activeUsers(0L)
                .blockedUsers(0L)
                .newUsersToday(0L)
                .totalAdmins(0L)
                .build();
        when(statisticsService.getStatistics()).thenReturn(stats);

        // When
        statsCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

        // Then
        verify(auditLogService).logAction(eq("VIEW_STATS"), eq(ADMIN_TELEGRAM_ID), anyMap());
    }
}
