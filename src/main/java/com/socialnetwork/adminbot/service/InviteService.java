package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.constant.BotConstants;
import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.entity.AdminInvitation;
import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.exception.DuplicateAdminException;
import com.socialnetwork.adminbot.exception.UnauthorizedException;
import com.socialnetwork.adminbot.repository.AdminInvitationRepository;
import com.socialnetwork.adminbot.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Сервис для управления пригласительными ссылками администраторов.
 * Обеспечивает создание, валидацию и активацию приглашений.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final AdminInvitationRepository invitationRepository;
    private final AdminRepository adminRepository;
    private final AuditLogService auditLogService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Создать новое приглашение.
     *
     * @param createdBy Telegram ID SUPER_ADMIN, создающего приглашение
     * @param role Роль для нового администратора
     * @return токен приглашения (без префикса "invite_")
     * @throws UnauthorizedException если создатель не является SUPER_ADMIN
     * @throws IllegalArgumentException если роль некорректна
     */
    @Transactional
    public String createInvitation(Long createdBy, AdminRole role) {
        log.info("action=create_invitation, createdBy={}, role={}, status=started", createdBy, role);

        // Проверяем, что создатель существует и является SUPER_ADMIN
        Admin creator = adminRepository.findByTelegramUserId(createdBy)
                .orElseThrow(() -> new UnauthorizedException("Admin not found"));

        if (creator.getRole() != AdminRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Only SUPER_ADMIN can create invitations");
        }

        // Проверяем, что роль может быть назначена
        if (!creator.getRole().canAssignRole(role)) {
            throw new IllegalArgumentException(
                    String.format("Cannot assign role %s", role.name()));
        }

        // Генерируем уникальный токен
        String inviteToken = generateUniqueToken();

        // Создаём приглашение
        AdminInvitation invitation = AdminInvitation.builder()
                .inviteToken(inviteToken)
                .role(role)
                .createdBy(createdBy)
                .expiresAt(LocalDateTime.now().plusHours(BotConstants.INVITE_EXPIRY_HOURS))
                .isUsed(false)
                .build();

        invitationRepository.save(invitation);

        log.info("action=create_invitation, invitationId={}, role={}, expiresAt={}, status=success",
                invitation.getId(), role, invitation.getExpiresAt());

        // Логируем в audit log
        auditLogService.logAction("CREATE_INVITATION", createdBy,
                java.util.Map.of(
                        "invitation_id", invitation.getId().toString(),
                        "role", role.name(),
                        "expires_at", invitation.getExpiresAt().toString()
                ));

        return inviteToken;
    }

    /**
     * Активировать приглашение (создать администратора).
     *
     * @param inviteToken токен из deep link (без префикса "invite_")
     * @param telegramUserId Telegram ID активирующегося пользователя
     * @param username Telegram username (может быть null)
     * @param firstName Имя пользователя
     * @return созданный администратор
     * @throws IllegalArgumentException если токен невалиден
     * @throws DuplicateAdminException если админ уже существует
     */
    @Transactional
    public Admin activateInvitation(
            String inviteToken,
            Long telegramUserId,
            String username,
            String firstName
    ) {
        log.info("action=activate_invitation, telegramUserId={}, status=started", telegramUserId);

        // Ищем приглашение по токену
        AdminInvitation invitation = invitationRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new IllegalArgumentException(
                        "❌ Приглашение не найдено. Токен может быть неверным."));

        // Проверяем валидность
        if (!invitation.isValid()) {
            if (invitation.getIsUsed()) {
                throw new IllegalArgumentException(
                        "❌ Это приглашение уже было использовано.");
            } else {
                throw new IllegalArgumentException(
                        "❌ Срок действия приглашения истёк.");
            }
        }

        // Проверяем, что этот Telegram ID ещё не зарегистрирован
        if (adminRepository.existsByTelegramUserId(telegramUserId)) {
            throw new DuplicateAdminException(
                    "❌ Этот Telegram аккаунт уже зарегистрирован как администратор.");
        }

        // Создаём администратора
        Admin newAdmin = Admin.builder()
                .telegramUserId(telegramUserId)
                .username(username)
                .firstName(firstName)
                .role(invitation.getRole())
                .isActive(true)
                .invitedBy(invitation.getCreatedBy())
                .build();

        Admin savedAdmin = adminRepository.save(newAdmin);

        // Помечаем приглашение как использованное
        invitation.markAsUsed(telegramUserId);
        invitationRepository.save(invitation);

        log.info("action=activate_invitation, newAdminId={}, role={}, invitedBy={}, status=success",
                savedAdmin.getTelegramUserId(), savedAdmin.getRole(), invitation.getCreatedBy());

        // Логируем активацию
        auditLogService.logAction("ACTIVATE_INVITATION", telegramUserId,
                java.util.Map.of(
                        "invitation_id", invitation.getId().toString(),
                        "role", savedAdmin.getRole().name(),
                        "invited_by", invitation.getCreatedBy().toString()
                ));

        return savedAdmin;
    }

    /**
     * Проверить валидность токена (без активации)
     *
     * @param inviteToken токен для проверки
     * @return true если токен валиден
     */
    @Transactional(readOnly = true)
    public boolean isTokenValid(String inviteToken) {
        return invitationRepository.findByInviteToken(inviteToken)
                .map(AdminInvitation::isValid)
                .orElse(false);
    }

    /**
     * Получить информацию о приглашении по токену
     *
     * @param inviteToken токен
     * @return приглашение
     * @throws IllegalArgumentException если токен не найден
     */
    @Transactional(readOnly = true)
    public AdminInvitation getInvitationByToken(String inviteToken) {
        return invitationRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new IllegalArgumentException("Приглашение не найдено"));
    }

    /**
     * Получить все активные приглашения
     *
     * @return список активных приглашений
     */
    @Transactional(readOnly = true)
    public List<AdminInvitation> getAllActiveInvitations() {
        return invitationRepository.findAllActive(LocalDateTime.now());
    }

    /**
     * Получить приглашения, созданные конкретным SUPER_ADMIN
     *
     * @param createdBy Telegram ID SUPER_ADMIN
     * @return список приглашений
     */
    @Transactional(readOnly = true)
    public List<AdminInvitation> getInvitationsByCreator(Long createdBy) {
        return invitationRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
    }

    /**
     * Генерировать уникальный токен приглашения.
     * Использует SecureRandom для криптографической стойкости.
     *
     * @return уникальный токен (Base64 URL-safe)
     */
    private String generateUniqueToken() {
        String token;
        int attempts = 0;

        do {
            byte[] randomBytes = new byte[BotConstants.INVITE_TOKEN_LENGTH];
            SECURE_RANDOM.nextBytes(randomBytes);

            // Используем Base64 URL-safe encoding (без +, /, =)
            // 32 байта → 43 символа в Base64 (без padding)
            token = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(randomBytes);

            attempts++;

            if (attempts >= BotConstants.MAX_TOKEN_GENERATION_ATTEMPTS) {
                throw new RuntimeException("Failed to generate unique invite token after " +
                        BotConstants.MAX_TOKEN_GENERATION_ATTEMPTS + " attempts");
            }

        } while (invitationRepository.existsByInviteToken(token));

        log.debug("action=generate_token, tokenLength={}, attempts={}", token.length(), attempts);
        return token;
    }
}
