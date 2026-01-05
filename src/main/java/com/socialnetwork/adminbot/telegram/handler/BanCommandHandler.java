package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.constant.BotConstants;
import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.service.StateTransitionService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.handler.base.StatefulCommandHandler;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;
import java.util.UUID;

/**
 * Handler для команды бана пользователя с подтверждением через State Machine.
 * Поддерживает многошаговый flow: выбор причины -&gt; подтверждение -&gt; выполнение.
 *
 * @since 1.0
 */
@Slf4j
@Component
public class BanCommandHandler extends StatefulCommandHandler {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final StateTransitionService stateTransitionService;

    public BanCommandHandler(
            ConversationStateService conversationStateService,
            StateTransitionService stateTransitionService,
            UserService userService,
            AuditLogService auditLogService
    ) {
        super(conversationStateService);
        this.stateTransitionService = stateTransitionService;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @Override
    public String getCommandName() {
        return "ban";
    }

    @Override
    protected BotState[] getRelatedStates() {
        return new BotState[]{
                BotState.AWAITING_BAN_REASON,
                BotState.CONFIRMING_BAN
        };
    }

    @Override
    protected boolean isInActiveConversation(ConversationState state) {
        BotState currentState = state.getState();
        return currentState == BotState.AWAITING_BAN_REASON
                || currentState == BotState.CONFIRMING_BAN;
    }

    @Override
    protected SendMessage startConversation(Message message, Long adminId) {
        String[] args = extractArgs(message.getText());

        // Проверка наличия аргумента
        if (args.length < 1) {
            return createMessage(message.getChatId(), BotMessage.BAN_USAGE.raw());
        }

        try {
            // Парсим UUID
            UUID userId = UUID.fromString(args[0]);

            // Получаем информацию о пользователе для подтверждения
            String userEmail = userService.getUserById(userId)
                    .getEmail();


            // Сохраняем данные в состояние
            ConversationState newState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();

            newState.addData(StateDataKey.BAN_TARGET_USER_ID, userId.toString());
            newState.addData(StateDataKey.BAN_TARGET_EMAIL, userEmail);

            conversationStateService.setState(adminId, newState);

            log.info("User {} started ban conversation for target user {}", adminId, userId);

            // Предлагаем выбрать причину бана
            SendMessage response = createMessage(
                    message.getChatId(),
                    String.join("\n\n",
                            BotMessage.BAN_HEADER.format(userEmail, userId),
                            BotMessage.CHOOSE_REASON.raw())
            );

            response.setReplyMarkup(KeyboardBuilder.buildBanReasonsKeyboard());

            return response;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format: {}", args[0]);
            return createMessage(message.getChatId(), BotMessage.ERROR_INVALID_USER_ID.raw());
        } catch (Exception e) {
            log.error("Error starting ban conversation: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createMessage(message.getChatId(),
                    BotMessage.ERROR_GENERIC.format(e.getMessage()));
        }
    }

    @Override
    protected SendMessage handleConversationStep(
            Message message,
            Long adminId,
            ConversationState state
    ) {
        BotState currentState = state.getState();

        if (currentState == BotState.AWAITING_BAN_REASON) {
            return handleBanReasonInput(message, adminId, state);
        } else if (currentState == BotState.CONFIRMING_BAN) {
            // Если пользователь в состоянии подтверждения, но вводит текст
            // (а не нажимает кнопку), игнорируем
            return createMessage(message.getChatId(), BotMessage.ACCEPT_OR_CANCEL.raw());
        }

        return createMessage(message.getChatId(), BotMessage.ERROR_UNKNOWN_STATE.raw());
    }

    /**
     * Обработка ввода причины бана.
     */
    private SendMessage handleBanReasonInput(Message message, Long adminId, ConversationState state) {
        String reason = message.getText().trim();

        if (reason.length() < BotConstants.MIN_BAN_REASON_LENGTH ||
                reason.length() > BotConstants.MAX_BAN_REASON_LENGTH) {
            return createMessage(message.getChatId(),
                    BotMessage.LIMIT_FOR_REASON.raw());
        }

        // Получаем данные из состояния
        String targetUserIdStr = state.getData(StateDataKey.BAN_TARGET_USER_ID, String.class);
        String targetUserEmail = state.getData(StateDataKey.BAN_TARGET_EMAIL, String.class);

        try {
            // Сохраняем причину в Redis перед переходом к подтверждению
            conversationStateService.updateStateData(adminId, StateDataKey.BAN_REASON, reason);
            stateTransitionService.transitionTo(adminId, BotState.CONFIRMING_BAN);

            log.info("User {} provided ban reason: {}", adminId, reason);

            // Показываем сообщение с подтверждением
            String confirmationText = String.join("\n\n",
                    BotMessage.ACCEPT_TO_BLOCK.raw(),
                    BotMessage.USER_INFO_EMAIL.format(targetUserEmail),
                    BotMessage.USER_INFO_ID.format(targetUserIdStr),
                    BotMessage.BAN_REASON.format(BotMessage.escapeHtml(reason)),
                    BotMessage.ACCEPT_TO_BLOCK_2.raw());

            SendMessage response = createMessage(message.getChatId(), confirmationText);
            response.setReplyMarkup(KeyboardBuilder.buildConfirmationKeyboard("ban_confirm"));

            return response;

        } catch (Exception e) {
            log.error("Error transitioning to CONFIRMING_BAN: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createMessage(message.getChatId(),
                    BotMessage.ERROR_GENERIC.format(e.getMessage()));
        }
    }

    /**
     * Выполнить бан после подтверждения (вызывается из CallbackQueryHandler)
     */
    public SendMessage executeBan(Long chatId, Long adminId) {
        ConversationState state = conversationStateService.getState(adminId);

        if (state.getState() != BotState.CONFIRMING_BAN) {
            log.warn("User {} tried to execute ban from wrong state: {}", adminId, state.getState());
            return createMessage(chatId, BotMessage.ERROR_STATE_FOR_BAN.raw());
        }

        try {
            // Извлекаем данные из состояния
            String targetUserIdStr = state.getData(StateDataKey.BAN_TARGET_USER_ID, String.class);
            String targetUserEmail = state.getData(StateDataKey.BAN_TARGET_EMAIL, String.class);
            String reason = state.getData(StateDataKey.BAN_REASON, String.class);

            UUID targetUserId = UUID.fromString(targetUserIdStr);

            // Выполняем бан
            userService.blockUser(targetUserId, adminId, reason);

            // Логируем действие
            auditLogService.logAction(
                    "BLOCK_USER",
                    adminId,
                    targetUserId,
                    Map.of("reason", reason, "source", "stateful_ban").toString()
            );

            log.info("User {} successfully banned user {} with reason: {}",
                    adminId, targetUserId, reason);

            // Сбрасываем состояние
            conversationStateService.resetToIdle(adminId);

            return createMessage(chatId,
                    String.join("\n\n",
                            BotMessage.BAN_SUCCESS_2.raw(),
                            BotMessage.USER_INFO_EMAIL.format(targetUserEmail),
                            BotMessage.USER_INFO_ID.format(targetUserId),
                            BotMessage.BAN_REASON.format(BotMessage.escapeHtml(reason))));

        } catch (Exception e) {
            log.error("Error executing ban: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createMessage(chatId, BotMessage.ERROR_GENERIC.format(e.getMessage()));
        }
    }

    /**
     * Отменить бан (вызывается из CallbackQueryHandler)
     */
    public SendMessage cancelBan(Long chatId, Long adminId) {
        log.info("User {} cancelled ban operation", adminId);
        conversationStateService.resetToIdle(adminId);

        return createMessage(chatId,
                String.join("\n\n",
                        BotMessage.BAN_CANCELED.raw(),
                        BotMessage.REMAINING_FOR_CANCELED_BAN.raw())
        );
    }

    /**
     * Обработка команды разблокировки (оставляем как stateless)
     */
    public SendMessage handleUnban(Message message, Long adminId) {
        String[] args = extractArgs(message.getText());

        if (args.length < 1) {
            return createMessage(message.getChatId(), BotMessage.UNBAN_USAGE.raw());
        }

        try {
            UUID userId = UUID.fromString(args[0]);
            userService.unblockUser(userId, adminId);

            auditLogService.logAction(
                    "UNBLOCK_USER",
                    adminId,
                    userId,
                    Map.of("reason", "Manual unban").toString()
            );

            return createMessage(message.getChatId(), BotMessage.UNBAN_SUCCESS.format(userId));

        } catch (IllegalArgumentException e) {
            return createMessage(message.getChatId(), BotMessage.ERROR_INVALID_USER_ID.raw());
        } catch (Exception e) {
            return createMessage(message.getChatId(),
                    BotMessage.ERROR_GENERIC.format(e.getMessage()));
        }
    }

}
