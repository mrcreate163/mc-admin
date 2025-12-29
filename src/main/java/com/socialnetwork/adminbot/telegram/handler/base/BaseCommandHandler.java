package com.socialnetwork.adminbot.telegram.handler.base;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

/**
 * Базовый абстрактный класс для всех command handlers
 */
public abstract class BaseCommandHandler {

    /**
     * Обработать команду
     *
     * @param message Сообщение от Telegram
     * @param adminId ID администратора
     * @return Ответное сообщение
     */
    public abstract SendMessage handle(Message message, Long adminId);

    /**
     * Получить имя команды (например, "start", "ban", "search")
     */
    public abstract String getCommandName();

    /**
     * Создать простое текстовое сообщение
     */
    protected SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }

    /**
     * Извлечь аргументы из команды
     * Например: "/ban uuid-here" -> ["uuid-here"]
     */
    protected String[] extractArgs(String text) {
        String[] parts = text.split(" ");
        if (parts.length <= 1) {
            return new String[0];
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, parts.length - 1);
        return args;
    }
}
