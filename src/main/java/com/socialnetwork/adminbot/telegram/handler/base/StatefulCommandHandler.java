package com.socialnetwork.adminbot.telegram.handler.base;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.service.ConversationStateService;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

/**
 * Базовый класс для stateful handlers
 * Используется для диалоговых команд: /search, /ban (с подтверждением), /add_admin
 */
@RequiredArgsConstructor
public abstract class StatefulCommandHandler extends BaseCommandHandler {

    protected final ConversationStateService conversationStateService;

    /**
     * Обработать команду с учётом текущего состояния
     */
    @Override
    public SendMessage handle(Message message, Long adminId) {
        ConversationState state = conversationStateService.getState(adminId);

        // Если пользователь в активном диалоге, обрабатываем как продолжение
        if (isInActiveConversation(state)) {
            return handleConversationStep(message, adminId, state);
        }

        // Иначе начинаем новый диалог
        return startConversation(message, adminId);
    }

    /**
     * Начать новый диалог
     */
    protected abstract SendMessage startConversation(Message message, Long adminId);

    /**
     * Обработать шаг в активном диалоге
     */
    protected abstract SendMessage handleConversationStep(
            Message message,
            Long adminId,
            ConversationState state
    );

    /**
     * Проверить, находится ли пользователь в активном диалоге для этого handler'а
     */
    protected abstract boolean isInActiveConversation(ConversationState state);

    /**
     * Получить состояния, которые относятся к этому handler'у
     */
    protected abstract BotState[] getRelatedStates();
}
