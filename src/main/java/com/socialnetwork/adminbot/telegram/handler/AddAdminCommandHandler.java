package com.socialnetwork.adminbot.telegram.handler;


import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.entity.AdminRole;
import com.socialnetwork.adminbot.service.AdminService;
import com.socialnetwork.adminbot.service.AuditLogService;
import com.socialnetwork.adminbot.service.ConversationStateService;
import com.socialnetwork.adminbot.service.InviteService;
import com.socialnetwork.adminbot.telegram.handler.base.StatefulCommandHandler;
import com.socialnetwork.adminbot.telegram.keyboard.KeyboardBuilder;
import com.socialnetwork.adminbot.telegram.messages.BotMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

/**
 * Handler для команды /addadmin с State Machine
 * Генерирует ссылки-приглашения для новых администраторов
 *
 * Доступно только для SUPER_ADMIN
 *
 * Workflow:
 * 1. /addadmin -> Показывает меню выбора роли (переход в AWAITING_ADMIN_ROLE)
 * 2. Callback с ролью -> Генерирует ссылку-приглашение (возврат в IDLE)
 */
@Slf4j
@Component
public class AddAdminCommandHandler extends StatefulCommandHandler {
    private final AdminService adminService;
    private final InviteService inviteService;
    private final AuditLogService auditLogService;

    @Value("${telegram.bot.username}")
    private String botUsername;

    public AddAdminCommandHandler(
            ConversationStateService conversationStateService,
            AdminService adminService,
            InviteService inviteService,
            AuditLogService auditLogService
    ) {
        super(conversationStateService);
        this.adminService = adminService;
        this.inviteService = inviteService;
        this.auditLogService = auditLogService;
    }

    @Override
    public String getCommandName() {
        return "addadmin";
    }

    @Override
    protected BotState[] getRelatedStates() {
        return new BotState[]{
                BotState.AWAITING_ADMIN_ROLE
        };
    }

    @Override
    protected boolean isInActiveConversation(ConversationState state) {
        BotState currentState = state.getState();
        return currentState == BotState.AWAITING_ADMIN_ROLE;
    }

    @Override
    protected SendMessage startConversation(Message message, Long adminId) {
        Long chatId = message.getChatId();

        // Проверка прав - только SUPER_ADMIN
        if (!adminService.hasRole(adminId, AdminRole.SUPER_ADMIN)) {
            log.warn("Unauthorized attempt to use /addadmin: adminId={}", adminId);
            auditLogService.logAction("UNAUTHORIZED_ADDADMIN_ATTEMPT", adminId);

            return createMessage(chatId,
                    BotMessage.ERROR_INSUFFICIENT_PERMISSIONS.format("SUPER_ADMIN"));
        }

        // Создаем новое состояние ожидания выбора роли
        ConversationState newState = ConversationState.builder()
                .state(BotState.AWAITING_ADMIN_ROLE)
                .build();

        conversationStateService.setState(adminId, newState);

        log.info("Admin {} initiated /addadmin command, transitioned to AWAITING_ADMIN_ROLE", adminId);

        // Показываем меню выбора роли
        SendMessage response = createMessage(chatId, BotMessage.ADMIN_SELECT_ROLE.raw());
        response.setReplyMarkup(KeyboardBuilder.buildRoleSelectionKeyboard());

        return response;
    }

    @Override
    protected SendMessage handleConversationStep(
            Message message,
            Long adminId,
            ConversationState state
    ) {
        BotState currentState = state.getState();

        if (currentState == BotState.AWAITING_ADMIN_ROLE) {
            // Если пользователь в состоянии выбора роли, но вводит текст
            // (а не нажимает кнопку), напоминаем использовать кнопки
            return createMessage(message.getChatId(),
                    "⚠️ Пожалуйста, используйте кнопки для выбора роли или /cancel для отмены.");
        }

        return createMessage(message.getChatId(), BotMessage.ERROR_UNKNOWN_STATE.raw());
    }

    /**
     * Обработать выбор роли (вызывается из CallbackQueryHandler)
     * Генерирует ссылку-приглашение
     *
     * @param adminId ID администратора
     * @param selectedRole выбранная роль
     * @return текст сообщения со ссылкой
     */
    public String handleRoleSelection(Long adminId, AdminRole selectedRole) {
        ConversationState state = conversationStateService.getState(adminId);

        // Проверяем, что админ в правильном состоянии
        if (state.getState() != BotState.AWAITING_ADMIN_ROLE) {
            log.warn("Admin {} tried to select role from wrong state: {}",
                    adminId, state.getState());
            return BotMessage.ERROR_UNKNOWN_STATE.raw();
        }

        // Проверяем, что админ может назначать эту роль
        AdminRole adminRole = adminService.getRole(adminId);

        if (!adminRole.canAssignRole(selectedRole)) {
            log.warn("Admin {} tried to assign higher or equal role: {}",
                    adminId, selectedRole);
            conversationStateService.resetToIdle(adminId);
            return BotMessage.ERROR_INSUFFICIENT_PERMISSIONS.format(adminRole.name());
        }

        try {
            // Генерируем токен приглашения
            String inviteToken = inviteService.createInvitation(adminId, selectedRole);

            // Формируем ссылку
            String inviteLink = String.format("https://t.me/%s?start=invite_%s",
                    botUsername, inviteToken);

            // Логируем действие
            auditLogService.logAction("GENERATE_INVITE_LINK", adminId, null,
                    "role=" + selectedRole.name());

            // Сбрасываем состояние в IDLE
            conversationStateService.resetToIdle(adminId);

            log.info("Generated invite link: adminId={}, role={}, token={}",
                    adminId, selectedRole, inviteToken);

            return BotMessage.ADMIN_INVITE_GENERATED.format(inviteLink, selectedRole.name());

        } catch (Exception e) {
            log.error("Error generating invite link: adminId={}, role={}",
                    adminId, selectedRole, e);

            conversationStateService.resetToIdle(adminId);

            return BotMessage.ERROR_GENERIC.format(
                    "Не удалось создать ссылку-приглашение. Попробуйте позже.");
        }
    }

    /**
     * Отменить создание приглашения (вызывается из CallbackQueryHandler)
     */
    public String cancelAddAdmin(Long adminId) {
        log.info("Admin {} cancelled /addadmin operation", adminId);
        conversationStateService.resetToIdle(adminId);
        return BotMessage.ADMIN_INVITE_CANCELLED.raw();
    }
}
