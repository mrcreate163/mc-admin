package com.socialnetwork.adminbot.domain;

/**
 * Перечисление возможных состояний диалога с ботом
 */
public enum BotState {

    /**
     * Начальное состояние - пользователь не в диалоге
     */
    IDLE,

    /**
     * Ожидание ввода поискового запроса
     */
    AWAITING_SEARCH_QUERY,

    /**
     * Отображение результатов поиска (с пагинацией)
     */
    SHOWING_SEARCH_RESULTS,

    // ========== Invite-based Admin Management (v2.2) ==========

    /**
     * Ожидание ввода username для создания приглашения
     */
    AWAITING_ADMIN_USERNAME,

    /**
     * Ожидание выбора роли для нового админа
     */
    AWAITING_ADMIN_ROLE,

    /**
     * Подтверждение создания приглашения
     */
    CONFIRMING_ADMIN_INVITE_CREATION,

    /**
     * Подтверждение активации приглашения (со стороны кандидата)
     */
    CONFIRMING_INVITE_ACCEPTANCE,

    // ========== Legacy (deprecated, for backward compatibility) ==========

    /**
     * @deprecated Используется старый подход (добавление по Telegram ID)
     * Оставлен для обратной совместимости
     */
    @Deprecated
    AWAITING_ADMIN_TELEGRAM_ID,

    /**
     * @deprecated Объединён с CONFIRMING_ADMIN_INVITE_CREATION
     */
    @Deprecated
    CONFIRMING_ADMIN_CREATION,

    // ========== Moderation ==========

    /**
     * Ожидание причины бана пользователя
     */
    AWAITING_BAN_REASON,

    /**
     * Подтверждение бана пользователя
     */
    CONFIRMING_BAN
}
