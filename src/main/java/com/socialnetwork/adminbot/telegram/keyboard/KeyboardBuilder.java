package com.socialnetwork.adminbot.telegram.keyboard;


import com.socialnetwork.adminbot.dto.AccountDto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

/**
 * Утилита для создания inline клавиатур бота.
 * Все тексты кнопок хранятся в BotMessage для централизованного управления.
 */
public class KeyboardBuilder {

    /**
     * Клавиатура выбора роли для нового админа
     * Структура:
     * [ 🔵 MODERATOR ]
     * [ 🟢 SENIOR_MODERATOR ]
     * [ 🟠 ADMIN ]
     * [ 🔴 SUPER_ADMIN ]
     * [ ❌ Отмена ]
     */
    public static InlineKeyboardMarkup buildRoleSelectionKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Каждая роль на отдельной кнопке
        rows.add(List.of(
                createButton("🔵 MODERATOR", "add_admin:role:MODERATOR")
        ));

        rows.add(List.of(
                createButton("🟢 SENIOR_MODERATOR", "add_admin:role:SENIOR_MODERATOR")
        ));

        rows.add(List.of(
                createButton("🟠 ADMIN", "add_admin:role:ADMIN")
        ));

        rows.add(List.of(
                createButton("🔴 SUPER_ADMIN", "add_admin:role:SUPER_ADMIN")
        ));

        // Кнопка отмены
        rows.add(List.of(
                createButton("❌ Отмена", "add_admin:cancel")
        ));

