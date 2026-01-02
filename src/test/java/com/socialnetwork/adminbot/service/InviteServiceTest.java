package com.socialnetwork.adminbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.socialnetwork.adminbot.domain.InviteToken;
import com.socialnetwork.adminbot.entity.AdminRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteService Unit Tests")
class InviteServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private InviteService inviteService;

    private InviteToken testInvite;
    private static final String TEST_TOKEN = "test-invite-token-12345";
    private static final String TEST_USERNAME = "@testuser";
    private static final Long CREATED_BY = 123456789L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        inviteService = new InviteService(redisTemplate, objectMapper);

        testInvite = InviteToken.builder()
                .token(TEST_TOKEN)
                .targetUsername(TEST_USERNAME)
                .role(AdminRole.ADMIN)
                .createdBy(CREATED_BY)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }

    @Nested
    @DisplayName("saveInvite Tests")
    class SaveInviteTests {

        @Test
        @DisplayName("should save invite token with TTL")
        void saveInvite_ShouldSaveTokenWithTtl() throws JsonProcessingException {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            inviteService.saveInvite(testInvite);

            // Then
            String expectedTokenKey = "invite:token:" + TEST_TOKEN;
            String expectedUsernameKey = "invite:username:" + TEST_USERNAME.toLowerCase();
            
            verify(valueOperations).set(eq(expectedTokenKey), anyString(), eq(Duration.ofHours(24)));
            verify(valueOperations).set(eq(expectedUsernameKey), eq(TEST_TOKEN), eq(Duration.ofHours(24)));
        }

        @Test
        @DisplayName("should serialize invite as JSON")
        void saveInvite_ShouldSerializeAsJson() throws JsonProcessingException {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

            // When
            inviteService.saveInvite(testInvite);

            // Then
            String expectedTokenKey = "invite:token:" + TEST_TOKEN;
            verify(valueOperations).set(eq(expectedTokenKey), jsonCaptor.capture(), any(Duration.class));
            
            String json = jsonCaptor.getValue();
            assertThat(json).contains(TEST_TOKEN);
            assertThat(json).contains(TEST_USERNAME);
            assertThat(json).contains("ADMIN");
        }
    }

    @Nested
    @DisplayName("getInviteByToken Tests")
    class GetInviteByTokenTests {

        @Test
        @DisplayName("should return invite when token exists and not expired")
        void getInviteByToken_WhenExists_ShouldReturnInvite() throws JsonProcessingException {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String json = objectMapper.writeValueAsString(testInvite);
            when(valueOperations.get("invite:token:" + TEST_TOKEN)).thenReturn(json);

            // When
            InviteToken result = inviteService.getInviteByToken(TEST_TOKEN);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getToken()).isEqualTo(TEST_TOKEN);
            assertThat(result.getTargetUsername()).isEqualTo(TEST_USERNAME);
            assertThat(result.getRole()).isEqualTo(AdminRole.ADMIN);
        }

        @Test
        @DisplayName("should return null when token not found")
        void getInviteByToken_WhenNotFound_ShouldReturnNull() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("invite:token:" + TEST_TOKEN)).thenReturn(null);

            // When
            InviteToken result = inviteService.getInviteByToken(TEST_TOKEN);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null and delete when token expired")
        void getInviteByToken_WhenExpired_ShouldReturnNullAndDelete() throws JsonProcessingException {
            // Given
            InviteToken expiredInvite = InviteToken.builder()
                    .token(TEST_TOKEN)
                    .targetUsername(TEST_USERNAME)
                    .role(AdminRole.ADMIN)
                    .createdBy(CREATED_BY)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .expiresAt(LocalDateTime.now().minusDays(1))  // Expired
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String json = objectMapper.writeValueAsString(expiredInvite);
            when(valueOperations.get("invite:token:" + TEST_TOKEN)).thenReturn(json);

            // When
            InviteToken result = inviteService.getInviteByToken(TEST_TOKEN);

            // Then
            assertThat(result).isNull();
            verify(redisTemplate).delete("invite:token:" + TEST_TOKEN);
            verify(redisTemplate).delete("invite:username:" + TEST_USERNAME.toLowerCase());
        }
    }

    @Nested
    @DisplayName("hasActivePendingInvite Tests")
    class HasActivePendingInviteTests {

        @Test
        @DisplayName("should return true when pending invite exists")
        void hasActivePendingInvite_WhenExists_ShouldReturnTrue() {
            // Given
            when(redisTemplate.hasKey("invite:username:" + TEST_USERNAME.toLowerCase())).thenReturn(true);

            // When
            boolean result = inviteService.hasActivePendingInvite(TEST_USERNAME);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no pending invite")
        void hasActivePendingInvite_WhenNotExists_ShouldReturnFalse() {
            // Given
            when(redisTemplate.hasKey("invite:username:" + TEST_USERNAME.toLowerCase())).thenReturn(false);

            // When
            boolean result = inviteService.hasActivePendingInvite(TEST_USERNAME);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteInvite Tests")
    class DeleteInviteTests {

        @Test
        @DisplayName("should delete both token and username keys")
        void deleteInvite_ShouldDeleteBothKeys() throws JsonProcessingException {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String json = objectMapper.writeValueAsString(testInvite);
            when(valueOperations.get("invite:token:" + TEST_TOKEN)).thenReturn(json);

            // When
            inviteService.deleteInvite(TEST_TOKEN);

            // Then
            verify(redisTemplate).delete("invite:token:" + TEST_TOKEN);
            verify(redisTemplate).delete("invite:username:" + TEST_USERNAME.toLowerCase());
        }
    }

    @Nested
    @DisplayName("tryLockInvite Tests")
    class TryLockInviteTests {

        @Test
        @DisplayName("should return true when lock acquired")
        void tryLockInvite_WhenLockAcquired_ShouldReturnTrue() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent("invite:lock:" + TEST_TOKEN, "locked", Duration.ofMinutes(5)))
                    .thenReturn(true);

            // When
            boolean result = inviteService.tryLockInvite(TEST_TOKEN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when lock already held")
        void tryLockInvite_WhenLockAlreadyHeld_ShouldReturnFalse() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent("invite:lock:" + TEST_TOKEN, "locked", Duration.ofMinutes(5)))
                    .thenReturn(false);

            // When
            boolean result = inviteService.tryLockInvite(TEST_TOKEN);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("unlockInvite Tests")
    class UnlockInviteTests {

        @Test
        @DisplayName("should delete lock key")
        void unlockInvite_ShouldDeleteLockKey() {
            // When
            inviteService.unlockInvite(TEST_TOKEN);

            // Then
            verify(redisTemplate).delete("invite:lock:" + TEST_TOKEN);
        }
    }
}
