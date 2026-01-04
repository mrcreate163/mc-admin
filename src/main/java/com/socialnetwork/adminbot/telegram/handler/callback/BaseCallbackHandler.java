package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

/**
 * Базовый абстрактный класс для обработчиков callback-запросов.
 * Содержит общие утилитные методы.
 */
public abstract class BaseCallbackHandler implements CallbackHandler {

    /**
     * Создаёт сообщение об ошибке.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения
     * @param error     текст ошибки
     * @return EditMessageText с ошибкой
     */
    protected EditMessageText createErrorMessage(Long chatId, Integer messageId, String error) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.ERROR_GENERIC.format(error));
        message.setParseMode("HTML");
        return message;
    }

    /**
     * Создаёт простое сообщение с текстом.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения
     * @param text      текст сообщения
     * @return EditMessageText с текстом
     */
    protected EditMessageText createMessage(Long chatId, Integer messageId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }

    /**
     * Экранирование HTML для Telegram.
     * Заменяет специальные HTML символы на их entity-коды.
     *
     * @param text текст для экранирования
     * @return экранированный текст
     */
    protected String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Форматирование детальной информации о пользователе.
     * ВАЖНО: НЕ используем BotMessage.format() для пользовательских данных,
     * т.к. они могут содержать символ % который вызовет IllegalFormatException.
     *
     * @param user объект с данными пользователя
     * @return отформатированная строка с информацией о пользователе
     */
    protected String formatUserDetails(AccountDto user) {
        // Безопасное экранирование всех пользовательских данных
        String safeFirstName = escapeHtml(user.getFirstName() != null ? user.getFirstName() : "N/A");
        String safeLastName = escapeHtml(user.getLastName() != null ? user.getLastName() : "N/A");
        String safeEmail = escapeHtml(user.getEmail() != null ? user.getEmail() : "N/A");
        String safePhone = escapeHtml(user.getPhone() != null ? user.getPhone() : "N/A");
        String safeCountry = escapeHtml(user.getCountry() != null ? user.getCountry() : "N/A");
        String safeCity = escapeHtml(user.getCity() != null ? user.getCity() : "N/A");
        String safeBirthDate = user.getBirthDate() != null ? user.getBirthDate().toString() : "N/A";
        String safeRegDate = user.getRegDate() != null ? user.getRegDate().toString() : "N/A";
        String safeLastOnline = user.getLastOnlineTime() != null ? user.getLastOnlineTime().toString() : "N/A";
        String safeAbout = escapeHtml(user.getAbout() != null ? user.getAbout() : "N/A");

        String onlineStatus = Boolean.TRUE.equals(user.getIsOnline()) ? "✅ Да" : "❌ Нет";
        String blockedStatus = Boolean.TRUE.equals(user.getIsBlocked()) ? "🔴 Да" : "🟢 Нет";

        // Формируем текст напрямую без String.format для пользовательских данных
        return "👤 <b>Информация о пользователе</b>\n\n" +
                "🆔 ID: <code>" + user.getId() + "</code>\n" +
                "📧 Email: <code>" + safeEmail + "</code>\n" +
                "👤 Имя: " + safeFirstName + " " + safeLastName + "\n" +
                "📱 Телефон: " + safePhone + "\n" +
                "🌍 Страна: " + safeCountry + "\n" +
                "🏙️ Город: " + safeCity + "\n" +
                "📅 Дата регистрации: " + safeRegDate + "\n" +
                "🎂 Дата рождения: " + safeBirthDate + "\n" +
                "⏰ Последняя активность: " + safeLastOnline + "\n" +
                "🟢 Онлайн: " + onlineStatus + "\n" +
                "🔒 Заблокирован: " + blockedStatus + "\n" +
                "📝 О себе: " + safeAbout;
    }
}
