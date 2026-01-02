package com.socialnetwork.adminbot.telegram.handler;

import com.socialnetwork.adminbot.domain.BotState;
import com.socialnetwork.adminbot.domain.ConversationState;
import com.socialnetwork.adminbot.domain.StateDataKey;
import com.socialnetwork.adminbot.dto.AccountDto;
import com.socialnetwork.adminbot.dto.PageAccountDto;
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

import java.util.List;

/**
 * Handler для команды поиска пользователей с пагинацией через State Machine
 */
@Slf4j
@Component
public class SearchCommandHandler extends StatefulCommandHandler {

    private static final int PAGE_SIZE = 5; // Оптимально для мобильного интерфейса
    private static final int MIN_QUERY_LENGTH = 3;
    private static final String EMAIL_PATTERN = "^[a-zA-Z0-9@._-]+$";

    private final UserService userService;
    private final StateTransitionService stateTransitionService;

    public SearchCommandHandler(
            ConversationStateService conversationStateService,
            StateTransitionService stateTransitionService,
            UserService userService
    ) {
        super(conversationStateService);
        this.stateTransitionService = stateTransitionService;
        this.userService = userService;
    }

    @Override
    public String getCommandName() {
        return "search";
    }

    @Override
    protected BotState[] getRelatedStates() {
        return new BotState[]{
                BotState.AWAITING_SEARCH_QUERY,
                BotState.SHOWING_SEARCH_RESULTS
        };
    }

    @Override
    protected boolean isInActiveConversation(ConversationState state) {
        BotState currentState = state.getState();
        return currentState == BotState.AWAITING_SEARCH_QUERY
                || currentState == BotState.SHOWING_SEARCH_RESULTS;
    }

    @Override
    protected SendMessage startConversation(Message message, Long adminId) {
        String[] args = extractArgs(message.getText());

        // Если аргумент передан сразу: /search john@example.com
        if (args.length > 0) {
            String query = String.join(" ", args).trim();
            return processSearchQuery(message.getChatId(), adminId, query);
        }

        // Иначе переходим в состояние ожидания ввода
        ConversationState newState = ConversationState.builder()
                .state(BotState.AWAITING_SEARCH_QUERY)
                .build();

        conversationStateService.setState(adminId, newState);

        log.info("User {} entered search mode, awaiting query", adminId);

        return createMessage(message.getChatId(), BotMessage.SEARCH_PROMPT.raw());
    }

    @Override
    protected SendMessage handleConversationStep(Message message, Long adminId, ConversationState state) {
        return null;
    }

