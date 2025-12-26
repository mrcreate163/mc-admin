package com.socialnetwork.adminbot.telegram.keyboard;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KeyboardBuilder {

    public static InlineKeyboardMarkup buildUserActionsKeyboard(UUID userId, boolean isBlocked) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("📊 Statistics")
                .callbackData("stats:" + userId)
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text(isBlocked ? "✅ Unblock" : "🚫 Block")
                .callbackData((isBlocked ? "unblock:" : "block:") + userId)
                .build());
        rows.add(row1);

        List<InlineKeyboardButton> row2 = List.of(
                InlineKeyboardButton.builder()
                        .text("« Back to Menu")
                        .callbackData("main_menu")
                        .build()
        );
        rows.add(row2);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public static InlineKeyboardMarkup buildMainMenuKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = List.of(
                InlineKeyboardButton.builder()
                        .text("📊 View Statistics")
                        .callbackData("show_stats")
                        .build()
        );
        rows.add(row1);

        List<InlineKeyboardButton> row2 = List.of(
                InlineKeyboardButton.builder()
                        .text("👥 List Users")
                        .callbackData("list_users")
                        .build()
        );
        rows.add(row2);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }
}
