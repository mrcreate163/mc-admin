package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartCommandHandler Unit Tests")
class StartCommandHandlerTest {

    @InjectMocks
    private StartCommandHandler startCommandHandler;

    private Message mockMessage;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);
        mockUser = mock(User.class);
    }

    @Test
    @DisplayName("handle - should return welcome message with user name")
    void handle_ShouldReturnWelcomeMessageWithUserName() {
        // Given
        when(mockMessage.getChatId()).thenReturn(12345L);
        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getFirstName()).thenReturn("Иван");

        // When
        SendMessage result = startCommandHandler.handle(mockMessage);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getChatId()).isEqualTo("12345");
        assertThat(result.getText()).contains("Добро пожаловать");
        assertThat(result.getText()).contains("Иван");
        assertThat(result.getParseMode()).isEqualTo("HTML");
        assertThat(result.getReplyMarkup()).isNotNull();
    }

    @Test
    @DisplayName("handle - should return default name when firstName is null")
    void handle_WhenFirstNameNull_ShouldUseDefaultName() {
        // Given
        when(mockMessage.getChatId()).thenReturn(12345L);
        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getFirstName()).thenReturn(null);

        // When
        SendMessage result = startCommandHandler.handle(mockMessage);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Админ");
    }

    @Test
    @DisplayName("handle - should escape HTML in user name")
    void handle_ShouldEscapeHtmlInUserName() {
        // Given
        when(mockMessage.getChatId()).thenReturn(12345L);
        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getFirstName()).thenReturn("<script>alert('xss')</script>");

        // When
        SendMessage result = startCommandHandler.handle(mockMessage);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).doesNotContain("<script>");
        assertThat(result.getText()).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("handle - should include all commands in response")
    void handle_ShouldIncludeAllCommands() {
        // Given
        when(mockMessage.getChatId()).thenReturn(12345L);
        when(mockMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getFirstName()).thenReturn("Test");

        // When
        SendMessage result = startCommandHandler.handle(mockMessage);

        // Then
        assertThat(result.getText()).contains("/start");
        assertThat(result.getText()).contains("/user");
        assertThat(result.getText()).contains("/ban");
        assertThat(result.getText()).contains("/unban");
        assertThat(result.getText()).contains("/stats");
    }
}
