package com.socialnetwork.adminbot.telegram.messages;

import com.socialnetwork.adminbot.dto.AccountDto;

/**
 * Форматтер для информации о пользователе.
 * Централизует форматирование данных пользователя для отображения в Telegram.
 * Использует безопасное экранирование HTML для защиты от XSS.
 *
 * @since 1.1
 */
public final class UserInfoFormatter {

    private UserInfoFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Форматирует полную информацию о пользователе.
     * Использует безопасное экранирование всех пользовательских данных.
     * НЕ использует String.format для пользовательских данных,
     * т.к. они могут содержать символ % который вызовет IllegalFormatException.
     *
     * @param user объект с данными пользователя
     * @return отформатированная HTML строка с информацией о пользователе
     */
    public static String formatFullUserInfo(AccountDto user) {
        String safeFirstName = safeEscape(user.getFirstName());
        String safeLastName = safeEscape(user.getLastName());
        String safeEmail = safeEscape(user.getEmail());
        String safePhone = safeEscape(user.getPhone());
        String safeCountry = safeEscape(user.getCountry());
        String safeCity = safeEscape(user.getCity());
        String safeBirthDate = user.getBirthDate() != null ? user.getBirthDate().toString() : BotMessage.STATUS_UNKNOWN.raw();
        String safeRegDate = user.getRegDate() != null ? user.getRegDate().toString() : BotMessage.STATUS_UNKNOWN.raw();
        String safeLastOnline = user.getLastOnlineTime() != null ? user.getLastOnlineTime().toString() : BotMessage.STATUS_UNKNOWN.raw();
        String safeAbout = safeEscape(user.getAbout());

        String onlineStatus = Boolean.TRUE.equals(user.getIsOnline())
                ? BotMessage.STATUS_YES.raw()
                : BotMessage.STATUS_NO.raw();
        String blockedStatus = Boolean.TRUE.equals(user.getIsBlocked())
                ? BotMessage.STATUS_YES.raw()
                : BotMessage.STATUS_NO.raw();

        return BotMessage.USER_INFO_TITLE.raw() + "\n\n" +
                BotMessage.USER_INFO_ID.format(user.getId()) + "\n" +
                BotMessage.USER_INFO_EMAIL_2.format(safeEmail) + "\n" +
                BotMessage.USER_INFO_NAME.format(safeFirstName, safeLastName) + "\n" +
                BotMessage.USER_INFO_PHONE.format(safePhone) + "\n" +
                BotMessage.USER_INFO_COUNTRY.format(safeCountry) + "\n" +
                BotMessage.USER_INFO_CITY.format(safeCity) + "\n" +
                BotMessage.USER_INFO_REGISTERED.format(safeRegDate) + "\n" +
                BotMessage.USER_INFO_BIRTH_DATE.format(safeBirthDate) + "\n" +
                BotMessage.USER_INFO_LAST_ONLINE.format(safeLastOnline) + "\n" +
                BotMessage.USER_INFO_ONLINE.format(onlineStatus) + "\n" +
                BotMessage.USER_INFO_BLOCKED.format(blockedStatus) + "\n" +
                BotMessage.USER_INFO_ABOUT.format(safeAbout);
    }

    /**
     * Форматирует краткую информацию о пользователе (для списков и поиска).
     *
     * @param user объект с данными пользователя
     * @return краткая отформатированная строка
     */
    public static String formatBriefUserInfo(AccountDto user) {
        String safeFirstName = safeEscape(user.getFirstName());
        String safeLastName = safeEscape(user.getLastName());
        String safeEmail = safeEscape(user.getEmail());
        String statusEmoji = Boolean.TRUE.equals(user.getIsBlocked())
                ? BotMessage.STATUS_BLOCKED.raw()
                : BotMessage.STATUS_ACTIVE.raw();

        return BotMessage.SEARCH_USER_CARD.format(
                safeFirstName,
                safeLastName,
                safeEmail,
                user.getId(),
                statusEmoji
        );
    }

    /**
     * Безопасно экранирует строку, возвращая "N/A" для null значений.
     *
     * @param value значение для экранирования
     * @return экранированное значение или "N/A"
     */
    private static String safeEscape(String value) {
        if (value == null || value.isBlank()) {
            return BotMessage.STATUS_UNKNOWN.raw();
        }
        return BotMessage.escapeHtml(value);
    }
}
