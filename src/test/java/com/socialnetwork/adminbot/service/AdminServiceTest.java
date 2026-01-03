package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.dto.AdminDto;
import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.exception.DuplicateAdminException;
import com.socialnetwork.adminbot.exception.UnauthorizedException;
import com.socialnetwork.adminbot.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminService adminService;

    private Admin testAdmin;
    private AdminDto testAdminDto;
    private static final Long TELEGRAM_USER_ID = 123456789L;

    @BeforeEach
    void setUp() {
        testAdmin = Admin.builder()
                .telegramUserId(TELEGRAM_USER_ID)
                .username("testadmin")
                .firstName("Test")
                .role(AdminRole.ADMIN)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testAdminDto = AdminDto.builder()
                .telegramUserId(TELEGRAM_USER_ID)
                .username("testadmin")
                .firstName("Test")
                .role(AdminRole.ADMIN)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("findByTelegramId - should return admin when found")
    void findByTelegramId_WhenAdminExists_ShouldReturnAdmin() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(testAdmin));

        // When
        Admin result = adminService.findByTelegramId(TELEGRAM_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTelegramUserId()).isEqualTo(TELEGRAM_USER_ID);
        assertThat(result.getUsername()).isEqualTo("testadmin");
        verify(adminRepository).findByTelegramUserId(TELEGRAM_USER_ID);
    }

    @Test
    @DisplayName("findByTelegramId - should throw exception when not found")
    void findByTelegramId_WhenAdminNotExists_ShouldThrowException() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminService.findByTelegramId(TELEGRAM_USER_ID))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Admin not found");

        verify(adminRepository).findByTelegramUserId(TELEGRAM_USER_ID);
    }

    @Test
    @DisplayName("isAdmin - should return true when admin exists")
    void isAdmin_WhenAdminExists_ShouldReturnTrue() {
        // Given
        when(adminRepository.existsByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(true);

        // When
        boolean result = adminService.isAdmin(TELEGRAM_USER_ID);

        // Then
        assertThat(result).isTrue();
        verify(adminRepository).existsByTelegramUserId(TELEGRAM_USER_ID);
    }

    @Test
    @DisplayName("isAdmin - should return false when admin does not exist")
    void isAdmin_WhenAdminNotExists_ShouldReturnFalse() {
        // Given
        when(adminRepository.existsByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(false);

        // When
        boolean result = adminService.isAdmin(TELEGRAM_USER_ID);

        // Then
        assertThat(result).isFalse();
        verify(adminRepository).existsByTelegramUserId(TELEGRAM_USER_ID);
    }

    @Test
    @DisplayName("createAdmin - should create new admin successfully")
    void createAdmin_WhenAdminNotExists_ShouldCreateAdmin() {
        // Given
        when(adminRepository.existsByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenReturn(testAdmin);

        // When
        Admin result = adminService.createAdmin(testAdminDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTelegramUserId()).isEqualTo(TELEGRAM_USER_ID);
        verify(adminRepository).existsByTelegramUserId(TELEGRAM_USER_ID);
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    @DisplayName("createAdmin - should throw exception when admin already exists")
    void createAdmin_WhenAdminExists_ShouldThrowException() {
        // Given
        when(adminRepository.existsByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> adminService.createAdmin(testAdminDto))
                .isInstanceOf(DuplicateAdminException.class)
                .hasMessage("Admin already exists");

        verify(adminRepository).existsByTelegramUserId(TELEGRAM_USER_ID);
        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllActiveAdmins - should return list of active admins")
    void getAllActiveAdmins_ShouldReturnActiveAdmins() {
        // Given
        List<Admin> activeAdmins = List.of(testAdmin);
        when(adminRepository.findByIsActiveTrue()).thenReturn(activeAdmins);

        // When
        List<Admin> result = adminService.getAllActiveAdmins();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
        verify(adminRepository).findByIsActiveTrue();
    }

    // ========== NEW TESTS FOR hasRole, hasPermission, getRole, createAdminFromInvite ==========

    @Test
    @DisplayName("hasRole - should return true when admin has exact role")
    void hasRole_WhenAdminHasExactRole_ShouldReturnTrue() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(testAdmin));

        // When
        boolean result = adminService.hasRole(TELEGRAM_USER_ID, AdminRole.ADMIN);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasRole - should return false when admin has different role")
    void hasRole_WhenAdminHasDifferentRole_ShouldReturnFalse() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(testAdmin));

        // When
        boolean result = adminService.hasRole(TELEGRAM_USER_ID, AdminRole.SUPER_ADMIN);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasRole - should return false when admin not found")
    void hasRole_WhenAdminNotFound_ShouldReturnFalse() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        // When
        boolean result = adminService.hasRole(TELEGRAM_USER_ID, AdminRole.ADMIN);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasPermission - should return true when admin has sufficient permission")
    void hasPermission_WhenSufficientPermission_ShouldReturnTrue() {
        // Given - testAdmin has ADMIN role (level 3)
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(testAdmin));

        // When - checking permission for MODERATOR (level 1)
        boolean result = adminService.hasPermission(TELEGRAM_USER_ID, AdminRole.MODERATOR);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasPermission - should return false when admin lacks permission")
    void hasPermission_WhenInsufficientPermission_ShouldReturnFalse() {
        // Given - testAdmin has ADMIN role (level 3)
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(testAdmin));

        // When - checking permission for SUPER_ADMIN (level 4)
        boolean result = adminService.hasPermission(TELEGRAM_USER_ID, AdminRole.SUPER_ADMIN);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasPermission - should return false when admin not found")
    void hasPermission_WhenAdminNotFound_ShouldReturnFalse() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        // When
        boolean result = adminService.hasPermission(TELEGRAM_USER_ID, AdminRole.MODERATOR);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getRole - should return admin role")
    void getRole_WhenAdminExists_ShouldReturnRole() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.of(testAdmin));

        // When
        AdminRole result = adminService.getRole(TELEGRAM_USER_ID);

        // Then
        assertThat(result).isEqualTo(AdminRole.ADMIN);
    }

    @Test
    @DisplayName("getRole - should throw exception when admin not found")
    void getRole_WhenAdminNotFound_ShouldThrowException() {
        // Given
        when(adminRepository.findByTelegramUserId(TELEGRAM_USER_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminService.getRole(TELEGRAM_USER_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("createAdminFromInvite - should create admin with invitedBy field")
    void createAdminFromInvite_WhenNotExists_ShouldCreateWithInvitedBy() {
        // Given
        Long invitedBy = 987654321L;
        when(adminRepository.existsByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Admin result = adminService.createAdminFromInvite(
                TELEGRAM_USER_ID,
                "newadmin",
                "New Admin",
                AdminRole.MODERATOR,
                invitedBy
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTelegramUserId()).isEqualTo(TELEGRAM_USER_ID);
        assertThat(result.getUsername()).isEqualTo("newadmin");
        assertThat(result.getFirstName()).isEqualTo("New Admin");
        assertThat(result.getRole()).isEqualTo(AdminRole.MODERATOR);
        assertThat(result.getIsActive()).isTrue();

        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    @DisplayName("createAdminFromInvite - should throw exception when admin exists")
    void createAdminFromInvite_WhenExists_ShouldThrowException() {
        // Given
        when(adminRepository.existsByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> adminService.createAdminFromInvite(
                TELEGRAM_USER_ID, "admin", "Admin", AdminRole.ADMIN, 999L))
                .isInstanceOf(DuplicateAdminException.class)
                .hasMessage("Admin already exists");

        verify(adminRepository, never()).save(any());
    }
}
