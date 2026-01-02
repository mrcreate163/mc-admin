package com.socialnetwork.adminbot.constant;

/**
 * Константы для пагинации результатов поиска
 */
public final class PaginationConstants {

    /**
     * Размер страницы для результатов поиска.
     * Оптимально для мобильного интерфейса Telegram.
     */
    public static final int SEARCH_PAGE_SIZE = 5;

    private PaginationConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
