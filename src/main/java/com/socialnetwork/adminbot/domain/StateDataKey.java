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

    // ========== Управление администраторами ==========

    /**
     * Telegram ID нового админа (Long)
     */
    public static final String ADMIN_TELEGRAM_ID = "adminTelegramId";

    /**
     * Username нового админа (String)
     */
    public static final String ADMIN_USERNAME = "adminUsername";

    /**
     * Имя нового админа (String)
     */
    public static final String ADMIN_FIRST_NAME = "adminFirstName";

    /**
     * Роль нового админа (String: ADMIN, MODERATOR)
     */
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
