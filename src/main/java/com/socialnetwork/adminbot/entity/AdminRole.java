package com.socialnetwork.adminbot.entity;

/**
 * Роли администраторов с иерархией прав
 *
 * Иерархия (от высшей к низшей):
 * SUPER_ADMIN > ADMIN > SENIOR_MODERATOR > MODERATOR
 */
public enum AdminRole {
    /**
     * Супер-администратор - полный доступ
     * - Может добавлять/удалять других админов
     * - Может управлять ролями
     * - Все права нижестоящих ролей
     */
    SUPER_ADMIN(4),

    /**
     * Администратор - расширенные права
     * - Может блокировать/разблокировать пользователей
     * - Может просматривать статистику
     * - Все права модераторов
     */
    ADMIN(3),

    /**
     * Старший модератор - средние права
     * - Может блокировать пользователей (требует подтверждения)
     * - Может просматривать пользователей
     * - Все права модераторов
     */
    SENIOR_MODERATOR(2),

    /**
     * Модератор - базовые права
     * - Может просматривать информацию о пользователях
     * - Может искать пользователей
     * - Не может блокировать
     */
    MODERATOR(1);

    private final int level;

    AdminRole(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Проверить, имеет ли эта роль достаточные права для выполнения действия
     *
     * @param requiredRole минимально требуемая роль
     * @return true если эта роль >= требуемой
     */
    public boolean hasPermission(AdminRole requiredRole) {
        return this.level >= requiredRole.level;
    }

    /**
     * Проверить, может ли эта роль назначать другую роль
     * Правило: можно назначать только роли ниже своей
     *
     * @param targetRole роль для назначения
     * @return true если может назначить
     */
    public boolean canAssignRole(AdminRole targetRole) {
        return this.level > targetRole.level;
    }
}
