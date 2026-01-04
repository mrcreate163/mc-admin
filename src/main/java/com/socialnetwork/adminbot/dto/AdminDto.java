package com.socialnetwork.adminbot.dto;

import com.socialnetwork.adminbot.entity.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDto {
    private Long telegramUserId;
    private String username;
    private String firstName;
    private AdminRole role;
    private Boolean isActive;
}