    /**
     * Обработка поискового запроса (из команды или текстового сообщения)
     */
    public SendMessage processSearchQuery(Long chatId, Long adminId, String query) {
        // Валидация запроса
        if (query.length() < MIN_QUERY_LENGTH) {
            log.warn("Search query too short: '{}' (user={})", query, adminId);
            return createMessage(chatId, BotMessage.SEARCH_MIN_LENGTH.raw());
        }

        if (!query.matches(EMAIL_PATTERN)) {
            log.warn("Invalid search query format: '{}' (user={})", query, adminId);
            return createMessage(chatId, BotMessage.SEARCH_INVALID_QUERY.raw());
        }

        try {
            // Выполняем поиск
            PageAccountDto searchResults = userService.searchUsersByEmail(query, 0, PAGE_SIZE);

            // Проверяем результаты
            if (searchResults.isEmpty() || searchResults.getContent().isEmpty()) {
                log.info("No results found for query '{}' (user={})", query, adminId);
                conversationStateService.resetToIdle(adminId);
                return createMessage(chatId, BotMessage.SEARCH_NO_RESULTS.format(query));
            }

            // Сохраняем метаданные поиска в состояние
            ConversationState newState = ConversationState.builder()
                    .state(BotState.SHOWING_SEARCH_RESULTS)
                    .build();

            newState.addData(StateDataKey.SEARCH_QUERY, query);
            newState.addData(StateDataKey.SEARCH_CURRENT_PAGE, 0);
            newState.addData(StateDataKey.SEARCH_TOTAL_PAGES, searchResults.getTotalPages());
            newState.addData(StateDataKey.SEARCH_TOTAL_RESULTS, (int) searchResults.getTotalElements());

            conversationStateService.setState(adminId, newState);

            log.info("Search completed: query='{}', found={}, pages={} (user={})",
                    query, searchResults.getTotalElements(), searchResults.getTotalPages(), adminId);

            // Формируем сообщение с результатами
            return buildSearchResultsMessage(chatId, query, searchResults, 0);

        } catch (Exception e) {
            log.error("Error during search: query='{}', user={}, error={}",
                    query, adminId, e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createMessage(chatId, BotMessage.ERROR_GENERIC.format(e.getMessage()));
        }
    }

    /**
     * Пагинация: переход на следующую/предыдущую страницу
     */
    public SendMessage handlePageNavigation(Long chatId, Long adminId, int newPage) {
        ConversationState state = conversationStateService.getState(adminId);

        if (state.getState() != BotState.SHOWING_SEARCH_RESULTS) {
            log.warn("Invalid state for page navigation: {} (user={})", state.getState(), adminId);
            return createMessage(chatId, "⚠️ Поиск устарел. Используйте /search для нового поиска.");
        }

        try {
            String query = state.getData(StateDataKey.SEARCH_QUERY, String.class);
            Integer totalPages = state.getData(StateDataKey.SEARCH_TOTAL_PAGES, Integer.class);

            // Валидация номера страницы
            if (newPage < 0 || newPage >= totalPages) {
                log.warn("Invalid page number: {} (total={}, user={})", newPage, totalPages, adminId);
                return createMessage(chatId, "⚠️ Некорректный номер страницы.");
            }

            // Выполняем поиск для новой страницы
            PageAccountDto searchResults = userService.searchUsersByEmail(query, newPage, PAGE_SIZE);

            // Обновляем текущую страницу в состоянии
            conversationStateService.updateStateData(adminId, StateDataKey.SEARCH_CURRENT_PAGE, newPage);

            log.info("Page navigation: page={}/{}, query='{}' (user={})",
                    newPage + 1, totalPages, query, adminId);

            // Формируем сообщение с новой страницей
            return buildSearchResultsMessage(chatId, query, searchResults, newPage);

        } catch (Exception e) {
            log.error("Error during page navigation: user={}, page={}, error={}",
                    adminId, newPage, e.getMessage(), e);
            conversationStateService.resetToIdle(adminId);
            return createMessage(chatId, BotMessage.ERROR_GENERIC.format(e.getMessage()));
        }
    }

    /**
     * Отмена поиска
     */
    public SendMessage cancelSearch(Long chatId, Long adminId) {
        conversationStateService.resetToIdle(adminId);
        log.info("Search cancelled by user {}", adminId);
        return createMessage(chatId, BotMessage.SEARCH_CANCELLED.raw());
    }

    /**
     * Формирование сообщения с результатами поиска
     */
    private SendMessage buildSearchResultsMessage(
            Long chatId,
            String query,
            PageAccountDto results,
            int currentPage
    ) {
        StringBuilder text = new StringBuilder();

        // Заголовок
        text.append(BotMessage.SEARCH_RESULTS_HEADER.format(
                escapeHtml(query),
                results.getTotalElements(),
                currentPage + 1,
                results.getTotalPages()
        ));

        // Карточки пользователей - ограничиваем до PAGE_SIZE для защиты от некорректного ответа backend
        List<AccountDto> users = results.getContent();
        int usersToDisplay = Math.min(users.size(), PAGE_SIZE);
        
        // Логируем предупреждение если backend вернул больше элементов чем запрошено
        if (users.size() > PAGE_SIZE) {
            log.warn("Backend returned {} users instead of requested {}. Limiting display to {}.",
                    users.size(), PAGE_SIZE, PAGE_SIZE);
        }
        
        for (int i = 0; i < usersToDisplay; i++) {
            AccountDto user = users.get(i);

            text.append(String.format("<b>%d.</b> ", currentPage * PAGE_SIZE + i + 1));
            text.append(BotMessage.SEARCH_USER_CARD.format(
                    escapeHtml(user.getFirstName() != null ? user.getFirstName() : BotMessage.STATUS_UNKNOWN.raw()),
                    escapeHtml(user.getLastName() != null ? user.getLastName() : BotMessage.STATUS_UNKNOWN.raw()),
                    escapeHtml(user.getEmail()),
                    user.getId(),
                    user.getIsBlocked() ? "🔴 Заблокирован" : "🟢 Активен"
            ));

            // Разделитель между пользователями
            if (i < usersToDisplay - 1) {
                text.append("\n\n");
            }
        }

        SendMessage message = createMessage(chatId, text.toString());

        // Добавляем клавиатуру с действиями и пагинацией
        // Передаём только ограниченный список пользователей
        List<AccountDto> usersForKeyboard = users.subList(0, usersToDisplay);
        message.setReplyMarkup(KeyboardBuilder.buildSearchResultsKeyboard(
                usersForKeyboard,
                currentPage,
                results.getTotalPages()
        ));

        return message;
    }

    /**
     * Экранирование HTML для Telegram
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
