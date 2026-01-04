package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.domain.BotState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Сервис для валидации и управления переходами между состояниями
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateTransitionService {

    private final ConversationStateService conversationStateService;

    /**
     * Разрешённые переходы между состояниями
     * Ключ: текущее состояние
     * Значение: множество состояний, в которые можно перейти
     */
    private static final Map<BotState, Set<BotState>> ALLOWED_TRANSITIONS = createAllowedTransitions();

    private static Map<BotState, Set<BotState>> createAllowedTransitions() {
        Map<BotState, Set<BotState>> transitions = new HashMap<>();

        transitions.put(BotState.IDLE, EnumSet.of(
                BotState.AWAITING_SEARCH_QUERY,
                BotState.AWAITING_ADMIN_TELEGRAM_ID, // legacy
                BotState.AWAITING_ADMIN_USERNAME,    // new invite-based flow
                BotState.AWAITING_BAN_REASON
        ));

        transitions.put(BotState.AWAITING_SEARCH_QUERY, EnumSet.of(
                BotState.SHOWING_SEARCH_RESULTS,
                BotState.IDLE
        ));

        transitions.put(BotState.SHOWING_SEARCH_RESULTS, EnumSet.of(
                BotState.SHOWING_SEARCH_RESULTS, // для пагинации
                BotState.IDLE
        ));

        // Legacy admin creation flow (deprecated)
        transitions.put(BotState.AWAITING_ADMIN_TELEGRAM_ID, EnumSet.of(
                BotState.AWAITING_ADMIN_ROLE,
                BotState.IDLE
        ));

        // New invite-based admin creation flow (v2.2)
        transitions.put(BotState.AWAITING_ADMIN_USERNAME, EnumSet.of(
                BotState.AWAITING_ADMIN_ROLE,
                BotState.IDLE
        ));

        transitions.put(BotState.AWAITING_ADMIN_ROLE, EnumSet.of(
                BotState.CONFIRMING_ADMIN_CREATION,        // legacy
                BotState.CONFIRMING_ADMIN_INVITE_CREATION, // new
                BotState.IDLE
        ));

        // Legacy confirmation (deprecated)
        transitions.put(BotState.CONFIRMING_ADMIN_CREATION, EnumSet.of(
                BotState.IDLE
        ));

        // New invite creation confirmation (v2.2)
        transitions.put(BotState.CONFIRMING_ADMIN_INVITE_CREATION, EnumSet.of(
                BotState.IDLE
        ));

        // Invite acceptance confirmation (candidate side)
        transitions.put(BotState.CONFIRMING_INVITE_ACCEPTANCE, EnumSet.of(
                BotState.IDLE
        ));

        // Ban flow
        transitions.put(BotState.AWAITING_BAN_REASON, EnumSet.of(
                BotState.CONFIRMING_BAN,
                BotState.IDLE
        ));

        transitions.put(BotState.CONFIRMING_BAN, EnumSet.of(
                BotState.IDLE
        ));

        return Collections.unmodifiableMap(transitions);
    }

    /**
     * Проверить, разрешён ли переход между состояниями
     */
    public boolean isTransitionAllowed(BotState from, BotState to) {
        Set<BotState> allowedStates = ALLOWED_TRANSITIONS.get(from);

        if (allowedStates == null) {
            log.warn("No transition rules defined for state: {}", from);
            return false;
        }

        return allowedStates.contains(to);
    }

    /**
     * Выполнить переход с валидацией
     * @throws IllegalStateException если переход не разрешён
     */
    public void transitionTo(Long telegramUserId, BotState newState) {
        BotState currentState = conversationStateService.getCurrentState(telegramUserId);

        if (!isTransitionAllowed(currentState, newState)) {
            String error = String.format(
                    "Transition not allowed: %s -> %s for user %d",
                    currentState, newState, telegramUserId
            );
            log.error(error);
            throw new IllegalStateException(error);
        }

        conversationStateService.transitionTo(telegramUserId, newState);
        log.info("State transition: {} -> {} for user {}",
                currentState, newState, telegramUserId);
    }

    /**
     * Безопасный переход с очисткой данных
     */
    public void transitionToWithClear(Long telegramUserId, BotState newState) {
        BotState currentState = conversationStateService.getCurrentState(telegramUserId);

        if (!isTransitionAllowed(currentState, newState)) {
            String error = String.format(
                    "Transition not allowed: %s -> %s for user %d",
                    currentState, newState, telegramUserId
            );
            log.error(error);
            throw new IllegalStateException(error);
        }

        conversationStateService.transitionToWithClear(telegramUserId, newState);
        log.info("State transition with clear: {} -> {} for user {}",
                currentState, newState, telegramUserId);
    }

    /**
     * Принудительный сброс в IDLE (всегда разрешён)
     */
    public void forceResetToIdle(Long telegramUserId) {
        conversationStateService.resetToIdle(telegramUserId);
        log.info("Force reset to IDLE for user {}", telegramUserId);
    }
}
