package com.socialnetwork.adminbot.service;

import static com.socialnetwork.adminbot.constant.AuditActionType.*;
import com.socialnetwork.adminbot.client.AccountClient;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AccountClient accountClient;
    private final AuditLogService auditLogService;

    /**
     * Получить пользователя по ID
     *
     * @param userId UUID пользователя
     * @return AccountDto с информацией о пользователе
     */
    public AccountDto getUserById(UUID userId) {
        log.debug("Getting user by ID: {}", userId);

        return accountClient.getAccountById(userId);
    }

    /**
     * Заблокировать пользователя
     *
     * @param userId UUID пользователя для блокировки
     * @param adminTelegramId Telegram ID администратора
     * @param reason причина блокировки (опционально)
     */
    public void blockUser(UUID userId, Long adminTelegramId, String reason) {
        log.info("Blocking user {} by admin {}, reason: {}", userId, adminTelegramId, reason);

        // Блокируем пользователя через AccountClient
        accountClient.blockAccount(userId);

        // Логируем действие в audit log
        auditLogService.logAction(
                BLOCK_USER,
                adminTelegramId,
                userId,
                reason
        );

        log.info("User {} successfully blocked by admin {}", userId, adminTelegramId);
    }

    /**
     * Разблокировать пользователя
     *
     * @param userId UUID пользователя для разблокировки
     * @param adminTelegramId Telegram ID администратора
     */
    public void unblockUser(UUID userId, Long adminTelegramId) {
        log.info("Unblocking user {} by admin {}", userId, adminTelegramId);

        // Разблокируем пользователя через AccountClient
        accountClient.unblockAccount(userId);

        // Логируем действие в audit log (без причины)
        auditLogService.logAction(
                UNBLOCK_USER,
                adminTelegramId,
                userId,
                null
        );

        log.info("User {} successfully unblocked by admin {}", userId, adminTelegramId);
    }

    /**
     * Получить страницу пользователей с пагинацией
     *
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @return PageAccountDto с результатами
     */
    public PageAccountDto getUsersPage(int page, int size) {
        log.debug("Fetching users page: page={}, size={}", page, size);
        return accountClient.getAccountsPage(page, size, "regDate,desc");
    }

    /**
     * Поиск пользователей по email с пагинацией
     *
     * @param email поисковый запрос (email или часть email)
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @return PageAccountDto с результатами поиска
     */
    public PageAccountDto searchUsersByEmail(String email, int page, int size) {
        log.debug("Searching users by email: email='{}', page={}, size={}", email, page, size);
        return accountClient.searchAccountsByEmail(email, page, size);
    }
}
