package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TextMessageHandler Unit Tests")
class TextMessageHandlerTest {

    @Mock
    private ConversationStateService conversationStateService;

    @Mock
    private BanCommandHandler banCommandHandler;

    @Mock
    private SearchCommandHandler searchCommandHandler;

    private TextMessageHandler textMessageHandler;

    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;

    @BeforeEach
    void setUp() {
        textMessageHandler = new TextMessageHandler(conversationStateService, banCommandHandler, searchCommandHandler);
        mockMessage = mock(Message.class);
        lenient().when(mockMessage.getChatId()).thenReturn(CHAT_ID);
    }

    // ========== IDLE STATE TESTS ==========

    @Nested
    @DisplayName("IDLE State Tests")
    class IdleStateTests {

        @Test
        @DisplayName("handle - should return unknown command message when in IDLE state")
        void handle_WhenInIdleState_ShouldReturnUnknownCommandMessage() {
            // Given
            ConversationState idleState = ConversationState.idle();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(idleState);

            // When
            SendMessage result = textMessageHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.ERROR_UNKNOWN_COMMAND.raw());
            verify(banCommandHandler, never()).handleConversationStep(any(), anyLong(), any());
        }
    }

    // ========== AWAITING_BAN_REASON STATE TESTS ==========

    @Nested
    @DisplayName("AWAITING_BAN_REASON State Tests")
    class AwaitingBanReasonStateTests {

        @Test
        @DisplayName("handle - should delegate to BanCommandHandler when in AWAITING_BAN_REASON state")
        void handle_WhenInAwaitingBanReasonState_ShouldDelegateToBanHandler() {
            // Given
            ConversationState awaitingReasonState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(awaitingReasonState);

            SendMessage expectedResponse = new SendMessage();
            expectedResponse.setChatId(CHAT_ID.toString());
            expectedResponse.setText("Expected response");
            when(banCommandHandler.handleConversationStep(mockMessage, ADMIN_TELEGRAM_ID, awaitingReasonState))
                    .thenReturn(expectedResponse);

            // When
            SendMessage result = textMessageHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isEqualTo(expectedResponse);
            verify(banCommandHandler).handleConversationStep(mockMessage, ADMIN_TELEGRAM_ID, awaitingReasonState);
        }
    }

    // ========== AWAITING_SEARCH_QUERY STATE TESTS ==========

    @Nested
    @DisplayName("AWAITING_SEARCH_QUERY State Tests")
    class AwaitingSearchQueryStateTests {

        @Test
        @DisplayName("handle - should delegate to SearchCommandHandler when in AWAITING_SEARCH_QUERY state")
        void handle_WhenInAwaitingSearchQueryState_ShouldDelegateToSearchHandler() {
            // Given
            String searchQuery = "test@example.com";
            ConversationState searchState = ConversationState.builder()
                    .state(BotState.AWAITING_SEARCH_QUERY)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(searchState);
            when(mockMessage.getText()).thenReturn(searchQuery);

            SendMessage expectedResponse = new SendMessage();
            expectedResponse.setChatId(CHAT_ID.toString());
            expectedResponse.setText("Search results");
            when(searchCommandHandler.processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, searchQuery))
                    .thenReturn(expectedResponse);

            // When
            SendMessage result = textMessageHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isEqualTo(expectedResponse);
            verify(searchCommandHandler).processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, searchQuery);
        }
    }

    // ========== AWAITING_ADMIN_TELEGRAM_ID STATE TESTS ==========

    @Nested
    @DisplayName("AWAITING_ADMIN_TELEGRAM_ID State Tests")
    class AwaitingAdminTelegramIdStateTests {

        @Test
        @DisplayName("handle - should return coming soon message for admin management state")
        void handle_WhenInAwaitingAdminTelegramIdState_ShouldReturnComingSoonMessage() {
            // Given
            ConversationState adminState = ConversationState.builder()
                    .state(BotState.AWAITING_ADMIN_TELEGRAM_ID)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(adminState);

            // When
            SendMessage result = textMessageHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("coming soon");
        }
    }

    // ========== UNHANDLED STATE TESTS ==========

    @Nested
    @DisplayName("Unhandled State Tests")
    class UnhandledStateTests {

        @Test
        @DisplayName("handle - should return error for unhandled state")
        void handle_WhenInUnhandledState_ShouldReturnErrorMessage() {
            // Given
            ConversationState confirmingBanState = ConversationState.builder()
                    .state(BotState.CONFIRMING_BAN)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(confirmingBanState);

            // When
            SendMessage result = textMessageHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.ERROR_UNKNOWN_STATE.raw());
        }
    }
}
