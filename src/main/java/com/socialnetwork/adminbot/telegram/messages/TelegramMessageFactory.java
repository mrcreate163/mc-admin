package com.socialnetwork.adminbot.telegram.messages;

import com.socialnetwork.adminbot.constant.BotConstants;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Фабрика для создания Telegram сообщений.
 * Централизует создание объектов SendMessage и EditMessageText
 * для устранения дублирования кода в обработчиках.
 *
 * @since 1.1
 */
public final class TelegramMessageFactory {



    private TelegramMessageFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Создаёт SendMessage с HTML форматированием.
     *
     * @param chatId ID чата
     * @param text   текст сообщения
     * @return настроенный объект SendMessage
     */
    public static SendMessage createHtmlMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode(BotConstants.PARSE_MODE_HTML);
        return message;
    }

    /**
     * Создаёт EditMessageText с HTML форматированием.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения для редактирования
     * @param text      текст сообщения
     * @return настроенный объект EditMessageText
     */
    public static EditMessageText createHtmlEditMessage(Long chatId, Integer messageId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(text);
        message.setParseMode(BotConstants.PARSE_MODE_HTML);
        return message;
    }

    /**
     * Создаёт EditMessageText с HTML форматированием и кнопками для взаимодействия.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения для редактирования
     * @param text      текст сообщения
     * @param keyboardBuilder готовая клавиатура
     * @return настроенный объект EditMessageText
     */
    public static EditMessageText createHtmlEditMessageWithKeyBoard(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboardBuilder) {
        EditMessageText message = createHtmlEditMessage(chatId, messageId, text);
        message.setReplyMarkup(keyboardBuilder);
        return message;
    }

    /**
     * Создаёт сообщение об ошибке с HTML форматированием.
     *
     * @param chatId ID чата
     * @param error  текст ошибки
     * @return настроенный объект SendMessage с ошибкой
     */
    public static SendMessage createErrorMessage(Long chatId, String error) {
        return createHtmlMessage(chatId, BotMessage.ERROR_GENERIC.format(error));
    }

    /**
     * Создаёт EditMessageText с ошибкой.
     *
     * @param chatId    ID чата
     * @param messageId ID сообщения для редактирования
     * @param error     текст ошибки
     * @return настроенный объект EditMessageText с ошибкой
     */
    public static EditMessageText createErrorEditMessage(Long chatId, Integer messageId, String error) {
        return createHtmlEditMessage(chatId, messageId, BotMessage.ERROR_GENERIC.format(error));
    }
}
