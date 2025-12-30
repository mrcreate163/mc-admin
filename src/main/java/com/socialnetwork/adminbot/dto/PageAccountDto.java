package com.socialnetwork.adminbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для пагинированного списка аккаунтов
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageAccountDto {

    private List<AccountDto> content;      // Список аккаунтов на текущей странице
    private int totalPages;                // Общее количество страниц
    private long totalElements;            // Общее количество элементов
    private int size;                      // Размер страницы
    private int number;                    // Номер текущей страницы (0-based)
    private boolean first;                 // Первая ли это страница
    private boolean last;                  // Последняя ли это страница
    private boolean empty;                 // Пустая ли страница
}
