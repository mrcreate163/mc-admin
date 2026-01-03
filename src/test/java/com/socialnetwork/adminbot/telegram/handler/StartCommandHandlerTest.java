package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.exception.DuplicateAdminException;
import com.socialnetwork.adminbot.service.AdminService;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.InviteService;
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
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartCommandHandler Unit Tests")
class StartCommandHandlerTest {

    @Mock
    private AdminService adminService;

    @Mock
    private InviteService inviteService;

    @Mock
    private AuditLogService auditLogService;

    private StartCommandHandler startCommandHandler;

    private Message mockMessage;
    private User mockUser;

    private static final Long TELEGRAM_USER_ID = 123456789L;
    private static final Long CHAT_ID = 12345L;

    @BeforeEach
    void setUp() {
        startCommandHandler = new StartCommandHandler(adminService, inviteService, auditLogService);
        mockMessage = mock(Message.class);
        mockUser = mock(User.class);

        lenient().when(mockMessage.getChatId()).thenReturn(CHAT_ID);
        lenient().when(mockMessage.getFrom()).thenReturn(mockUser);
        lenient().when(mockUser.getId()).thenReturn(TELEGRAM_USER_ID);
    }

    @Nested
    @DisplayName("Regular Start Tests")
    class RegularStartTests {

        @Test
        @DisplayName("handle - should return welcome message for existing admin")
        void handle_WhenExistingAdmin_ShouldReturnWelcomeMessage() {
            // Given
            when(mockMessage.getText()).thenReturn("/start");
            when(mockUser.getFirstName()).thenReturn("Иван");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(true);

            Admin admin = Admin.builder()
                    .telegramUserId(TELEGRAM_USER_ID)
                    .username("ivan")
                    .firstName("Иван")
                    .role(AdminRole.ADMIN)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(adminService.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(admin);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getChatId()).isEqualTo(CHAT_ID.toString());
            assertThat(result.getText()).contains("Добро пожаловать");
            assertThat(result.getText()).contains("Иван");
            assertThat(result.getText()).contains("ADMIN");
            assertThat(result.getParseMode()).isEqualTo("HTML");
            assertThat(result.getReplyMarkup()).isNotNull();

            verify(auditLogService).logAction(eq("BOT_START"), eq(TELEGRAM_USER_ID), anyMap());
        }

        @Test
        @DisplayName("handle - should return unauthorized message for non-admin")
        void handle_WhenNotAdmin_ShouldReturnUnauthorizedMessage() {
            // Given
            when(mockMessage.getText()).thenReturn("/start");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(false);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("не зарегистрирован");
            assertThat(result.getText()).contains("SUPER_ADMIN");
        }

        @Test
        @DisplayName("handle - should escape HTML in user name")
        void handle_WhenHtmlInName_ShouldEscape() {
            // Given
            when(mockMessage.getText()).thenReturn("/start");
            when(mockUser.getFirstName()).thenReturn("<script>alert('xss')</script>");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(true);

            Admin admin = Admin.builder()
                    .telegramUserId(TELEGRAM_USER_ID)
                    .role(AdminRole.ADMIN)
                    .build();
            when(adminService.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(admin);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).doesNotContain("<script>");
            assertThat(result.getText()).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("handle - should use default name when firstName is null")
        void handle_WhenFirstNameNull_ShouldUseDefault() {
            // Given
            when(mockMessage.getText()).thenReturn("/start");
            when(mockUser.getFirstName()).thenReturn(null);
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(true);

            Admin admin = Admin.builder()
                    .telegramUserId(TELEGRAM_USER_ID)
                    .role(AdminRole.MODERATOR)
                    .build();
            when(adminService.findByTelegramId(TELEGRAM_USER_ID)).thenReturn(admin);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Администратор");
        }
    }

    @Nested
    @DisplayName("Deep Link Invite Tests")
    class DeepLinkInviteTests {

