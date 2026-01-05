package com.socialnetwork.adminbot.constant;

/**
 * Централизованное хранилище констант бота.
 * Объединяет все магические числа и строки из проекта
 * для улучшения поддерживаемости и читаемости кода.
 *
 * @since 1.1
 */
public final class BotConstants {

    private BotConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Telegram API ====================

    /**
     * Максимальная длина текста сообщения в Telegram.
     */
    public static final int TELEGRAM_MESSAGE_MAX_LENGTH = 4096;

    /**
     * Режим парсинга HTML для Telegram сообщений.
     */
    public static final String PARSE_MODE_HTML = "HTML";

    // ==================== Приглашения ====================

    /**
     * Длина токена приглашения в байтах (64 символа Base64).
     */
    public static final int INVITE_TOKEN_LENGTH = 32;

    /**
     * Срок действия приглашения в часах.
     */
    public static final int INVITE_EXPIRY_HOURS = 24;

    /**
     * Максимальное количество попыток генерации уникального токена.
     */
    public static final int MAX_TOKEN_GENERATION_ATTEMPTS = 10;

    // ==================== Поиск и пагинация ====================

    /**
     * Минимальная длина поискового запроса.
     */
    public static final int MIN_SEARCH_QUERY_LENGTH = 3;

    /**
     * Паттерн для валидации email поискового запроса.
     */
    public static final String EMAIL_SEARCH_PATTERN = "^[a-zA-Z0-9@._-]+$";

    // ==================== Валидация ====================

    /**
     * Максимальная длина причины бана.
     */
    public static final int MAX_BAN_REASON_LENGTH = 500;

    /**
     * Минимальная длина причины бана.
     */
    public static final int MIN_BAN_REASON_LENGTH = 1;

    // ==================== Причины бана ====================

    /**
     * Причины бана на русском языке.
     */
    public static final class BanReasons {
        public static final String SPAM = "Спам";
        public static final String HARASSMENT = "Harassment";
        public static final String BOT_FAKE = "Bot/Fake аккаунт";
        public static final String COMMUNITY_VIOLATION = "Нарушение правил сообщества";

        private BanReasons() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    // ==================== Callback Data Prefixes ====================

    /**
     * Префиксы для callback data в Telegram.
     */
    public static final class CallbackPrefix {
        public static final String BLOCK = "block:";
        public static final String UNBLOCK = "unblock:";
        public static final String BAN_REASON = "ban_reason:";
        public static final String BAN_CONFIRM = "ban_confirm";
        public static final String BAN_CANCEL = "ban_cancel";
        public static final String SEARCH_PAGE = "search_page:";
        public static final String SEARCH_VIEW = "search_view:";
        public static final String SEARCH_BAN = "search_ban:";
        public static final String SEARCH_UNBAN = "search_unban:";
        public static final String SEARCH_NEW = "search_new";
        public static final String SEARCH_CANCEL = "search_cancel";
        public static final String ADD_ADMIN_ROLE = "add_admin:role:";
        public static final String ADD_ADMIN_CONFIRM = "add_admin:confirm";
        public static final String ADD_ADMIN_CANCEL = "add_admin:cancel";
        public static final String NOOP = "noop";

        private CallbackPrefix() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    // ==================== Deep Link ====================

    /**
     * Префикс для deep link приглашений.
     */
    public static final String INVITE_DEEP_LINK_PREFIX = "invite_";

    /**
     * Fallback имя администратора если не указано.
     */
    public static final String DEFAULT_ADMIN_NAME = "Admin";

    /**
     * Fallback имя для приветствия если не указано.
     */
    public static final String DEFAULT_GREETING_NAME = "Администратор";
}
