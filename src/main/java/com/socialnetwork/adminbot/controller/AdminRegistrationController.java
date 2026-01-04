package com.socialnetwork.adminbot.controller;

import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.entity.AdminInvitation;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.InviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST контроллер для регистрации администраторов через пригласительные ссылки.
 *
 * Endpoints:
 * - POST /api/v1/admin-bot/register - активация администратора по токену
 * - GET /api/v1/admin-bot/invite/validate - проверка валидности токена
 * - GET /api/v1/admin-bot/health - health check
 *
 * Этот контроллер может использоваться как через REST API,
 * так и косвенно через Telegram deep links (обработка в StartCommandHandler).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin-bot")
@RequiredArgsConstructor
public class AdminRegistrationController {

    private final InviteService inviteService;
    private final AuditLogService auditLogService;

    /**
     * Активация администратора по пригласительному токену
     *
     * POST /api/v1/admin-bot/register
     * Body: {
     *   "token": "abc123xyz456",
     *   "telegram_id": 123456789,
     *   "username": "john_doe",  // optional
     *   "first_name": "John"
     * }
     *
     * @param request тело запроса с данными активации
     * @return информация об активированном администраторе
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerAdmin(@RequestBody RegistrationRequest request) {
        log.info("Registration attempt: token={}, telegramId={}",
                request.getToken(), request.getTelegramId());

        try {
            // Валидация входных данных
            if (request.getToken() == null || request.getToken().isBlank()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "error", "Токен приглашения не может быть пустым"
                        ));
            }

            if (request.getTelegramId() == null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "error", "Telegram ID не может быть пустым"
                        ));
            }

            if (request.getFirstName() == null || request.getFirstName().isBlank()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "error", "Имя не может быть пустым"
                        ));
            }

            // Активируем администратора
            Admin activatedAdmin = inviteService.activateInvitation(
                    request.getToken(),
                    request.getTelegramId(),
                    request.getUsername(),
                    request.getFirstName()
            );

            log.info("Successfully activated admin: id={}, role={}",
                    activatedAdmin.getTelegramUserId(), activatedAdmin.getRole());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Администратор успешно активирован",
                    "admin", Map.of(
                            "telegram_id", activatedAdmin.getTelegramUserId(),
                            "username", activatedAdmin.getUsername() != null
                                    ? activatedAdmin.getUsername() : "",
                            "first_name", activatedAdmin.getFirstName(),
                            "role", activatedAdmin.getRole().name(),
                            "created_at", activatedAdmin.getCreatedAt().toString(),
                            "invited_by", activatedAdmin.getInvitedBy()
                    )
            ));

        } catch (IllegalArgumentException e) {
            // Токен невалиден, истёк или уже использован
            log.warn("Invalid registration attempt: {}", e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Error during admin registration: {}", e.getMessage(), e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Внутренняя ошибка сервера. Попробуйте позже."
                    ));
        }
    }

    /**
     * Проверка валидности пригласительного токена (без активации)
     *
     * GET /api/v1/admin-bot/invite/validate?token=abc123xyz
     *
     * @param token токен для проверки
     * @return информация о валидности токена
     */
    @GetMapping("/invite/validate")
    public ResponseEntity<?> validateInviteToken(@RequestParam("token") String token) {
        log.debug("Validating invite token: {}", token);

        try {
            if (token == null || token.isBlank()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "valid", false,
                                "error", "Токен не может быть пустым"
                        ));
            }

            boolean isValid = inviteService.isTokenValid(token);

            if (isValid) {
                // Получаем информацию о приглашении
                AdminInvitation invitation = inviteService.getInvitationByToken(token);

                return ResponseEntity.ok(Map.of(
                        "valid", true,
                        "role", invitation.getRole().name(),
                        "expires_at", invitation.getExpiresAt().toString(),
                        "hours_until_expiry", invitation.getHoursUntilExpiry(),
                        "created_by", invitation.getCreatedBy()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "valid", false,
                        "message", "Токен недействителен, истёк или уже использован"
                ));
            }

        } catch (IllegalArgumentException e) {
            log.warn("Token validation failed: {}", e.getMessage());

            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "message", "Токен не найден"
            ));

        } catch (Exception e) {
            log.error("Error validating invite token: {}", e.getMessage(), e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "valid", false,
                            "error", "Ошибка проверки токена"
                    ));
        }
    }

    /**
     * Health check endpoint
     *
     * GET /api/v1/admin-bot/health
     *
     * @return статус сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "admin-bot-registration",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * DTO для запроса регистрации
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    public static class RegistrationRequest {
        private String token;
        private Long telegramId;
        private String username;
        private String firstName;
    }
}
