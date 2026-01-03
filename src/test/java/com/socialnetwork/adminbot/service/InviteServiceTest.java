package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.entity.AdminInvitation;
import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.exception.DuplicateAdminException;
import com.socialnetwork.adminbot.exception.UnauthorizedException;
import com.socialnetwork.adminbot.repository.AdminInvitationRepository;
import com.socialnetwork.adminbot.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteService Unit Tests")
class InviteServiceTest {

    @Mock
    private AdminInvitationRepository invitationRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AuditLogService auditLogService;

    private InviteService inviteService;

    private Admin superAdmin;
    private Admin regularAdmin;
    private AdminInvitation testInvitation;

    private static final Long SUPER_ADMIN_ID = 123456789L;
    private static final Long REGULAR_ADMIN_ID = 987654321L;
    private static final Long NEW_ADMIN_ID = 555555555L;
    private static final String TEST_TOKEN = "testToken123456789";

    @BeforeEach
    void setUp() {
        inviteService = new InviteService(invitationRepository, adminRepository, auditLogService);

        superAdmin = Admin.builder()
                .telegramUserId(SUPER_ADMIN_ID)
                .username("superadmin")
                .firstName("Super")
                .role(AdminRole.SUPER_ADMIN)
                .isActive(true)
                .build();

        regularAdmin = Admin.builder()
                .telegramUserId(REGULAR_ADMIN_ID)
                .username("admin")
                .firstName("Admin")
                .role(AdminRole.ADMIN)
                .isActive(true)
                .build();

        testInvitation = AdminInvitation.builder()
                .id(UUID.randomUUID())
                .inviteToken(TEST_TOKEN)
                .role(AdminRole.ADMIN)
                .createdBy(SUPER_ADMIN_ID)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .isUsed(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createInvitation Tests")
    class CreateInvitationTests {

        @Test
        @DisplayName("should create invitation successfully when SUPER_ADMIN creates for lower role")
        void createInvitation_WhenSuperAdminCreatesForLowerRole_ShouldSucceed() {
            // Given
            when(adminRepository.findByTelegramUserId(SUPER_ADMIN_ID)).thenReturn(Optional.of(superAdmin));
            when(invitationRepository.existsByInviteToken(anyString())).thenReturn(false);
            when(invitationRepository.save(any(AdminInvitation.class))).thenAnswer(invocation -> {
                AdminInvitation inv = invocation.getArgument(0);
                inv.setId(UUID.randomUUID());
                return inv;
            });

            // When
            String token = inviteService.createInvitation(SUPER_ADMIN_ID, AdminRole.ADMIN);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();

            ArgumentCaptor<AdminInvitation> captor = ArgumentCaptor.forClass(AdminInvitation.class);
            verify(invitationRepository).save(captor.capture());

            AdminInvitation saved = captor.getValue();
            assertThat(saved.getRole()).isEqualTo(AdminRole.ADMIN);
            assertThat(saved.getCreatedBy()).isEqualTo(SUPER_ADMIN_ID);
            assertThat(saved.getIsUsed()).isFalse();

            verify(auditLogService).logAction(eq("CREATE_INVITATION"), eq(SUPER_ADMIN_ID), anyMap());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when creator is not SUPER_ADMIN")
        void createInvitation_WhenNotSuperAdmin_ShouldThrowException() {
            // Given
            when(adminRepository.findByTelegramUserId(REGULAR_ADMIN_ID)).thenReturn(Optional.of(regularAdmin));

            // When & Then
            assertThatThrownBy(() -> inviteService.createInvitation(REGULAR_ADMIN_ID, AdminRole.MODERATOR))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Only SUPER_ADMIN can create invitations");

            verify(invitationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw UnauthorizedException when creator not found")
        void createInvitation_WhenCreatorNotFound_ShouldThrowException() {
            // Given
            when(adminRepository.findByTelegramUserId(anyLong())).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> inviteService.createInvitation(999L, AdminRole.ADMIN))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Admin not found");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when trying to assign SUPER_ADMIN role")
        void createInvitation_WhenAssigningSuperAdminRole_ShouldThrowException() {
            // Given
            when(adminRepository.findByTelegramUserId(SUPER_ADMIN_ID)).thenReturn(Optional.of(superAdmin));

            // When & Then
            assertThatThrownBy(() -> inviteService.createInvitation(SUPER_ADMIN_ID, AdminRole.SUPER_ADMIN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot assign role");
        }
    }

    @Nested
    @DisplayName("activateInvitation Tests")
    class ActivateInvitationTests {

        @Test
        @DisplayName("should activate invitation successfully")
        void activateInvitation_WhenValid_ShouldCreateAdmin() {
            // Given
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(testInvitation));
            when(adminRepository.existsByTelegramUserId(NEW_ADMIN_ID)).thenReturn(false);
            when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(invitationRepository.save(any(AdminInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Admin result = inviteService.activateInvitation(TEST_TOKEN, NEW_ADMIN_ID, "newadmin", "New Admin");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTelegramUserId()).isEqualTo(NEW_ADMIN_ID);
            assertThat(result.getRole()).isEqualTo(AdminRole.ADMIN);
            assertThat(result.getInvitedBy()).isEqualTo(SUPER_ADMIN_ID);

            verify(adminRepository).save(any(Admin.class));
            verify(invitationRepository).save(argThat(inv -> inv.getIsUsed() && inv.getActivatedAdminId().equals(NEW_ADMIN_ID)));
            verify(auditLogService).logAction(eq("ACTIVATE_INVITATION"), eq(NEW_ADMIN_ID), anyMap());
        }

        @Test
        @DisplayName("should throw exception when token not found")
        void activateInvitation_WhenTokenNotFound_ShouldThrowException() {
            // Given
            when(invitationRepository.findByInviteToken(anyString())).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> inviteService.activateInvitation("invalid", NEW_ADMIN_ID, null, "Name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не найдено");
        }

        @Test
        @DisplayName("should throw exception when invitation already used")
        void activateInvitation_WhenAlreadyUsed_ShouldThrowException() {
            // Given
            testInvitation.markAsUsed(999L);
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(testInvitation));

            // When & Then
            assertThatThrownBy(() -> inviteService.activateInvitation(TEST_TOKEN, NEW_ADMIN_ID, null, "Name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("уже было использовано");
        }

        @Test
        @DisplayName("should throw exception when invitation expired")
        void activateInvitation_WhenExpired_ShouldThrowException() {
            // Given
            AdminInvitation expiredInvitation = AdminInvitation.builder()
                    .id(UUID.randomUUID())
                    .inviteToken(TEST_TOKEN)
                    .role(AdminRole.ADMIN)
                    .createdBy(SUPER_ADMIN_ID)
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .isUsed(false)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build();
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(expiredInvitation));

            // When & Then
            assertThatThrownBy(() -> inviteService.activateInvitation(TEST_TOKEN, NEW_ADMIN_ID, null, "Name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("истёк");
        }

        @Test
        @DisplayName("should throw DuplicateAdminException when admin already exists")
        void activateInvitation_WhenAdminExists_ShouldThrowException() {
            // Given
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(testInvitation));
            when(adminRepository.existsByTelegramUserId(NEW_ADMIN_ID)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> inviteService.activateInvitation(TEST_TOKEN, NEW_ADMIN_ID, null, "Name"))
                    .isInstanceOf(DuplicateAdminException.class)
                    .hasMessageContaining("уже зарегистрирован");
        }
    }

    @Nested
    @DisplayName("isTokenValid Tests")
    class IsTokenValidTests {

        @Test
        @DisplayName("should return true when token is valid")
        void isTokenValid_WhenValid_ShouldReturnTrue() {
            // Given
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(testInvitation));

            // When
            boolean result = inviteService.isTokenValid(TEST_TOKEN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when token not found")
        void isTokenValid_WhenNotFound_ShouldReturnFalse() {
            // Given
            when(invitationRepository.findByInviteToken(anyString())).thenReturn(Optional.empty());

            // When
            boolean result = inviteService.isTokenValid("invalid");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when invitation is used")
        void isTokenValid_WhenUsed_ShouldReturnFalse() {
            // Given
            testInvitation.markAsUsed(999L);
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(testInvitation));

            // When
            boolean result = inviteService.isTokenValid(TEST_TOKEN);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getInvitationByToken Tests")
    class GetInvitationByTokenTests {

        @Test
        @DisplayName("should return invitation when found")
        void getInvitationByToken_WhenFound_ShouldReturn() {
            // Given
            when(invitationRepository.findByInviteToken(TEST_TOKEN)).thenReturn(Optional.of(testInvitation));

            // When
            AdminInvitation result = inviteService.getInvitationByToken(TEST_TOKEN);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getInviteToken()).isEqualTo(TEST_TOKEN);
        }

        @Test
        @DisplayName("should throw exception when not found")
        void getInvitationByToken_WhenNotFound_ShouldThrowException() {
            // Given
            when(invitationRepository.findByInviteToken(anyString())).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> inviteService.getInvitationByToken("invalid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не найдено");
        }
    }

    @Nested
    @DisplayName("getAllActiveInvitations Tests")
    class GetAllActiveInvitationsTests {

        @Test
        @DisplayName("should return all active invitations")
        void getAllActiveInvitations_ShouldReturnList() {
            // Given
            List<AdminInvitation> invitations = List.of(testInvitation);
            when(invitationRepository.findAllActive(any(LocalDateTime.class))).thenReturn(invitations);

            // When
            List<AdminInvitation> result = inviteService.getAllActiveInvitations();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getInviteToken()).isEqualTo(TEST_TOKEN);
        }
    }

    @Nested
    @DisplayName("getInvitationsByCreator Tests")
    class GetInvitationsByCreatorTests {

        @Test
        @DisplayName("should return invitations by creator")
        void getInvitationsByCreator_ShouldReturnList() {
            // Given
            List<AdminInvitation> invitations = List.of(testInvitation);
            when(invitationRepository.findByCreatedByOrderByCreatedAtDesc(SUPER_ADMIN_ID)).thenReturn(invitations);

            // When
            List<AdminInvitation> result = inviteService.getInvitationsByCreator(SUPER_ADMIN_ID);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCreatedBy()).isEqualTo(SUPER_ADMIN_ID);
        }
    }
}
