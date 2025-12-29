package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StateTransitionService Unit Tests")
class StateTransitionServiceTest {

    @Mock
    private ConversationStateService conversationStateService;

    private StateTransitionService stateTransitionService;

    private static final Long TELEGRAM_USER_ID = 123456789L;

    @BeforeEach
    void setUp() {
        stateTransitionService = new StateTransitionService(conversationStateService);
    }

    // ========== IS TRANSITION ALLOWED TESTS ==========

    @Nested
    @DisplayName("isTransitionAllowed Tests")
    class IsTransitionAllowedTests {

        @Test
        @DisplayName("IDLE -> AWAITING_BAN_REASON should be allowed")
        void idleToAwaitingBanReason_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.IDLE, BotState.AWAITING_BAN_REASON)).isTrue();
        }

        @Test
        @DisplayName("IDLE -> AWAITING_SEARCH_QUERY should be allowed")
        void idleToAwaitingSearchQuery_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.IDLE, BotState.AWAITING_SEARCH_QUERY)).isTrue();
        }

        @Test
        @DisplayName("IDLE -> AWAITING_ADMIN_TELEGRAM_ID should be allowed")
        void idleToAwaitingAdminTelegramId_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.IDLE, BotState.AWAITING_ADMIN_TELEGRAM_ID)).isTrue();
        }

        @Test
        @DisplayName("IDLE -> CONFIRMING_BAN should not be allowed")
        void idleToConfirmingBan_ShouldNotBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.IDLE, BotState.CONFIRMING_BAN)).isFalse();
        }

        @Test
        @DisplayName("AWAITING_BAN_REASON -> CONFIRMING_BAN should be allowed")
        void awaitingBanReasonToConfirmingBan_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.AWAITING_BAN_REASON, BotState.CONFIRMING_BAN)).isTrue();
        }

        @Test
        @DisplayName("AWAITING_BAN_REASON -> IDLE should be allowed")
        void awaitingBanReasonToIdle_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.AWAITING_BAN_REASON, BotState.IDLE)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMING_BAN -> IDLE should be allowed")
        void confirmingBanToIdle_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.CONFIRMING_BAN, BotState.IDLE)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMING_BAN -> AWAITING_BAN_REASON should not be allowed")
        void confirmingBanToAwaitingBanReason_ShouldNotBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.CONFIRMING_BAN, BotState.AWAITING_BAN_REASON)).isFalse();
        }

        @Test
        @DisplayName("AWAITING_SEARCH_QUERY -> SHOWING_SEARCH_RESULTS should be allowed")
        void awaitingSearchQueryToShowingSearchResults_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.AWAITING_SEARCH_QUERY, BotState.SHOWING_SEARCH_RESULTS)).isTrue();
        }

        @Test
        @DisplayName("SHOWING_SEARCH_RESULTS -> SHOWING_SEARCH_RESULTS should be allowed (pagination)")
        void showingSearchResultsToShowingSearchResults_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.SHOWING_SEARCH_RESULTS, BotState.SHOWING_SEARCH_RESULTS)).isTrue();
        }

        @Test
        @DisplayName("AWAITING_ADMIN_TELEGRAM_ID -> AWAITING_ADMIN_ROLE should be allowed")
        void awaitingAdminTelegramIdToAwaitingAdminRole_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.AWAITING_ADMIN_TELEGRAM_ID, BotState.AWAITING_ADMIN_ROLE)).isTrue();
        }

        @Test
        @DisplayName("AWAITING_ADMIN_ROLE -> CONFIRMING_ADMIN_CREATION should be allowed")
        void awaitingAdminRoleToConfirmingAdminCreation_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.AWAITING_ADMIN_ROLE, BotState.CONFIRMING_ADMIN_CREATION)).isTrue();
        }

        @Test
        @DisplayName("CONFIRMING_ADMIN_CREATION -> IDLE should be allowed")
        void confirmingAdminCreationToIdle_ShouldBeAllowed() {
            assertThat(stateTransitionService.isTransitionAllowed(
                    BotState.CONFIRMING_ADMIN_CREATION, BotState.IDLE)).isTrue();
        }
    }

    // ========== TRANSITION TO TESTS ==========

    @Nested
    @DisplayName("transitionTo Tests")
    class TransitionToTests {

        @Test
        @DisplayName("transitionTo - should transition when allowed")
        void transitionTo_WhenAllowed_ShouldTransition() {
            // Given
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.IDLE);

            // When
            stateTransitionService.transitionTo(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);

            // Then
            verify(conversationStateService).transitionTo(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);
        }

        @Test
        @DisplayName("transitionTo - should throw exception when not allowed")
        void transitionTo_WhenNotAllowed_ShouldThrowException() {
            // Given
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.IDLE);

            // When & Then
            assertThatThrownBy(() ->
                    stateTransitionService.transitionTo(TELEGRAM_USER_ID, BotState.CONFIRMING_BAN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Transition not allowed");
        }

        @Test
        @DisplayName("transitionTo - should allow ban flow transitions")
        void transitionTo_BanFlow_ShouldAllowCorrectTransitions() {
            // IDLE -> AWAITING_BAN_REASON
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.IDLE);
            stateTransitionService.transitionTo(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);
            verify(conversationStateService).transitionTo(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);

            // AWAITING_BAN_REASON -> CONFIRMING_BAN
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.AWAITING_BAN_REASON);
            stateTransitionService.transitionTo(TELEGRAM_USER_ID, BotState.CONFIRMING_BAN);
            verify(conversationStateService).transitionTo(TELEGRAM_USER_ID, BotState.CONFIRMING_BAN);

            // CONFIRMING_BAN -> IDLE
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.CONFIRMING_BAN);
            stateTransitionService.transitionTo(TELEGRAM_USER_ID, BotState.IDLE);
            verify(conversationStateService).transitionTo(TELEGRAM_USER_ID, BotState.IDLE);
        }
    }

    // ========== TRANSITION TO WITH CLEAR TESTS ==========

    @Nested
    @DisplayName("transitionToWithClear Tests")
    class TransitionToWithClearTests {

        @Test
        @DisplayName("transitionToWithClear - should transition and clear when allowed")
        void transitionToWithClear_WhenAllowed_ShouldTransitionAndClear() {
            // Given
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.AWAITING_BAN_REASON);

            // When
            stateTransitionService.transitionToWithClear(TELEGRAM_USER_ID, BotState.IDLE);

            // Then
            verify(conversationStateService).transitionToWithClear(TELEGRAM_USER_ID, BotState.IDLE);
        }

        @Test
        @DisplayName("transitionToWithClear - should throw exception when not allowed")
        void transitionToWithClear_WhenNotAllowed_ShouldThrowException() {
            // Given
            when(conversationStateService.getCurrentState(TELEGRAM_USER_ID))
                    .thenReturn(BotState.IDLE);

            // When & Then
            assertThatThrownBy(() ->
                    stateTransitionService.transitionToWithClear(TELEGRAM_USER_ID, BotState.CONFIRMING_BAN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Transition not allowed");
        }
    }

    // ========== FORCE RESET TO IDLE TESTS ==========

    @Nested
    @DisplayName("forceResetToIdle Tests")
    class ForceResetToIdleTests {

        @Test
        @DisplayName("forceResetToIdle - should always reset to IDLE regardless of current state")
        void forceResetToIdle_ShouldAlwaysResetToIdle() {
            // When
            stateTransitionService.forceResetToIdle(TELEGRAM_USER_ID);

            // Then
            verify(conversationStateService).resetToIdle(TELEGRAM_USER_ID);
        }

        @Test
        @DisplayName("forceResetToIdle - should reset from any state")
        void forceResetToIdle_FromAnyState_ShouldReset() {
            // Force reset should not check transition rules
            stateTransitionService.forceResetToIdle(TELEGRAM_USER_ID);

            verify(conversationStateService).resetToIdle(TELEGRAM_USER_ID);
            // No exception should be thrown
        }
    }
}
