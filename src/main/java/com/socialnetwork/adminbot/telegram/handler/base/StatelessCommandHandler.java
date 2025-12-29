package com.socialnetwork.adminbot.telegram.handler.base;

/**
 * Базовый класс для stateless handlers
 * Используется для простых команд, которые не требуют диалога:
 * /start, /stats, /help
 */
public abstract class StatelessCommandHandler extends BaseCommandHandler {

    /**
     * Stateless handlers не управляют состоянием
     * Они просто обрабатывают команду и возвращают ответ
     */
}
