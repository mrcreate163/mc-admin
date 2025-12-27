package com.socialnetwork.adminbot.telegram.keyboard;


import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Утилита для создания inline клавиатур бота.
 * Все тексты кнопок хранятся в BotMessage для централизованного управления.
 */
public class KeyboardBuilder {

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
