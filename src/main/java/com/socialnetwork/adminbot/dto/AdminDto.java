package com.socialnetwork.adminbot.dto;

import com.socialnetwork.adminbot.entity.AdminRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDto {
    private UUID id;
    private Long telegramUserId;
    private String username;
    private String firstName;
    private AdminRole role;
    private Boolean isActive;
}
