package com.socialnetwork.adminbot.domain;

/**
 * Константы для ключей данных в ConversationState.data
 * Используются для типобезопасного доступа к контексту
 */
public final class StateDataKey {

    private StateDataKey() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Поиск пользователей ==========

    /**
     * Поисковый запрос (String)
     */
    public static final String SEARCH_QUERY = "searchQuery";

    /**
     * Текущая страница результатов (Integer)
     */
    public static final String SEARCH_CURRENT_PAGE = "currentPage";

    /**
     * Общее количество страниц (Integer)
     */
    public static final String SEARCH_TOTAL_PAGES = "totalPages";

    /**
     * Общее количество найденных пользователей (Long)
     */
    public static final String SEARCH_TOTAL_RESULTS = "totalResults";

    // ========== Управление администраторами (Invite-based v2.2) ==========

    /**
     * Username целевого админа для приглашения (String, формат: @username)
     */
    public static final String INVITE_TARGET_USERNAME = "inviteTargetUsername";

    /**
     * Роль для нового админа (String: ADMIN, MODERATOR)
     */
    public static final String INVITE_ADMIN_ROLE = "inviteAdminRole";

    /**
     * Токен приглашения (String, UUID)
     */
    public static final String INVITE_TOKEN = "inviteToken";

    // ========== Legacy Admin Management (deprecated) ==========

    /**
     * @deprecated Используется для старого подхода
     * Telegram ID нового админа (Long)
     */
    @Deprecated
    public static final String ADMIN_TELEGRAM_ID = "adminTelegramId";

    /**
     * @deprecated Используется для старого подхода
     * Username нового админа (String)
     */
    @Deprecated
    public static final String ADMIN_USERNAME = "adminUsername";

    /**
     * @deprecated Используется для старого подхода
     * Имя нового админа (String)
     */
    @Deprecated
    public static final String ADMIN_FIRST_NAME = "adminFirstName";

    /**
     * @deprecated Используется для старого подхода
     * Роль нового админа (String: ADMIN, MODERATOR)
     */
    @Deprecated
    public static final String ADMIN_ROLE = "adminRole";

    // ========== Модерация (бан) ==========

    /**
     * UUID целевого пользователя для бана (String)
     */
    public static final String BAN_TARGET_USER_ID = "targetUserId";

    /**
     * Username целевого пользователя (String)
     */
    public static final String BAN_TARGET_USERNAME = "targetUsername";

    /**
     * Email целевого пользователя (String)
     */
    public static final String BAN_TARGET_EMAIL = "targetEmail";

    /**
     * Причина бана (String)
     */
    public static final String BAN_REASON = "banReason";

    /**
     * Временный бан или постоянный (Boolean)
     */
    public static final String BAN_IS_TEMPORARY = "isTemporary";

    /**
     * Длительность бана в днях (Integer, если временный)
     */
    public static final String BAN_DURATION_DAYS = "banDurationDays";
}
