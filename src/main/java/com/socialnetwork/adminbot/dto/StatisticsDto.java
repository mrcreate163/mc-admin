package com.socialnetwork.adminbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsDto {
    private Long totalUsers;
    private Long activeUsers;
    private Long blockedUsers;
    private Long newUsersToday;
    private Long totalAdmins;
}
