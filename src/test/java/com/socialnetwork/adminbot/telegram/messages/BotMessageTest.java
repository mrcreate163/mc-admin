package com.socialnetwork.adminbot.telegram.messages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BotMessage Unit Tests")
class BotMessageTest {

    @Test
    @DisplayName("raw - should return template without modification")
    void raw_ShouldReturnTemplateWithoutModification() {
        // When
        String result = BotMessage.WELCOME_TITLE.raw();

        // Then
        assertThat(result).isEqualTo("👋 <b>Добро пожаловать в Админ-панель!</b>");
    }

    @Test
    @DisplayName("format - should substitute single placeholder")
    void format_ShouldSubstituteSinglePlaceholder() {
        // When
        String result = BotMessage.WELCOME_GREETING.format("Иван");

        // Then
        assertThat(result).isEqualTo("Привет, Иван!");
    }

    @Test
    @DisplayName("format - should substitute multiple placeholders")
    void format_ShouldSubstituteMultiplePlaceholders() {
        // When
        String result = BotMessage.USER_INFO_NAME.format("Иван", "Петров");

        // Then
        assertThat(result).isEqualTo("Имя: Иван Петров");
    }

    @Test
    @DisplayName("format - should substitute numeric placeholders")
    void format_ShouldSubstituteNumericPlaceholders() {
        // When
        String result = BotMessage.STATS_TOTAL_USERS.format(1500);

        // Then
        assertThat(result).isEqualTo("Всего пользователей: 1500");
    }

    @Test
    @DisplayName("format - should return raw when no arguments")
    void format_WhenNoArguments_ShouldReturnRaw() {
        // When
        String result = BotMessage.WELCOME_TITLE.format();

        // Then
        assertThat(result).isEqualTo(BotMessage.WELCOME_TITLE.raw());
    }

    @Test
    @DisplayName("format - should return raw when null arguments")
    void format_WhenNullArguments_ShouldReturnRaw() {
        // When
        String result = BotMessage.WELCOME_TITLE.format((Object[]) null);

        // Then
        assertThat(result).isEqualTo(BotMessage.WELCOME_TITLE.raw());
    }

    @Test
    @DisplayName("hasPlaceholders - should return true when has placeholders")
    void hasPlaceholders_WhenHasPlaceholders_ShouldReturnTrue() {
        // When & Then
        assertThat(BotMessage.WELCOME_GREETING.hasPlaceholders()).isTrue();
        assertThat(BotMessage.STATS_TOTAL_USERS.hasPlaceholders()).isTrue();
    }

    @Test
    @DisplayName("hasPlaceholders - should return false when no placeholders")
    void hasPlaceholders_WhenNoPlaceholders_ShouldReturnFalse() {
        // When & Then
        assertThat(BotMessage.WELCOME_TITLE.hasPlaceholders()).isFalse();
        assertThat(BotMessage.MAIN_MENU_TITLE.hasPlaceholders()).isFalse();
    }

    @Test
    @DisplayName("escapeHtml - should escape special characters")
    void escapeHtml_ShouldEscapeSpecialCharacters() {
        // When
        String result = BotMessage.escapeHtml("<script>alert('xss')</script>");

        // Then
        assertThat(result).isEqualTo("&lt;script&gt;alert('xss')&lt;/script&gt;");
    }

    @Test
    @DisplayName("escapeHtml - should escape ampersand")
    void escapeHtml_ShouldEscapeAmpersand() {
        // When
        String result = BotMessage.escapeHtml("Tom & Jerry");

        // Then
        assertThat(result).isEqualTo("Tom &amp; Jerry");
    }

    @Test
    @DisplayName("escapeHtml - should return N/A for null")
    void escapeHtml_WhenNull_ShouldReturnNA() {
        // When
        String result = BotMessage.escapeHtml(null);

        // Then
        assertThat(result).isEqualTo(BotMessage.STATUS_UNKNOWN.raw());
    }

    @Test
    @DisplayName("join - should join multiple messages with newline")
    void join_ShouldJoinMessagesWithNewline() {
        // When
        String result = BotMessage.join(BotMessage.WELCOME_TITLE, BotMessage.MAIN_MENU_TITLE);

        // Then
        assertThat(result).contains(BotMessage.WELCOME_TITLE.raw());
        assertThat(result).contains(BotMessage.MAIN_MENU_TITLE.raw());
        assertThat(result).contains("\n");
    }

    @Test
    @DisplayName("joinLines - should join lines with custom delimiter")
    void joinLines_ShouldJoinLinesWithCustomDelimiter() {
        // When
        String result = BotMessage.joinLines("\n\n", "Line 1", "Line 2", "Line 3");

        // Then
        assertThat(result).isEqualTo("Line 1\n\nLine 2\n\nLine 3");
    }
}
