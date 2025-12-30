package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.dto.PageAccountDto;
import com.socialnetwork.adminbot.dto.StatisticsDto;
import com.socialnetwork.adminbot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final UserService userService;
    private final AuditLogRepository auditLogRepository;

    public StatisticsDto getStatistics() {
        try {
            // Get first page to extract totals
            PageAccountDto firstPage = userService.getUsersPage(0, 1);

            long totalUsers = firstPage.getTotalElements();
            
            // Calculate new users today
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            Long newUsersToday = auditLogRepository.countByActionTypeAndCreatedAtAfter("USER_REGISTERED", startOfDay);

            // For MVP, we'll use simplified statistics
            // In future versions, we can add more detailed statistics from the account service
            return StatisticsDto.builder()
                    .totalUsers(totalUsers)
                    .activeUsers(0L) // Will be implemented in v2.0
                    .blockedUsers(0L) // Will be implemented in v2.0
                    .newUsersToday(newUsersToday)
                    .totalAdmins(0L) // Will be implemented in v2.0
                    .build();

        } catch (Exception e) {
            log.error("Error fetching statistics: {}", e.getMessage(), e);
            return StatisticsDto.builder()
                    .totalUsers(0L)
                    .activeUsers(0L)
                    .blockedUsers(0L)
                    .newUsersToday(0L)
                    .totalAdmins(0L)
                    .build();
        }
    }
}
