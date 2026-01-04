package com.socialnetwork.adminbot.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Модель состояния диалога с пользователем
 * Хранится в Redis с TTL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationState {

    /**
     * Текущее состояние диалога
     */
    private BotState state;

    /**
     * Контекстные данные для текущего диалога
     * Ключ - название параметра, значение - данные
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * Версия состояния (для оптимистичной блокировки)
     */
    @Builder.Default
    private Long version = 0L;

    /**
     * Время создания состояния
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Время последнего обновления состояния
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Добавить данные в контекст
     */
    public void addData(String key, Object value) {
        this.data.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Получить данные из контекста с безопасной обработкой типов
     */
    public <T> T getData(String key, Class<T> type) {
        Object value = this.data.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        // Handle type conversion for Number types (Redis deserialization may return different types)
        if (type == Integer.class && value instanceof Number) {
            return type.cast(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return type.cast(((Number) value).longValue());
        }
        if (type == Double.class && value instanceof Number) {
            return type.cast(((Number) value).doubleValue());
        }
        throw new ClassCastException("Cannot cast " + value.getClass().getName() + " to " + type.getName());
    }

    /**
     * Проверить наличие данных в контексте
     */
    public boolean hasData(String key) {
        return this.data.containsKey(key);
    }

    /**
     * Очистить все данные контекста
     */
    public void clearData() {
        this.data.clear();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Увеличить версию (для оптимистичной блокировки)
     */
    public void incrementVersion() {
        this.version++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Создать новое состояние в IDLE
     */
    public static ConversationState idle() {
        LocalDateTime now = LocalDateTime.now();
        return ConversationState.builder()
                .state(BotState.IDLE)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
