package com.socialnetwork.adminbot.service;

import com.socialnetwork.adminbot.dto.AdminDto;
import com.socialnetwork.adminbot.entity.Admin;
import com.socialnetwork.adminbot.exception.DuplicateAdminException;
import com.socialnetwork.adminbot.exception.UnauthorizedException;
import com.socialnetwork.adminbot.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;

    public Admin findByTelegramId(Long telegramUserId) {
        return adminRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> {
                    log.warn("Admin not found: telegramUserId={}", telegramUserId);
                    return new UnauthorizedException("Admin not found");
                });
    }

    public boolean isAdmin(Long telegramUserId) {
        return adminRepository.existsByTelegramUserId(telegramUserId);
    }

    @Transactional
    public Admin createAdmin(AdminDto dto) {
        if (adminRepository.existsByTelegramUserId(dto.getTelegramUserId())) {
            throw new DuplicateAdminException("Admin already exists");
        }

        Admin admin = Admin.builder()
                .telegramUserId(dto.getTelegramUserId())
                .username(dto.getUsername())
                .firstName(dto.getFirstName())
                .role(dto.getRole())
                .isActive(true)
                .build();

        Admin saved = adminRepository.save(admin);
        log.info("Created new admin: id={} telegramUserId={}", 
                saved.getId(), saved.getTelegramUserId());
        return saved;
    }

    public List<Admin> getAllActiveAdmins() {
        return adminRepository.findByIsActiveTrue();
    }
}
