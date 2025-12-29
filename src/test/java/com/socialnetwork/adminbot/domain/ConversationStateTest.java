package com.socialnetwork.adminbot.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConversationState Domain Model Tests")
class ConversationStateTest {

    // ========== BUILDER TESTS ==========

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder - should create state with default values")
        void builder_ShouldCreateStateWithDefaultValues() {
            // When
            ConversationState state = ConversationState.builder()
                    .state(BotState.IDLE)
                    .build();

            // Then
            assertThat(state.getState()).isEqualTo(BotState.IDLE);
            assertThat(state.getData()).isNotNull().isEmpty();
            assertThat(state.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("builder - should create state with all fields")
        void builder_ShouldCreateStateWithAllFields() {
            // Given
            LocalDateTime now = LocalDateTime.now();

            // When
            ConversationState state = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .version(5L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            // Then
            assertThat(state.getState()).isEqualTo(BotState.AWAITING_BAN_REASON);
            assertThat(state.getVersion()).isEqualTo(5L);
            assertThat(state.getCreatedAt()).isEqualTo(now);
            assertThat(state.getUpdatedAt()).isEqualTo(now);
        }
    }

    // ========== STATIC FACTORY TESTS ==========

    @Nested
    @DisplayName("Static Factory Tests")
    class StaticFactoryTests {

        @Test
        @DisplayName("idle - should create IDLE state with initialized fields")
        void idle_ShouldCreateIdleStateWithInitializedFields() {
            // When
            ConversationState state = ConversationState.idle();

            // Then
            assertThat(state.getState()).isEqualTo(BotState.IDLE);
            assertThat(state.getVersion()).isEqualTo(0L);
            assertThat(state.getCreatedAt()).isNotNull();
            assertThat(state.getUpdatedAt()).isNotNull();
            assertThat(state.getData()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("idle - should have createdAt and updatedAt set to same time")
        void idle_ShouldHaveSameCreatedAndUpdatedTime() {
            // When
            ConversationState state = ConversationState.idle();

            // Then
            assertThat(state.getCreatedAt()).isEqualTo(state.getUpdatedAt());
        }
    }

    // ========== ADD DATA TESTS ==========

    @Nested
    @DisplayName("addData Tests")
    class AddDataTests {

        @Test
        @DisplayName("addData - should add data to map")
        void addData_ShouldAddDataToMap() {
            // Given
            ConversationState state = ConversationState.idle();

            // When
            state.addData("targetUserId", "test-uuid");

            // Then
            assertThat(state.getData()).containsEntry("targetUserId", "test-uuid");
        }

        @Test
        @DisplayName("addData - should update updatedAt timestamp")
        void addData_ShouldUpdateTimestamp() throws InterruptedException {
            // Given
            ConversationState state = ConversationState.idle();
            LocalDateTime originalUpdatedAt = state.getUpdatedAt();

            // Wait a tiny bit to ensure different timestamp
            Thread.sleep(10);

            // When
            state.addData("key", "value");

            // Then
            assertThat(state.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("addData - should overwrite existing key")
        void addData_ShouldOverwriteExistingKey() {
            // Given
            ConversationState state = ConversationState.idle();
            state.addData("key", "oldValue");

            // When
            state.addData("key", "newValue");

            // Then
            assertThat(state.getData().get("key")).isEqualTo("newValue");
            assertThat(state.getData()).hasSize(1);
        }
    }

    // ========== GET DATA TESTS ==========

    @Nested
    @DisplayName("getData Tests")
    class GetDataTests {

        @Test
        @DisplayName("getData - should return typed data when exists")
        void getData_WhenExists_ShouldReturnTypedData() {
            // Given
            ConversationState state = ConversationState.idle();
            state.addData("userId", "test-uuid");

            // When
            String result = state.getData("userId", String.class);

            // Then
            assertThat(result).isEqualTo("test-uuid");
        }

        @Test
        @DisplayName("getData - should return null when key not exists")
        void getData_WhenNotExists_ShouldReturnNull() {
            // Given
            ConversationState state = ConversationState.idle();

            // When
            String result = state.getData("nonExistentKey", String.class);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getData - should handle integer values")
        void getData_WithIntegerValue_ShouldReturnInteger() {
            // Given
            ConversationState state = ConversationState.idle();
            state.addData("page", 5);

            // When
            Integer result = state.getData("page", Integer.class);

            // Then
            assertThat(result).isEqualTo(5);
        }
    }

    // ========== HAS DATA TESTS ==========

    @Nested
    @DisplayName("hasData Tests")
    class HasDataTests {

        @Test
        @DisplayName("hasData - should return true when key exists")
        void hasData_WhenKeyExists_ShouldReturnTrue() {
            // Given
            ConversationState state = ConversationState.idle();
            state.addData("key", "value");

            // When & Then
            assertThat(state.hasData("key")).isTrue();
        }

        @Test
        @DisplayName("hasData - should return false when key not exists")
        void hasData_WhenKeyNotExists_ShouldReturnFalse() {
            // Given
            ConversationState state = ConversationState.idle();

            // When & Then
            assertThat(state.hasData("nonExistentKey")).isFalse();
        }
    }

    // ========== CLEAR DATA TESTS ==========

    @Nested
    @DisplayName("clearData Tests")
    class ClearDataTests {

        @Test
        @DisplayName("clearData - should remove all data")
        void clearData_ShouldRemoveAllData() {
            // Given
            ConversationState state = ConversationState.idle();
            state.addData("key1", "value1");
            state.addData("key2", "value2");

            // When
            state.clearData();

            // Then
            assertThat(state.getData()).isEmpty();
        }

        @Test
        @DisplayName("clearData - should update updatedAt timestamp")
        void clearData_ShouldUpdateTimestamp() throws InterruptedException {
            // Given
            ConversationState state = ConversationState.idle();
            state.addData("key", "value");
            LocalDateTime originalUpdatedAt = state.getUpdatedAt();

            Thread.sleep(10);

            // When
            state.clearData();

            // Then
            assertThat(state.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }
    }

    // ========== INCREMENT VERSION TESTS ==========

    @Nested
    @DisplayName("incrementVersion Tests")
    class IncrementVersionTests {

        @Test
        @DisplayName("incrementVersion - should increase version by 1")
        void incrementVersion_ShouldIncreaseByOne() {
            // Given
            ConversationState state = ConversationState.idle();
            assertThat(state.getVersion()).isEqualTo(0L);

            // When
            state.incrementVersion();

            // Then
            assertThat(state.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("incrementVersion - should increment multiple times")
        void incrementVersion_ShouldIncrementMultipleTimes() {
            // Given
            ConversationState state = ConversationState.idle();

            // When
            state.incrementVersion();
            state.incrementVersion();
            state.incrementVersion();

            // Then
            assertThat(state.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("incrementVersion - should update updatedAt timestamp")
        void incrementVersion_ShouldUpdateTimestamp() throws InterruptedException {
            // Given
            ConversationState state = ConversationState.idle();
            LocalDateTime originalUpdatedAt = state.getUpdatedAt();

            Thread.sleep(10);

            // When
            state.incrementVersion();

            // Then
            assertThat(state.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }
    }

    // ========== BOT STATE ENUM TESTS ==========

    @Nested
    @DisplayName("BotState Enum Tests")
    class BotStateEnumTests {

        @Test
        @DisplayName("BotState - should have all expected values")
        void botState_ShouldHaveAllExpectedValues() {
            BotState[] values = BotState.values();

            assertThat(values).contains(
                    BotState.IDLE,
                    BotState.AWAITING_SEARCH_QUERY,
                    BotState.SHOWING_SEARCH_RESULTS,
                    BotState.AWAITING_ADMIN_TELEGRAM_ID,
                    BotState.AWAITING_ADMIN_ROLE,
                    BotState.CONFIRMING_ADMIN_CREATION,
                    BotState.AWAITING_BAN_REASON,
                    BotState.CONFIRMING_BAN
            );
        }

        @Test
        @DisplayName("BotState.IDLE - should be the default/initial state")
        void botStateIdle_ShouldBeDefaultState() {
            ConversationState state = ConversationState.idle();
            assertThat(state.getState()).isEqualTo(BotState.IDLE);
        }
    }
}
