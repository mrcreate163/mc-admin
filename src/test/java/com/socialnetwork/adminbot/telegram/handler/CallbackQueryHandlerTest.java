package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.service.*;
import com.socialnetwork.adminbot.telegram.handler.callback.*;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для CallbackQueryHandler (маршрутизатор callback-запросов).
 * Тестирует корректную маршрутизацию к специализированным обработчикам.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CallbackQueryHandler Unit Tests")
class CallbackQueryHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ConversationStateService conversationStateService;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private BanCommandHandler banCommandHandler;

    @Mock
    private SearchCommandHandler searchCommandHandler;

    @Mock
    private AddAdminCommandHandler addAdminCommandHandler;

    private CallbackQueryHandler callbackQueryHandler;
    private UserBlockCallbackHandler userBlockCallbackHandler;
    private NavigationCallbackHandler navigationCallbackHandler;

    private CallbackQuery mockCallbackQuery;
    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;
    private static final Integer MESSAGE_ID = 100;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        // Создаём специализированные обработчики с моками
        userBlockCallbackHandler = new UserBlockCallbackHandler(
                userService,
                auditLogService,
                conversationStateService,
                stateTransitionService,
                banCommandHandler
        );

        navigationCallbackHandler = new NavigationCallbackHandler(
                statisticsService,
                auditLogService
        );

        SearchCallbackHandler searchCallbackHandler = new SearchCallbackHandler(
                userService,
                auditLogService,
                conversationStateService,
                searchCommandHandler
        );

        AdminManagementCallbackHandler adminManagementCallbackHandler = new AdminManagementCallbackHandler(
                addAdminCommandHandler
        );

        // Создаём маршрутизатор со списком обработчиков
        callbackQueryHandler = new CallbackQueryHandler(
                List.of(
                        userBlockCallbackHandler,
                        searchCallbackHandler,
                        adminManagementCallbackHandler,
                        navigationCallbackHandler
                )
        );

        mockCallbackQuery = mock(CallbackQuery.class);
        mockMessage = mock(Message.class);
        lenient().when(mockCallbackQuery.getMessage()).thenReturn(mockMessage);
        lenient().when(mockMessage.getChatId()).thenReturn(CHAT_ID);
        lenient().when(mockMessage.getMessageId()).thenReturn(MESSAGE_ID);
    }

    // ========== BAN REASON SELECTION TESTS ==========

    @Nested
    @DisplayName("Ban Reason Selection Tests")
    class BanReasonSelectionTests {

        @Test
        @DisplayName("handle - should save reason to Redis and transition on ban reason selection")
        void handle_WhenBanReasonSelected_ShouldSaveReasonAndTransition() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("ban_reason:spam");

            ConversationState awaitingReasonState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_USER_ID, USER_ID.toString());
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_EMAIL, USER_EMAIL);

            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(awaitingReasonState);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(USER_EMAIL);
            assertThat(result.getText()).contains("Спам"); // translated reason
            assertThat(result.getReplyMarkup()).isNotNull(); // confirmation keyboard

            // Verify reason is saved to Redis before transition
            verify(conversationStateService).updateStateData(ADMIN_TELEGRAM_ID, StateDataKey.BAN_REASON, "Спам");
            verify(stateTransitionService).transitionTo(ADMIN_TELEGRAM_ID, BotState.CONFIRMING_BAN);
        }

        @Test
        @DisplayName("handle - should return error when not in AWAITING_BAN_REASON state")
        void handle_WhenNotInAwaitingBanReasonState_ShouldReturnError() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("ban_reason:spam");

            ConversationState idleState = ConversationState.idle();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(idleState);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.ERROR_STATE_FOR_REASON.raw());
            verify(conversationStateService, never()).updateStateData(anyLong(), anyString(), any());
            verify(stateTransitionService, never()).transitionTo(anyLong(), any());
        }

        @Test
        @DisplayName("handle - should map harassment reason correctly")
        void handle_WhenHarassmentReasonSelected_ShouldMapCorrectly() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("ban_reason:harassment");

            ConversationState awaitingReasonState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_USER_ID, USER_ID.toString());
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_EMAIL, USER_EMAIL);

            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(awaitingReasonState);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            verify(conversationStateService).updateStateData(ADMIN_TELEGRAM_ID, StateDataKey.BAN_REASON, "Harassment");
        }

        @Test
        @DisplayName("handle - should map bot reason correctly")
        void handle_WhenBotReasonSelected_ShouldMapCorrectly() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("ban_reason:bot");

            ConversationState awaitingReasonState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_USER_ID, USER_ID.toString());
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_EMAIL, USER_EMAIL);

            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(awaitingReasonState);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            verify(conversationStateService).updateStateData(ADMIN_TELEGRAM_ID, StateDataKey.BAN_REASON, "Bot/Fake аккаунт");
        }
    }

    // ========== BAN CONFIRM/CANCEL TESTS ==========

    @Nested
    @DisplayName("Ban Confirm/Cancel Tests")
    class BanConfirmCancelTests {

        @Test
        @DisplayName("handle - should delegate to BanCommandHandler on ban_confirm")
        void handle_WhenBanConfirm_ShouldDelegateToBanCommandHandler() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("ban_confirm");

            SendMessage banResult = new SendMessage();
            banResult.setChatId(CHAT_ID.toString());
            banResult.setText("Ban executed successfully");
            when(banCommandHandler.executeBan(CHAT_ID, ADMIN_TELEGRAM_ID)).thenReturn(banResult);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).isEqualTo("Ban executed successfully");
            verify(banCommandHandler).executeBan(CHAT_ID, ADMIN_TELEGRAM_ID);
        }

        @Test
        @DisplayName("handle - should delegate to BanCommandHandler on ban_cancel")
        void handle_WhenBanCancel_ShouldDelegateToBanCommandHandler() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("ban_cancel");

            SendMessage cancelResult = new SendMessage();
            cancelResult.setChatId(CHAT_ID.toString());
            cancelResult.setText("Ban cancelled");
            when(banCommandHandler.cancelBan(CHAT_ID, ADMIN_TELEGRAM_ID)).thenReturn(cancelResult);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).isEqualTo("Ban cancelled");
            verify(banCommandHandler).cancelBan(CHAT_ID, ADMIN_TELEGRAM_ID);
        }
    }

    // ========== SHOW STATS TESTS ==========

    @Nested
    @DisplayName("Show Stats Tests")
    class ShowStatsTests {

        @Test
        @DisplayName("handle - should show statistics on show_stats callback")
        void handle_WhenShowStats_ShouldReturnStatistics() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("show_stats");

            StatisticsDto stats = StatisticsDto.builder()
                    .totalUsers(1500L)
                    .activeUsers(1200L)
                    .blockedUsers(50L)
                    .newUsersToday(25L)
                    .totalAdmins(5L)
                    .build();
            when(statisticsService.getStatistics()).thenReturn(stats);

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Статистика");
            assertThat(result.getText()).contains("1500");
            verify(auditLogService).logAction(eq("VIEW_STATS"), eq(ADMIN_TELEGRAM_ID), anyMap());
        }
    }

    // ========== MAIN MENU TESTS ==========

    @Nested
    @DisplayName("Main Menu Tests")
    class MainMenuTests {

        @Test
        @DisplayName("handle - should show main menu on main_menu callback")
        void handle_WhenMainMenu_ShouldShowMainMenu() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("main_menu");

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.MAIN_MENU_TITLE.raw());
            assertThat(result.getReplyMarkup()).isNotNull();
        }
    }

    // ========== CREATE ANSWER TESTS ==========

    @Nested
    @DisplayName("Create Answer Tests")
    class CreateAnswerTests {

        @Test
        @DisplayName("createAnswer - should create AnswerCallbackQuery with text")
        void createAnswer_ShouldCreateAnswerWithText() {
            // Given
            String callbackQueryId = "callback-123";
            String text = "Success!";

            // When
            AnswerCallbackQuery result = callbackQueryHandler.createAnswer(callbackQueryId, text);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCallbackQueryId()).isEqualTo(callbackQueryId);
            assertThat(result.getText()).isEqualTo(text);
        }
    }

    // ========== UNKNOWN ACTION TESTS ==========

    @Nested
    @DisplayName("Unknown Action Tests")
    class UnknownActionTests {

        @Test
        @DisplayName("handle - should return error for unknown callback data")
        void handle_WhenUnknownAction_ShouldReturnError() {
            // Given
            when(mockCallbackQuery.getData()).thenReturn("unknown_action");

            // When
            EditMessageText result = callbackQueryHandler.handle(mockCallbackQuery, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.ERROR_UNKNOWN_ACTION.raw());
        }
    }
}