        private static final String INVITE_TOKEN = "testToken123456789";

        @Test
        @DisplayName("handle - should activate admin via invite link")
        void handle_WhenValidInviteLink_ShouldActivateAdmin() {
            // Given
            when(mockMessage.getText()).thenReturn("/start invite_" + INVITE_TOKEN);
            when(mockUser.getFirstName()).thenReturn("New Admin");
            when(mockUser.getUserName()).thenReturn("newadmin");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(false);

            Admin activatedAdmin = Admin.builder()
                    .telegramUserId(TELEGRAM_USER_ID)
                    .username("newadmin")
                    .firstName("New Admin")
                    .role(AdminRole.ADMIN)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .invitedBy(999L)
                    .build();

            when(inviteService.activateInvitation(eq(INVITE_TOKEN), eq(TELEGRAM_USER_ID), eq("newadmin"), eq("New Admin")))
                    .thenReturn(activatedAdmin);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("успешно активирован");
            assertThat(result.getText()).contains("ADMIN");
            assertThat(result.getReplyMarkup()).isNotNull();

            verify(inviteService).activateInvitation(INVITE_TOKEN, TELEGRAM_USER_ID, "newadmin", "New Admin");
        }

        @Test
        @DisplayName("handle - should return error when already admin")
        void handle_WhenAlreadyAdmin_ShouldReturnError() {
            // Given
            when(mockMessage.getText()).thenReturn("/start invite_" + INVITE_TOKEN);
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(true);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("уже активирован");

            verify(inviteService, never()).activateInvitation(any(), any(), any(), any());
        }

        @Test
        @DisplayName("handle - should return error when invite token invalid")
        void handle_WhenInvalidToken_ShouldReturnError() {
            // Given
            when(mockMessage.getText()).thenReturn("/start invite_invalid");
            when(mockUser.getFirstName()).thenReturn("Test");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(false);

            when(inviteService.activateInvitation(eq("invalid"), eq(TELEGRAM_USER_ID), any(), any()))
                    .thenThrow(new IllegalArgumentException("❌ Приглашение не найдено."));

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Ошибка активации");
            assertThat(result.getText()).contains("не найдено");
        }

        @Test
        @DisplayName("handle - should use fallback name when firstName is null or blank")
        void handle_WhenFirstNameNullInInvite_ShouldUseFallback() {
            // Given
            when(mockMessage.getText()).thenReturn("/start invite_" + INVITE_TOKEN);
            when(mockUser.getFirstName()).thenReturn("   ");
            when(mockUser.getUserName()).thenReturn("user123");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(false);

            Admin activatedAdmin = Admin.builder()
                    .telegramUserId(TELEGRAM_USER_ID)
                    .role(AdminRole.MODERATOR)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(inviteService.activateInvitation(eq(INVITE_TOKEN), eq(TELEGRAM_USER_ID), eq("user123"), eq("Admin")))
                    .thenReturn(activatedAdmin);

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            verify(inviteService).activateInvitation(INVITE_TOKEN, TELEGRAM_USER_ID, "user123", "Admin");
        }

        @Test
        @DisplayName("handle - should return error when duplicate admin exception")
        void handle_WhenDuplicateAdmin_ShouldReturnError() {
            // Given
            when(mockMessage.getText()).thenReturn("/start invite_" + INVITE_TOKEN);
            when(mockUser.getFirstName()).thenReturn("Test");
            when(adminService.isAdmin(TELEGRAM_USER_ID)).thenReturn(false);

            when(inviteService.activateInvitation(any(), any(), any(), any()))
                    .thenThrow(new DuplicateAdminException("❌ Этот Telegram аккаунт уже зарегистрирован."));

            // When
            SendMessage result = startCommandHandler.handle(mockMessage);

            // Then
            assertThat(result).isNotNull();
            // Generic error is returned when DuplicateAdminException is caught
            assertThat(result.getText()).contains("Ошибка");
        }
    }
}
