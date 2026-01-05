package com.socialnetwork.adminbot.exception;

/**
 * Исключение выбрасывается когда не удаётся сгенерировать уникальный токен.
 * Может указывать на проблемы с БД или чрезмерное количество существующих токенов.
 */
public class TokenGenerationException extends RuntimeException {

    private final int attemptsMade;

    public TokenGenerationException(String message, int attemptsMade) {
        super(message);
        this.attemptsMade = attemptsMade;
    }

    public TokenGenerationException(String message, int attemptsMade, Throwable cause) {
        super(message, cause);
        this.attemptsMade = attemptsMade;
    }

    public int getAttemptsMade() {
        return attemptsMade;
    }

    /**
     * Создаёт исключение для случая превышения лимита попыток.
     */
    public static TokenGenerationException maxAttemptsExceeded(int maxAttempts) {
        return new TokenGenerationException(
                String.format("Failed to generate unique invite token after %d attempts. " +
                                "This may indicate database issues or excessive number of existing tokens.",
                        maxAttempts),
                maxAttempts
        );
    }

    /**
     * Создаёт исключение для случая проблем с БД.
     */
    public static TokenGenerationException databaseError(int attemptsMade, Throwable cause) {
        return new TokenGenerationException(
                String.format("Database error during token generation after %d attempts", attemptsMade),
                attemptsMade,
                cause
        );
    }
}
