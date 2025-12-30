package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchCommandHandler Unit Tests")
class SearchCommandHandlerTest {

    @Mock
    private ConversationStateService conversationStateService;

    @Mock
    private StateTransitionService stateTransitionService;

    @Mock
    private UserService userService;

    private SearchCommandHandler searchCommandHandler;

    private Message mockMessage;
    private static final Long ADMIN_TELEGRAM_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        searchCommandHandler = new SearchCommandHandler(
                conversationStateService,
                stateTransitionService,
                userService
        );

        mockMessage = mock(Message.class);
        lenient().when(mockMessage.getChatId()).thenReturn(CHAT_ID);
    }

    // ========== START CONVERSATION TESTS ==========

    @Nested
    @DisplayName("Start Conversation Tests")
    class StartConversationTests {

        @BeforeEach
        void setUpIdleState() {
            ConversationState idleState = ConversationState.idle();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(idleState);
        }

        @Test
        @DisplayName("handle - should enter awaiting query state when no argument provided")
        void handle_WhenNoArgument_ShouldEnterAwaitingQueryState() {
            // Given
            when(mockMessage.getText()).thenReturn("/search");

            // When
            SendMessage result = searchCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.SEARCH_PROMPT.raw());

            // Verify state was saved
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(conversationStateService).setState(eq(ADMIN_TELEGRAM_ID), stateCaptor.capture());
            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getState()).isEqualTo(BotState.AWAITING_SEARCH_QUERY);
        }

        @Test
        @DisplayName("handle - should search immediately when query provided as argument")
        void handle_WhenQueryProvided_ShouldSearchImmediately() {
            // Given
            when(mockMessage.getText()).thenReturn("/search test@example.com");
            
            PageAccountDto searchResults = createSearchResults(1);
            when(userService.searchUsersByEmail("test@example.com", 0, 5)).thenReturn(searchResults);

            // When
            SendMessage result = searchCommandHandler.handle(mockMessage, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("test@example.com");
            verify(userService).searchUsersByEmail("test@example.com", 0, 5);
        }
    }

    // ========== PROCESS SEARCH QUERY TESTS ==========

    @Nested
    @DisplayName("Process Search Query Tests")
    class ProcessSearchQueryTests {

        @Test
        @DisplayName("processSearchQuery - should reject query shorter than 3 characters")
        void processSearchQuery_WhenQueryTooShort_ShouldRejectWithMessage() {
            // When
            SendMessage result = searchCommandHandler.processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, "ab");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.SEARCH_MIN_LENGTH.raw());
            verify(userService, never()).searchUsersByEmail(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("processSearchQuery - should reject invalid query format")
        void processSearchQuery_WhenInvalidFormat_ShouldRejectWithMessage() {
            // When
            SendMessage result = searchCommandHandler.processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, "test<script>");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.SEARCH_INVALID_QUERY.raw());
            verify(userService, never()).searchUsersByEmail(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("processSearchQuery - should return no results message when nothing found")
        void processSearchQuery_WhenNoResults_ShouldReturnNoResultsMessage() {
            // Given
            PageAccountDto emptyResults = PageAccountDto.builder()
                    .content(Collections.emptyList())
                    .totalElements(0)
                    .totalPages(0)
                    .build();
            when(userService.searchUsersByEmail("notfound@example.com", 0, 5)).thenReturn(emptyResults);

            // When
            SendMessage result = searchCommandHandler.processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, "notfound@example.com");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Ничего не найдено");
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }

        @Test
        @DisplayName("processSearchQuery - should return results with keyboard when users found")
        void processSearchQuery_WhenUsersFound_ShouldReturnResultsWithKeyboard() {
            // Given
            PageAccountDto searchResults = createSearchResults(3);
            when(userService.searchUsersByEmail("test@example.com", 0, 5)).thenReturn(searchResults);

            // When
            SendMessage result = searchCommandHandler.processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, "test@example.com");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Результаты поиска");
            assertThat(result.getText()).contains("test@example.com");
            assertThat(result.getReplyMarkup()).isNotNull();

            // Verify state was saved with search metadata
            ArgumentCaptor<ConversationState> stateCaptor = ArgumentCaptor.forClass(ConversationState.class);
            verify(conversationStateService).setState(eq(ADMIN_TELEGRAM_ID), stateCaptor.capture());
            ConversationState savedState = stateCaptor.getValue();
            assertThat(savedState.getState()).isEqualTo(BotState.SHOWING_SEARCH_RESULTS);
            assertThat(savedState.getData(StateDataKey.SEARCH_QUERY, String.class)).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("processSearchQuery - should reset state on exception")
        void processSearchQuery_WhenException_ShouldResetState() {
            // Given
            when(userService.searchUsersByEmail(any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Service error"));

            // When
            SendMessage result = searchCommandHandler.processSearchQuery(CHAT_ID, ADMIN_TELEGRAM_ID, "test@example.com");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Ошибка");
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }
    }

    // ========== PAGE NAVIGATION TESTS ==========

    @Nested
    @DisplayName("Page Navigation Tests")
    class PageNavigationTests {

        @Test
        @DisplayName("handlePageNavigation - should reject when not in search results state")
        void handlePageNavigation_WhenNotInSearchResultsState_ShouldRejectWithMessage() {
            // Given
            ConversationState idleState = ConversationState.idle();
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(idleState);

            // When
            SendMessage result = searchCommandHandler.handlePageNavigation(CHAT_ID, ADMIN_TELEGRAM_ID, 1);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Поиск устарел");
        }

        @Test
        @DisplayName("handlePageNavigation - should reject invalid page number")
        void handlePageNavigation_WhenInvalidPageNumber_ShouldRejectWithMessage() {
            // Given
            ConversationState searchResultsState = ConversationState.builder()
                    .state(BotState.SHOWING_SEARCH_RESULTS)
                    .build();
            searchResultsState.addData(StateDataKey.SEARCH_QUERY, "test@example.com");
            searchResultsState.addData(StateDataKey.SEARCH_TOTAL_PAGES, 3);
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(searchResultsState);

            // When
            SendMessage result = searchCommandHandler.handlePageNavigation(CHAT_ID, ADMIN_TELEGRAM_ID, 5);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Некорректный номер страницы");
        }

        @Test
        @DisplayName("handlePageNavigation - should navigate to valid page")
        void handlePageNavigation_WhenValidPage_ShouldNavigate() {
            // Given
            ConversationState searchResultsState = ConversationState.builder()
                    .state(BotState.SHOWING_SEARCH_RESULTS)
                    .build();
            searchResultsState.addData(StateDataKey.SEARCH_QUERY, "test@example.com");
            searchResultsState.addData(StateDataKey.SEARCH_TOTAL_PAGES, 3);
            when(conversationStateService.getState(ADMIN_TELEGRAM_ID)).thenReturn(searchResultsState);

            PageAccountDto searchResults = createSearchResults(5);
            when(userService.searchUsersByEmail("test@example.com", 1, 5)).thenReturn(searchResults);

            // When
            SendMessage result = searchCommandHandler.handlePageNavigation(CHAT_ID, ADMIN_TELEGRAM_ID, 1);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Результаты поиска");
            verify(conversationStateService).updateStateData(ADMIN_TELEGRAM_ID, StateDataKey.SEARCH_CURRENT_PAGE, 1);
        }
    }

    // ========== CANCEL SEARCH TESTS ==========

    @Nested
    @DisplayName("Cancel Search Tests")
    class CancelSearchTests {

        @Test
        @DisplayName("cancelSearch - should reset state and confirm cancellation")
        void cancelSearch_ShouldResetStateAndConfirm() {
            // When
            SendMessage result = searchCommandHandler.cancelSearch(CHAT_ID, ADMIN_TELEGRAM_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains(BotMessage.SEARCH_CANCELLED.raw());
            verify(conversationStateService).resetToIdle(ADMIN_TELEGRAM_ID);
        }
    }

    // ========== GET COMMAND NAME ==========

    @Test
    @DisplayName("getCommandName - should return 'search'")
    void getCommandName_ShouldReturnSearch() {
        assertThat(searchCommandHandler.getCommandName()).isEqualTo("search");
    }

    // ========== HELPER METHODS ==========

    private PageAccountDto createSearchResults(int count) {
        List<AccountDto> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            accounts.add(AccountDto.builder()
                    .id(UUID.randomUUID())
                    .email("user" + i + "@example.com")
                    .firstName("User" + i)
                    .lastName("Test")
                    .isBlocked(false)
                    .build());
        }

        return PageAccountDto.builder()
                .content(accounts)
                .totalElements(count)
                .totalPages((count / 5) + 1)
                .number(0)
                .size(5)
                .build();
    }
}
