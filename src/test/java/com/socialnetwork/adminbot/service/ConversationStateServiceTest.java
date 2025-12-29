package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationStateService Unit Tests")
class ConversationStateServiceTest {

    @Mock
    private RedisTemplate<String, ConversationState> conversationStateRedisTemplate;

    @Mock
    private ValueOperations<String, ConversationState> valueOperations;

    private ConversationStateService conversationStateService;

    private static final Long TELEGRAM_USER_ID = 123456789L;
    private static final String STATE_KEY_PREFIX = "telegram:state:";
    private static final String EXPECTED_KEY = STATE_KEY_PREFIX + TELEGRAM_USER_ID;

    @BeforeEach
    void setUp() {
        conversationStateService = new ConversationStateService(conversationStateRedisTemplate);
        lenient().when(conversationStateRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ========== GET STATE TESTS ==========

    @Nested
    @DisplayName("getState Tests")
    class GetStateTests {

        @Test
        @DisplayName("getState - should return stored state when exists")
        void getState_WhenStateExists_ShouldReturnStoredState() {
            // Given
            ConversationState storedState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(storedState);

            // When
            ConversationState result = conversationStateService.getState(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(BotState.AWAITING_BAN_REASON);
            verify(valueOperations).get(EXPECTED_KEY);
        }

        @Test
        @DisplayName("getState - should return IDLE state when not exists")
        void getState_WhenStateNotExists_ShouldReturnIdleState() {
            // Given
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

            // When
            ConversationState result = conversationStateService.getState(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getState()).isEqualTo(BotState.IDLE);
        }
    }

    // ========== GET STATE OPTIONAL TESTS ==========

    @Nested
    @DisplayName("getStateOptional Tests")
    class GetStateOptionalTests {

        @Test
        @DisplayName("getStateOptional - should return Optional with state when exists")
        void getStateOptional_WhenStateExists_ShouldReturnOptionalWithState() {
            // Given
            ConversationState storedState = ConversationState.builder()
                    .state(BotState.CONFIRMING_BAN)
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(storedState);

            // When
            Optional<ConversationState> result = conversationStateService.getStateOptional(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getState()).isEqualTo(BotState.CONFIRMING_BAN);
        }

        @Test
        @DisplayName("getStateOptional - should return empty Optional when not exists")
        void getStateOptional_WhenStateNotExists_ShouldReturnEmptyOptional() {
            // Given
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

            // When
            Optional<ConversationState> result = conversationStateService.getStateOptional(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========== SET STATE TESTS ==========

    @Nested
    @DisplayName("setState Tests")
    class SetStateTests {

        @Test
        @DisplayName("setState - should save state with default TTL")
        void setState_ShouldSaveStateWithDefaultTtl() {
            // Given
            ConversationState state = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();

            // When
            conversationStateService.setState(TELEGRAM_USER_ID, state);

            // Then
            verify(valueOperations).set(eq(EXPECTED_KEY), eq(state), eq(Duration.ofMinutes(30)));
            assertThat(state.getUpdatedAt()).isNotNull();
            assertThat(state.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("setState - should save state with custom TTL")
        void setState_ShouldSaveStateWithCustomTtl() {
            // Given
            ConversationState state = ConversationState.builder()
                    .state(BotState.AWAITING_SEARCH_QUERY)
                    .createdAt(LocalDateTime.now().minusMinutes(5))
                    .build();
            Duration customTtl = Duration.ofHours(1);

            // When
            conversationStateService.setState(TELEGRAM_USER_ID, state, customTtl);

            // Then
            verify(valueOperations).set(eq(EXPECTED_KEY), eq(state), eq(customTtl));
            assertThat(state.getCreatedAt()).isNotNull(); // Should preserve existing createdAt
        }
    }

    // ========== TRANSITION TESTS ==========

    @Nested
    @DisplayName("transitionTo Tests")
    class TransitionToTests {

        @Test
        @DisplayName("transitionTo - should update state and increment version")
        void transitionTo_ShouldUpdateStateAndIncrementVersion() {
            // Given
            ConversationState currentState = ConversationState.builder()
                    .state(BotState.IDLE)
                    .version(0L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(currentState);

            // When
            conversationStateService.transitionTo(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);

            // Then
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(valueOperations).set(eq(EXPECTED_KEY), stateCaptor.capture(), any(Duration.class));

            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getState()).isEqualTo(BotState.AWAITING_BAN_REASON);
            assertThat(savedState.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("transitionToWithClear - should create new state with cleared data")
        void transitionToWithClear_ShouldCreateNewStateWithClearedData() {
            // When
            conversationStateService.transitionToWithClear(TELEGRAM_USER_ID, BotState.AWAITING_SEARCH_QUERY);

            // Then
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(valueOperations).set(eq(EXPECTED_KEY), stateCaptor.capture(), any(Duration.class));

            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getState()).isEqualTo(BotState.AWAITING_SEARCH_QUERY);
            assertThat(savedState.getVersion()).isEqualTo(0L);
            assertThat(savedState.getData()).isEmpty();
        }
    }

    // ========== UPDATE STATE DATA TESTS ==========

    @Nested
    @DisplayName("updateStateData Tests")
    class UpdateStateDataTests {

        @Test
        @DisplayName("updateStateData - should add data to existing state")
        void updateStateData_ShouldAddDataToExistingState() {
            // Given
            ConversationState currentState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .version(0L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(currentState);

            // When
            conversationStateService.updateStateData(TELEGRAM_USER_ID, "targetUserId", "test-uuid");

            // Then
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(valueOperations).set(eq(EXPECTED_KEY), stateCaptor.capture(), any(Duration.class));

            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getData()).containsEntry("targetUserId", "test-uuid");
            assertThat(savedState.getVersion()).isEqualTo(1L);
        }
    }

    // ========== RESET TO IDLE TESTS ==========

    @Nested
    @DisplayName("resetToIdle Tests")
    class ResetToIdleTests {

        @Test
        @DisplayName("resetToIdle - should set state to IDLE with cleared data")
        void resetToIdle_ShouldSetStateToIdleWithClearedData() {
            // When
            conversationStateService.resetToIdle(TELEGRAM_USER_ID);

            // Then
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(valueOperations).set(eq(EXPECTED_KEY), stateCaptor.capture(), any(Duration.class));

            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getState()).isEqualTo(BotState.IDLE);
            assertThat(savedState.getData()).isEmpty();
        }
    }

    // ========== CLEAR STATE TESTS ==========

    @Nested
    @DisplayName("clearState Tests")
    class ClearStateTests {

        @Test
        @DisplayName("clearState - should delete state from Redis")
        void clearState_ShouldDeleteStateFromRedis() {
            // When
            conversationStateService.clearState(TELEGRAM_USER_ID);

            // Then
            verify(conversationStateRedisTemplate).delete(EXPECTED_KEY);
        }
    }

    // ========== IS IN STATE TESTS ==========

    @Nested
    @DisplayName("isInState Tests")
    class IsInStateTests {

        @Test
        @DisplayName("isInState - should return true when in expected state")
        void isInState_WhenInExpectedState_ShouldReturnTrue() {
            // Given
            ConversationState state = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(state);

            // When
            boolean result = conversationStateService.isInState(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isInState - should return false when in different state")
        void isInState_WhenInDifferentState_ShouldReturnFalse() {
            // Given
            ConversationState state = ConversationState.builder()
                    .state(BotState.CONFIRMING_BAN)
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(state);

            // When
            boolean result = conversationStateService.isInState(TELEGRAM_USER_ID, BotState.AWAITING_BAN_REASON);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ========== IS IDLE TESTS ==========

    @Nested
    @DisplayName("isIdle Tests")
    class IsIdleTests {

        @Test
        @DisplayName("isIdle - should return true when state is IDLE")
        void isIdle_WhenStateIsIdle_ShouldReturnTrue() {
            // Given
            ConversationState state = ConversationState.idle();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(state);

            // When
            boolean result = conversationStateService.isIdle(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isIdle - should return false when state is not IDLE")
        void isIdle_WhenStateIsNotIdle_ShouldReturnFalse() {
            // Given
            ConversationState state = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(state);

            // When
            boolean result = conversationStateService.isIdle(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ========== GET CURRENT STATE TESTS ==========

    @Nested
    @DisplayName("getCurrentState Tests")
    class GetCurrentStateTests {

        @Test
        @DisplayName("getCurrentState - should return current BotState enum")
        void getCurrentState_ShouldReturnCurrentBotStateEnum() {
            // Given
            ConversationState state = ConversationState.builder()
                    .state(BotState.CONFIRMING_BAN)
                    .build();
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(state);

            // When
            BotState result = conversationStateService.getCurrentState(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isEqualTo(BotState.CONFIRMING_BAN);
        }

        @Test
        @DisplayName("getCurrentState - should return IDLE when no state exists")
        void getCurrentState_WhenNoState_ShouldReturnIdle() {
            // Given
            when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

            // When
            BotState result = conversationStateService.getCurrentState(TELEGRAM_USER_ID);

            // Then
            assertThat(result).isEqualTo(BotState.IDLE);
        }
    }
}
