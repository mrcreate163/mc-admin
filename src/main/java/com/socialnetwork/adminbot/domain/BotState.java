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

    /**
     * Ожидание Telegram ID для добавления нового админа
     */
    AWAITING_ADMIN_TELEGRAM_ID,

    /**
     * Ожидание выбора роли для нового админа
     */
    AWAITING_ADMIN_ROLE,

    /**
     * Подтверждение создания нового админа
     */
    CONFIRMING_ADMIN_CREATION,

    /**
     * Ожидание причины бана пользователя
     */
    AWAITING_BAN_REASON,

    /**
     * Подтверждение бана пользователя
     */
    CONFIRMING_BAN
}