        return createKeyboard(rows);
    }


    /**
     * Создаёт клавиатуру с действиями над пользователем.
     * <p>
     * Структура:
     * [ 📊 Статистика ] [ 🚫 Заблокировать / ✅ Разблокировать ]
     * [          « Вернуться в меню          ]
     *
     * @param userId    ID пользователя для действий
     * @param isBlocked текущий статус блокировки пользователя
     * @return готовая inline клавиатура
     */
    public static InlineKeyboardMarkup buildUserActionsKeyboard(UUID userId, boolean isBlocked) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первый ряд: Статистика + Блокировка/Разблокировка
        List<InlineKeyboardButton> row1 = new ArrayList<>();

        // Кнопка "Статистика"
        row1.add(createButton(
                "📊 Статистика",
                "stats:" + userId
        ));

        // Кнопка "Заблокировать" или "Разблокировать"
        row1.add(createButton(
                isBlocked ? "✅ Разблокировать" : "🚫 Заблокировать",
                (isBlocked ? "unblock:" : "block:") + userId
        ));

        rows.add(row1);

        // Второй ряд: Возврат в меню
        List<InlineKeyboardButton> row2 = List.of(
                createButton("« Вернуться в меню", "main_menu")
        );

        rows.add(row2);

        return createKeyboard(rows);
    }

    /**
     * Клавиатура с причинами бана
     * Структура:
     * [ 🚫 Спам ]      [😡 Harassment]
     * [🤖 Bot/Fake] [️ Нарушение правил]
     * [❌ Отмена]
     */
    public static InlineKeyboardMarkup buildBanReasonsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Первая строка
        keyboard.add(Arrays.asList(
                createButton("🚫 Спам", "ban_reason:spam"),
                createButton("😡 Harassment", "ban_reason:harassment")
        ));

        // Вторая строка
        keyboard.add(Arrays.asList(
                createButton("🤖 Bot/Fake", "ban_reason:bot"),
                createButton("⚠️ Нарушение правил", "ban_reason:violation")
        ));

        // Третья строка - отмена
        keyboard.add(Collections.singletonList(
                createButton("❌ Отмена", "ban_cancel")
        ));

        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Клавиатура подтверждения действия
     * Структура:
     * [ ✅ Подтвердить ] [ ❌ Отмена ]
     *
     * @param actionPrefix префикс для callback data (например, "ban", "delete")
     */
    public static InlineKeyboardMarkup buildConfirmationKeyboard(String actionPrefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(Arrays.asList(
                createButton("✅ Подтвердить", actionPrefix + "_confirm"),
                createButton("❌ Отмена", actionPrefix + "_cancel")
        ));

        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Создаёт главное меню бота.
     * <p>
     * Структура:
     * [    📊 Просмотр статистики    ]
     * [    👥 Список пользователей   ] - (заглушка для v2.0)
     *
     * @return готовая inline клавиатура
     */
    public static InlineKeyboardMarkup buildMainMenuKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка "Просмотр статистики"
        rows.add(List.of(
                createButton("📊 Просмотр статистики", "show_stats")
        ));

        //TODO fix: Добавить функционал списка пользователей в v2.0
        // Кнопка "Список пользователей" (пока не реализовано)

        // Раскомментируй когда добавишь функционал в v2.0
        // rows.add(List.of(
        //     createButton("👥 Список пользователей", "list_users")
        // ));

        return createKeyboard(rows);
    }

    /**
     * Создаёт клавиатуру подтверждения действия.
     * <p>
     * Структура:
     * [  ✅ Подтвердить  ] [  ❌ Отмена  ]
     *
     * @param confirmCallback callback data для подтверждения
     * @param cancelCallback  callback data для отмены
     * @return готовая inline клавиатура
     */
    public static InlineKeyboardMarkup buildConfirmationKeyboard(String confirmCallback, String cancelCallback) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("✅ Подтвердить", confirmCallback));
        row.add(createButton("❌ Отмена", cancelCallback));

        rows.add(row);

        return createKeyboard(rows);
    }

    /**
     * Создаёт клавиатуру для пагинации списка.
     * <p>
     * Структура:
     * [ ◀️ Назад ] [ Страница X/Y ] [ Вперёд ▶️ ]
     * [        « Вернуться в меню        ]
     *
     * @param currentPage    текущая страница (0-based)
     * @param totalPages     общее количество страниц
     * @param callbackPrefix префикс для callback data (например, "users_page:")
     * @return готовая inline клавиатура
     */
    public static InlineKeyboardMarkup buildPaginationKeyboard(
            int currentPage,
            int totalPages,
            String callbackPrefix
    ) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        // Кнопка "Назад" (если не первая страница)
        if (currentPage > 0) {
            row.add(createButton("◀️ Назад", callbackPrefix + (currentPage - 1)));
        } else {
            // Placeholder чтобы кнопки были ровно
            row.add(createButton(" ", "noop"));
        }

        // Индикатор страницы
        row.add(createButton(
                String.format("Страница %d/%d", currentPage + 1, totalPages),
                "noop"
        ));

        // Кнопка "Вперёд" (если не последняя страница)
        if (currentPage < totalPages - 1) {
            row.add(createButton("Вперёд ▶️", callbackPrefix + (currentPage + 1)));
        } else {
            row.add(createButton(" ", "noop"));
        }

        rows.add(row);

        // Кнопка возврата в меню
        rows.add(List.of(createButton("« Вернуться в меню", "main_menu")));

        return createKeyboard(rows);
    }

    // ==================== Вспомогательные методы ====================

    /**
     * Создаёт кнопку с текстом и callback data.
     *
     * @param text         текст на кнопке
     * @param callbackData данные для callback query
     * @return готовая кнопка
     */
    private static InlineKeyboardButton createButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    /**
     * Создаёт кнопку с URL ссылкой.
     *
     * @param text текст на кнопке
     * @param url  ссылка для открытия
     * @return готовая кнопка
     */
    private static InlineKeyboardButton createUrlButton(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
    }

    /**
     * Максимальное количество пользователей для отображения в клавиатуре
     */
    private static final int MAX_USERS_PER_PAGE = 5;
    
    /**
     * Клавиатура для результатов поиска с действиями и пагинацией
     *
     * Структура:
     * [ 👁 Просмотр | 🚫 Бан ] для каждого пользователя
     * ...
     * [ ◀️ Назад ] [ Страница X/Y ] [ Вперёд ▶️ ]
     * [ 🔍 Новый поиск ] [ ❌ Отмена ]
     *
     * @param users       список пользователей на текущей странице (ограничен до MAX_USERS_PER_PAGE)
     * @param currentPage текущая страница (0-based)
     * @param totalPages  общее количество страниц
     * @return готовая inline клавиатура
     */
    public static InlineKeyboardMarkup buildSearchResultsKeyboard(
            List<AccountDto> users,
            int currentPage,
            int totalPages
    ) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Ограничиваем количество пользователей для защиты от превышения лимитов Telegram
        int usersToProcess = Math.min(users.size(), MAX_USERS_PER_PAGE);
        
        // Кнопки действий для каждого пользователя
        for (int i = 0; i < usersToProcess; i++) {
            AccountDto user = users.get(i);
            int userNumber = currentPage * MAX_USERS_PER_PAGE + i + 1;

            List<InlineKeyboardButton> row = new ArrayList<>();

            // Кнопка "Просмотр"
            row.add(createButton(
                    String.format("%d. 👁 Просмотр", userNumber),
                    "search_view:" + user.getId()
            ));

            // Кнопка "Бан" или "Разбан"
            if (user.getIsBlocked()) {
                row.add(createButton(
                        "✅ Разбан",
                        "search_unban:" + user.getId()
                ));
            } else {
                row.add(createButton(
                        "🚫 Бан",
                        "search_ban:" + user.getId()
                ));
            }

            keyboard.add(row);
        }

        // Разделитель
        keyboard.add(List.of(createButton("─────────", "noop")));

        // Кнопки пагинации (если больше одной страницы)
        if (totalPages > 1) {
            List<InlineKeyboardButton> paginationRow = new ArrayList<>();

            // Кнопка "Назад" (если не первая страница)
            if (currentPage > 0) {
                paginationRow.add(createButton(
                        "◀️ Назад",
                        "search_page:" + (currentPage - 1)
                ));
            }

            // Индикатор текущей страницы
            paginationRow.add(createButton(
                    String.format("📄 %d/%d", currentPage + 1, totalPages),
                    "noop"
            ));

            // Кнопка "Вперёд" (если не последняя страница)
            if (currentPage < totalPages - 1) {
                paginationRow.add(createButton(
                        "Вперёд ▶️",
                        "search_page:" + (currentPage + 1)
                ));
            }

            keyboard.add(paginationRow);
        }

        // Кнопки управления
        keyboard.add(Arrays.asList(
                createButton("🔍 Новый поиск", "search_new"),
                createButton("❌ Закрыть", "search_cancel")
        ));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Создаёт InlineKeyboardMarkup из списка рядов кнопок.
     *
     * @param rows список рядов кнопок
     * @return готовая клавиатура
     */
    private static InlineKeyboardMarkup createKeyboard(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

}
