package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.service.StateTransitionService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("BanCommandHandler Unit Tests")
class BanCommandHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ConversationStateService conversationStateService;

    @Mock
    private StateTransitionService stateTransitionService;

    private BanCommandHandler banCommandHandler;

    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        banCommandHandler = new BanCommandHandler(
                conversationStateService,
                stateTransitionService,
                userService,
                auditLogService
        );

        mockMessage = mock(Message.class);
        lenient().when(mockMessage.getChatId()).thenReturn(CHAT_ID);
    }

    // ========== START CONVERSATION TESTS (handle command) ==========

    @Nested
    @DisplayName("Start Conversation Tests")
    class StartConversationTests {

        @BeforeEach
        void setUpIdleState() {
            // User is in IDLE state - not in active conversation
            ConversationState idleState = ConversationState.idle();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(idleState);
        }

        @Test
        @DisplayName("handle - should return usage message when no user id provided")
        void handle_WhenNoUserIdProvided_ShouldReturnUsageMessage() {
            // Given
            when(mockMessage.getText()).thenReturn("/ban");

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("/ban");
            verify(userService, never()).blockUser(any(), anyLong(), anyString());
            verify(conversationStateService, never()).setState(anyLong(), any());
        }

        @Test
        @DisplayName("handle - should start ban conversation with valid user id")
        void handle_WhenValidUserId_ShouldStartConversation() {
            // Given
            when(mockMessage.getText()).thenReturn("/ban " + USER_ID);
            AccountDto account = AccountDto.builder()
                    .id(USER_ID)
                    .email(USER_EMAIL)
                    .build();
            when(userService.getUserById(USER_ID)).thenReturn(account);

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(USER_EMAIL);
            assertThat(result.getText()).contains(USER_ID.toString());
            assertThat(result.getReplyMarkup()).isNotNull(); // keyboard with ban reasons

            // Verify state was saved
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(conversationStateService).setState(eq(ADMIN_TELEGRAM_ID), stateCaptor.capture());
            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getState()).isEqualTo(BotState.AWAITING_BAN_REASON);
            assertThat(savedState.getData(StateDataKey.BAN_TARGET_USER_ID, String.class)).isEqualTo(USER_ID.toString());
        }

        @Test
        @DisplayName("handle - should return error when invalid UUID format")
        void handle_WhenInvalidUuidFormat_ShouldReturnError() {
            // Given
            when(mockMessage.getText()).thenReturn("/ban invalid-uuid");

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.ERROR_INVALID_USER_ID.raw());
            verify(userService, never()).getUserById(any());
            verify(conversationStateService, never()).setState(anyLong(), any());
        }

        @Test
        @DisplayName("handle - should return error and reset state on service exception")
        void handle_WhenServiceException_ShouldReturnErrorAndResetState() {
            // Given
            when(mockMessage.getText()).thenReturn("/ban " + USER_ID);
            when(userService.getUserById(USER_ID)).thenThrow(new RuntimeException("Service error"));

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Ошибка");
            assertThat(result.getText()).contains("Service error");
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }
    }

    // ========== CONVERSATION STEP TESTS (reason input) ==========

    @Nested
    @DisplayName("Ban Reason Input Tests")
    class BanReasonInputTests {

        private ConversationState awaitingReasonState;

        @BeforeEach
        void setUpAwaitingReasonState() {
            awaitingReasonState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_USER_ID, USER_ID.toString());
            awaitingReasonState.addData(StateDataKey.BAN_TARGET_EMAIL, USER_EMAIL);

            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(awaitingReasonState);
        }

        @Test
        @DisplayName("handleConversationStep - should accept valid reason and transition to confirming")
        void handleConversationStep_WhenValidReason_ShouldTransitionToConfirming() {
            // Given
            String reason = "Spam content violation";
            when(mockMessage.getText()).thenReturn(reason);

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(USER_EMAIL);
            assertThat(result.getText()).contains(reason);
            assertThat(result.getReplyMarkup()).isNotNull(); // confirmation keyboard

            // Verify reason is saved to Redis before transition
            verify(conversationStateService).updateStateData(ADMIN_TELEGRAM_ID, StateDataKey.BAN_REASON, reason);
            verify(stateTransitionService).transitionTo(ADMIN_TELEGRAM_ID, BotState.CONFIRMING_BAN);
        }

        @Test
        @DisplayName("handleConversationStep - should reject empty reason")
        void handleConversationStep_WhenEmptyReason_ShouldRejectAndAskAgain() {
            // Given
            when(mockMessage.getText()).thenReturn("   ");

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.LIMIT_FOR_REASON.raw());
            verify(stateTransitionService, never()).transitionTo(anyLong(), any());
        }

        @Test
        @DisplayName("handleConversationStep - should reject reason exceeding 500 characters")
        void handleConversationStep_WhenReasonTooLong_ShouldReject() {
            // Given
            String longReason = "a".repeat(501);
            when(mockMessage.getText()).thenReturn(longReason);

            // When
            SendMessage result = banCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.LIMIT_FOR_REASON.raw());
            verify(stateTransitionService, never()).transitionTo(anyLong(), any());
        }
    }

    // ========== EXECUTE BAN TESTS ==========

    @Nested
    @DisplayName("Execute Ban Tests")
    class ExecuteBanTests {

        @Test
        @DisplayName("executeBan - should execute ban successfully from CONFIRMING_BAN state")
        void executeBan_WhenInConfirmingState_ShouldExecuteBan() {
            // Given
            String reason = "Spam";
            ConversationState confirmingState = ConversationState.builder()
                    .state(BotState.CONFIRMING_BAN)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            confirmingState.addData(StateDataKey.BAN_TARGET_USER_ID, USER_ID.toString());
            confirmingState.addData(StateDataKey.BAN_TARGET_EMAIL, USER_EMAIL);
            confirmingState.addData(StateDataKey.BAN_REASON, reason);

            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(confirmingState);
            doNothing().when(userService).blockUser(eq(USER_ID), eq(ADMIN_TELEGRAM_ID), eq(reason));

            // When
            SendMessage result = banCommandHandler.executeBan(CHAT_ID, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.BAN_SUCCESS_2.raw());
            assertThat(result.getText()).contains(USER_EMAIL);

            verify(userService).blockUser(USER_ID, ADMIN_TELEGRAM_ID, reason);
            verify(auditLogService).logAction(eq("BLOCK_USER"), eq(ADMIN_TELEGRAM_ID), eq(USER_ID), anyString());
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }

        @Test
        @DisplayName("executeBan - should return error when not in CONFIRMING_BAN state")
        void executeBan_WhenNotInConfirmingState_ShouldReturnError() {
            // Given
            ConversationState idleState = ConversationState.idle();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(idleState);

            // When
            SendMessage result = banCommandHandler.executeBan(CHAT_ID, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.ERROR_STATE_FOR_BAN.raw());
            verify(userService, never()).blockUser(any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("executeBan - should reset state on service error")
        void executeBan_WhenServiceError_ShouldResetState() {
            // Given
            ConversationState confirmingState = ConversationState.builder()
                    .state(BotState.CONFIRMING_BAN)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            confirmingState.addData(StateDataKey.BAN_TARGET_USER_ID, USER_ID.toString());
            confirmingState.addData(StateDataKey.BAN_TARGET_EMAIL, USER_EMAIL);
            confirmingState.addData(StateDataKey.BAN_REASON, "Spam");

            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(confirmingState);
            doThrow(new RuntimeException("Service error")).when(userService).blockUser(any(), anyLong(), anyString());

            // When
            SendMessage result = banCommandHandler.executeBan(CHAT_ID, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Ошибка");
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }
    }

    // ========== CANCEL BAN TESTS ==========

    @Nested
    @DisplayName("Cancel Ban Tests")
    class CancelBanTests {

        @Test
        @DisplayName("cancelBan - should cancel and reset state")
        void cancelBan_ShouldResetStateAndConfirm() {
            // When
            SendMessage result = banCommandHandler.cancelBan(CHAT_ID, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.BAN_CANCELED.raw());
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }
    }

    // ========== UNBAN TESTS ==========

    @Nested
    @DisplayName("Unban Tests")
    class UnbanTests {

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
            verify(userService).unblockUser(USER_ID, ADMIN_TELEGRAM_ID);
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
            assertThat(result.getText()).contains(BotMessage.ERROR_INVALID_USER_ID.raw());
            verify(userService, never()).unblockUser(any(), anyLong());
        }

        @Test
        @DisplayName("handleUnban - should return error on service exception")
        void handleUnban_WhenServiceException_ShouldReturnError() {
            // Given
            when(mockMessage.getText()).thenReturn("/unban " + USER_ID);
            doThrow(new RuntimeException("Service error")).when(userService).unblockUser(any(), anyLong());

            // When
            SendMessage result = banCommandHandler.handleUnban(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Ошибка");
            assertThat(result.getText()).contains("Service error");
        }
    }

    // ========== GET COMMAND NAME ==========

    @Test
    @DisplayName("getCommandName - should return 'ban'")
    void getCommandName_ShouldReturnBan() {
        assertThat(banCommandHandler.getCommandName()).isEqualTo("ban");
    }
}
