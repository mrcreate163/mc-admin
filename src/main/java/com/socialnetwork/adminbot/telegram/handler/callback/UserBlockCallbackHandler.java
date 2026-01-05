package com.socialnetwork.adminbot.telegram.handler.callback;

import com.socialnetwork.adminbot.constant.BotConstants;
import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.service.StateTransitionService;
import com.socialnetwork.adminbot.service.UserService;
import com.socialnetwork.adminbot.telegram.handler.BanCommandHandler;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Map;
import java.util.UUID;

/**
 * Обработчик callback-запросов для блокировки/разблокировки пользователей.
 * Обрабатывает: block:*, unblock:*, ban_reason:*, ban_confirm, ban_cancel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBlockCallbackHandler extends BaseCallbackHandler {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ConversationStateService conversationStateService;
    private final StateTransitionService stateTransitionService;
    private final BanCommandHandler banCommandHandler;

    @Override
    public boolean canHandle(String callbackData) {
        return callbackData.startsWith("block:") ||
               callbackData.startsWith("unblock:") ||
               callbackData.startsWith("ban_reason:") ||
               callbackData.equals("ban_confirm") ||
               callbackData.equals("ban_cancel");
    }

    @Override
    public EditMessageText handle(CallbackQuery callbackQuery, Long chatId, Integer messageId, Long adminId) {
        String data = callbackQuery.getData();

        try {
            if (data.startsWith("block:")) {
                return handleBlock(data, chatId, messageId, adminId);
            } else if (data.startsWith("unblock:")) {
                return handleUnblock(data, chatId, messageId, adminId);
            } else if (data.startsWith("ban_reason:")) {
                return handleBanReasonSelection(data, chatId, messageId, adminId);
            } else if (data.equals("ban_confirm")) {
                return handleBanConfirm(chatId, messageId, adminId);
            } else if (data.equals("ban_cancel")) {
                return handleBanCancel(chatId, messageId, adminId);
            }
        } catch (Exception e) {
            log.error("Error handling user block callback: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }

        return null;
    }

    /**
     * Обработка блокировки пользователя через callback.
     * Использует State Machine flow.
     */
    private EditMessageText handleBlock(String data, Long chatId, Integer messageId, Long adminId) {
        try {
            UUID userId = UUID.fromString(data.substring("block:".length()));

            // Проверяем, что пользователь в IDLE состоянии
            BotState currentState = conversationStateService.getCurrentState(adminId);
            if (currentState != BotState.IDLE) {
                return createErrorMessage(chatId, messageId, BotMessage.UNCOMPLETED_ACTION.raw());
            }

            // Получаем информацию о пользователе
            String email = userService.getUserById(userId).getEmail();

            // Создаём состояние для flow бана
            ConversationState newState = ConversationState.builder()
                    .state(BotState.AWAITING_BAN_REASON)
                    .build();

            newState.addData(StateDataKey.BAN_TARGET_USER_ID, userId.toString());
            newState.addData(StateDataKey.BAN_TARGET_EMAIL, email);

            conversationStateService.setState(adminId, newState);

            log.info("User {} started ban conversation via callback for target user {}", adminId, userId);

            // Показываем клавиатуру с причинами бана
            String text = String.join("\n\n",
                    BotMessage.USER_INFO_EMAIL.format(email),
                    BotMessage.USER_INFO_ID.format(userId),
                    BotMessage.CHOOSE_REASON.raw()
            );

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildBanReasonsKeyboard());

            return message;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in callback: {}, error: {}", data, e.getMessage());
            return createErrorMessage(chatId, messageId, "⚠️ Неверный формат ID пользователя");
        }
    }

    /**
     * Обработка выбора причины бана из клавиатуры.
     */
    private EditMessageText handleBanReasonSelection(String data, Long chatId, Integer messageId, Long adminId) {
        String reason = data.substring(BotConstants.CallbackPrefix.BAN_REASON.length());

        // Маппинг callback data -> человекочитаемая причина
        String readableReason = switch (reason) {
            case "spam" -> BotConstants.BanReasons.SPAM;
            case "harassment" -> BotConstants.BanReasons.HARASSMENT;
            case "bot" -> BotConstants.BanReasons.BOT_FAKE;
            case "violation" -> BotConstants.BanReasons.COMMUNITY_VIOLATION;
            default -> reason;
        };

        ConversationState state = conversationStateService.getState(adminId);

        if (state.getState() != BotState.AWAITING_BAN_REASON) {
            return createErrorMessage(chatId, messageId,
                    "⚠️ Ошибка: неверное состояние для выбора причины.");
        }

        try {
            // Сохраняем причину в Redis перед переходом к подтверждению
            conversationStateService.updateStateData(adminId, StateDataKey.BAN_REASON, readableReason);
            stateTransitionService.transitionTo(adminId, BotState.CONFIRMING_BAN);

            String targetUserIdStr = state.getData(StateDataKey.BAN_TARGET_USER_ID, String.class);
            String targetUserEmail = state.getData(StateDataKey.BAN_TARGET_EMAIL, String.class);

            String confirmationText = String.join("\n\n",
                    BotMessage.ACCEPT_TO_BLOCK.raw(),
                    BotMessage.USER_INFO_EMAIL.format(targetUserEmail),
                    BotMessage.USER_INFO_ID.format(targetUserIdStr),
                    BotMessage.BAN_REASON.format(escapeHtml(readableReason)),
                    BotMessage.ACCEPT_TO_BLOCK_2.raw()
            );

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(confirmationText);
            message.setParseMode("HTML");
            message.setReplyMarkup(KeyboardBuilder.buildConfirmationKeyboard("ban"));

            return message;

        } catch (Exception e) {
            log.error("Error processing ban reason selection: {}", e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createErrorMessage(chatId, messageId, e.getMessage());
        }
    }

    /**
     * Подтверждение бана.
     */
    private EditMessageText handleBanConfirm(Long chatId, Integer messageId, Long adminId) {
        SendMessage result = banCommandHandler.executeBan(chatId, adminId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(result.getText());
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Отмена бана.
     */
    private EditMessageText handleBanCancel(Long chatId, Integer messageId, Long adminId) {
        SendMessage result = banCommandHandler.cancelBan(chatId, adminId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(result.getText());
        message.setParseMode("HTML");

        return message;
    }

    /**
     * Обработка разблокировки пользователя через callback.
     */
    private EditMessageText handleUnblock(String data, Long chatId, Integer messageId, Long adminId) {
        UUID userId = UUID.fromString(data.substring("unblock:".length()));
        userService.unblockUser(userId, adminId);

        auditLogService.logAction("UNBLOCK_USER", adminId, userId,
                Map.of("source", "callback").toString());

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(BotMessage.UNBAN_CALLBACK_SUCCESS.format(userId));
        message.setParseMode("HTML");

        return message;
    }
}
