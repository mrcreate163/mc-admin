package com.socialnetwork.adminbot.repository;

import com.socialnetwork.adminbot.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    /**
     * Найти админа по telegram ID
     * @param telegramUserId
     * @return Optional<Admin>
     */
    Optional<Admin> findByTelegramUserId(Long telegramUserId);

    /**
     * Проверить существование определённого Telegram ID
     * @param telegramUserId
     * @return true || false
     */
    boolean existsByTelegramUserId(Long telegramUserId);

    /**
     * Проверить существование админа по username
     * @param username
     * @return true || false
     */
    boolean existsByUsername(String username);

    /**
     * Найти админа по username (case-insensitive)
     * @param username
     * @return Optional<Admin>
     */
    Optional<Admin> findByUsernameIgnoreCase(String username);

    /**
     * Получить всех активных админов
     * @return List<Admin>
     */
    List<Admin> findByIsActiveTrue();

    /**
     * Получить админов, приглашённых определённым SUPER_ADMIN
     * @param invitedBy
     * @return List<Admin>
     */
    List<Admin> findByInvitedBy(Long invitedBy);

}
