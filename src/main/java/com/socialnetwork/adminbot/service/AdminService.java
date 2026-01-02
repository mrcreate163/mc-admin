package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.dto.AdminDto;
import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.exception.DuplicateAdminException;
import com.socialnetwork.adminbot.exception.UnauthorizedException;
import com.socialnetwork.adminbot.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdminRepository adminRepository;

    public Admin findByTelegramId(Long telegramUserId) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> {
                    log.warn("Admin not found: telegramUserId={}", telegramUserId);
                    return new UnauthorizedException("Admin not found");
                });
    }

    public boolean isAdmin(Long telegramUserId) {
        return adminRepository.existsByTelegramUserId(telegramUserId);
    }



    /**
     * Проверить, имеет ли администратор определенную роль
     */
    public boolean hasRole(Long telegramUserId, AdminRole role) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .map(admin -> admin.getRole() == role)
                .orElse(false);
    }

    /**
     * Проверить, имеет ли администратор достаточно прав для выполнения действия
     *
     * @param telegramUserId ID администратора
     * @param requiredRole минимально требуемая роль
     * @return true если права достаточны
     */
    public boolean hasPermission(Long telegramUserId, AdminRole requiredRole) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .map(admin -> admin.getRole().hasPermission(requiredRole))
                .orElse(false);
    }

    /**
     * Получить роль администратора
     */
    public AdminRole getRole(Long telegramUserId) {
        return findByTelegramId(telegramUserId).getRole();
    }

    @Transactional
    public Admin createAdmin(AdminDto dto) {
        if (adminRepository.existsByTelegramUserId(dto.getTelegramUserId())) {
            throw new DuplicateAdminException("Admin already exists");
        }

        Admin admin = Admin.builder()
                .telegramUserId(dto.getTelegramUserId())
                .username(dto.getUsername())
                .firstName(dto.getFirstName())
                .role(dto.getRole())
                .isActive(true)
                .build();

        Admin saved = adminRepository.save(admin);
        log.info("Created new admin: telegramUserId={}, role={}",
                saved.getTelegramUserId(), saved.getRole());
        return saved;
    }

    /**
     * Создать администратора из приглашения
     *
     * @param telegramUserId Telegram ID нового админа
     * @param username Telegram username
     * @param firstName Имя пользователя
     * @param role Роль
     * @param invitedBy Telegram ID пригласившего SUPER_ADMIN
     * @return созданный Admin
     */
    @Transactional
    public Admin createAdminFromInvite(Long telegramUserId, String username,
                                       String firstName, AdminRole role, Long invitedBy) {
        if (adminRepository.existsByTelegramUserId(telegramUserId)) {
            throw new DuplicateAdminException("Admin already exists");
        }

        Admin admin = Admin.builder()
                .telegramUserId(telegramUserId)
                .username(username)
                .firstName(firstName)
                .role(role)
                .isActive(true)
                .build();

        Admin saved = adminRepository.save(admin);
        log.info("Created new admin from invite: telegramUserId={}, role={}, invitedBy={}",
                saved.getTelegramUserId(), saved.getRole(), invitedBy);
        return saved;
    }

    public List<Admin> getAllActiveAdmins() {
        return adminRepository.findByIsActiveTrue();
    }
}
