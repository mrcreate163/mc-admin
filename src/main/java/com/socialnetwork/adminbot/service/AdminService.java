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

/**
 * Сервис для управления администраторами бота.
 * Предоставляет методы для поиска, создания и проверки прав администраторов.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdminRepository adminRepository;

    /**
     * Найти администратора по Telegram ID.
     *
     * @param telegramUserId Telegram ID пользователя
     * @return найденный администратор
     * @throws UnauthorizedException если администратор не найден
     */
    public Admin findByTelegramId(Long telegramUserId) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> {
                    log.warn("Admin not found: telegramUserId={}", telegramUserId);
                    return new UnauthorizedException("Admin not found");
                });
    }

    /**
     * Проверить, является ли пользователь администратором.
     *
     * @param telegramUserId Telegram ID пользователя
     * @return true если пользователь является администратором
     */
    public boolean isAdmin(Long telegramUserId) {
        return adminRepository.existsByTelegramUserId(telegramUserId);
    }

    /**
     * Проверить, имеет ли администратор определенную роль.
     *
     * @param telegramUserId Telegram ID администратора
     * @param role роль для проверки
     * @return true если администратор имеет указанную роль
     */
    public boolean hasRole(Long telegramUserId, AdminRole role) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .map(admin -> admin.getRole() == role)
                .orElse(false);
    }

    /**
     * Проверить, имеет ли администратор достаточно прав для выполнения действия.
     * Использует иерархию ролей для проверки уровня доступа.
     *
     * @param telegramUserId Telegram ID администратора
     * @param requiredRole минимально требуемая роль
     * @return true если права достаточны
     */
    public boolean hasPermission(Long telegramUserId, AdminRole requiredRole) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .map(admin -> admin.getRole().hasPermission(requiredRole))
                .orElse(false);
    }

    /**
     * Получить роль администратора.
     *
     * @param telegramUserId Telegram ID администратора
     * @return роль администратора
     * @throws UnauthorizedException если администратор не найден
     */
    public AdminRole getRole(Long telegramUserId) {
        return findByTelegramId(telegramUserId).getRole();
    }

    /**
     * Создать нового администратора.
     *
     * @param dto данные для создания администратора
     * @return созданный администратор
     * @throws DuplicateAdminException если администратор с таким Telegram ID уже существует
     */
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
        log.info("action=create_admin, telegramUserId={}, role={}, status=success",
                saved.getTelegramUserId(), saved.getRole());
        return saved;
    }

    /**
     * Создать администратора из приглашения.
     *
     * @param telegramUserId Telegram ID нового админа
     * @param username Telegram username
     * @param firstName Имя пользователя
     * @param role Роль
     * @param invitedBy Telegram ID пригласившего SUPER_ADMIN
     * @return созданный Admin
     * @throws DuplicateAdminException если администратор с таким Telegram ID уже существует
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
        log.info("action=create_admin_from_invite, telegramUserId={}, role={}, invitedBy={}, status=success",
                saved.getTelegramUserId(), saved.getRole(), invitedBy);
        return saved;
    }

    /**
     * Получить всех активных администраторов.
     *
     * @return список активных администраторов
     */
    public List<Admin> getAllActiveAdmins() {
        return adminRepository.findByIsActiveTrue();
    }
}
