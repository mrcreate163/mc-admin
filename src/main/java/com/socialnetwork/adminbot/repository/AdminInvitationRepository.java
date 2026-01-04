package com.socialnetwork.adminbot.repository;

import com.socialnetwork.adminbot.entity.AdminInvitation;
import com.socialnetwork.adminbot.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, UUID> {

    /**
     * Найти приглашение по токену
     * Основной метод для активации по deep link
     *
     * @param inviteToken токен из ссылки
     * @return Optional<AdminInvitation>
     */
    Optional<AdminInvitation> findByInviteToken(String inviteToken);

    /**
     * Проверить существование токена (для предотвращения дубликатов)
     *
     * @param inviteToken токен
     * @return true если токен уже существует
     */
    boolean existsByInviteToken(String inviteToken);

    /**
     * Найти все приглашения, созданные конкретным SUPER_ADMIN
     * Для статистики и управления
     *
     * @param createdBy Telegram ID SUPER_ADMIN
     * @return список приглашений
     */
    List<AdminInvitation> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    /**
     * Найти все активные (неиспользованные и не истёкшие) приглашения
     * Для админ-панели управления приглашениями (v2.0)
     *
     * @param now текущее время
     * @return список активных приглашений
     */
    @Query("SELECT ai FROM AdminInvitation ai WHERE ai.isUsed = false AND ai.expiresAt > :now")
    List<AdminInvitation> findAllActive(@Param("now") LocalDateTime now);

    /**
     * Найти все использованные приглашения
     * Для аудита и статистики
     *
     * @return список использованных приглашений
     */
    List<AdminInvitation> findByIsUsedTrueOrderByUsedAtDesc();

    /**
     * Найти приглашения по роли
     * Для статистики: сколько приглашений создано для каждой роли
     *
     * @param role роль
     * @return список приглашений
     */
    List<AdminInvitation> findByRole(AdminRole role);

    /**
     * Найти приглашение по ID активированного админа
     * Для получения истории: через какое приглашение активирован админ
     *
     * @param activatedAdminId Telegram ID активированного админа
     * @return Optional<AdminInvitation>
     */
    Optional<AdminInvitation> findByActivatedAdminId(Long activatedAdminId);

    /**
     * Подсчитать количество активных приглашений для конкретного SUPER_ADMIN
     *
     * @param createdBy Telegram ID SUPER_ADMIN
     * @param now текущее время
     * @return количество активных приглашений
     */
    @Query("SELECT COUNT(ai) FROM AdminInvitation ai WHERE ai.createdBy = :createdBy AND ai.isUsed = false AND ai.expiresAt > :now")
    long countActiveByCreatedBy(@Param("createdBy") Long createdBy, @Param("now") LocalDateTime now);

    /**
     * Удалить все истёкшие приглашения (для scheduled cleanup в v2.0)
     *
     * @param now текущее время
     * @return количество удалённых записей
     */
    @Modifying
    @Query("DELETE FROM AdminInvitation ai WHERE ai.isUsed = false AND ai.expiresAt < :now")
    int deleteExpiredInvitations(@Param("now") LocalDateTime now);
}
